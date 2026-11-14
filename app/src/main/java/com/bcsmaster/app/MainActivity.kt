package com.bcsmaster.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File

// ------------------------------------------------------------
// MODEL
// answer: 0=ক, 1=খ, 2=গ, 3=ঘ
// ------------------------------------------------------------

data class Question(
    val id: Long = 0,
    val subject: String,
    val question: String,
    val options: List<String>,
    val answer: Int,
    val explanation: String,
    val wrong: Int = 0
)

data class OcrDraft(
    val question: String,
    val options: List<String>,
    val answer: Int = -1,
    val explanation: String = ""
)

// ------------------------------------------------------------
// DATABASE
// Existing database name, table and columns are preserved.
// This is intended to keep previously saved questions.
// ------------------------------------------------------------

class QuestionDB(context: Context) :
    SQLiteOpenHelper(context, "bcs_master.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE questions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                subject TEXT NOT NULL,
                question TEXT NOT NULL,
                a TEXT NOT NULL,
                b TEXT NOT NULL,
                c TEXT NOT NULL,
                d TEXT NOT NULL,
                answer INTEGER NOT NULL,
                explanation TEXT,
                wrong INTEGER DEFAULT 0
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) {
        // Do not drop the existing table or saved questions.
    }

    fun add(q: Question): Long {
        if (q.options.size != 4 || q.answer !in 0..3) return -1L

        val values = ContentValues().apply {
            put("subject", q.subject)
            put("question", q.question)
            put("a", q.options[0])
            put("b", q.options[1])
            put("c", q.options[2])
            put("d", q.options[3])
            put("answer", q.answer)
            put("explanation", q.explanation)
            put("wrong", q.wrong)
        }

        return writableDatabase.insert("questions", null, values)
    }

    fun getAll(wrongOnly: Boolean = false): List<Question> {
        val result = mutableListOf<Question>()
        val sql = if (wrongOnly) {
            "SELECT * FROM questions WHERE wrong > 0 ORDER BY id DESC"
        } else {
            "SELECT * FROM questions ORDER BY id DESC"
        }

        readableDatabase.rawQuery(sql, null).use { c ->
            while (c.moveToNext()) {
                result.add(
                    Question(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        subject = c.getString(c.getColumnIndexOrThrow("subject")),
                        question = c.getString(c.getColumnIndexOrThrow("question")),
                        options = listOf(
                            c.getString(c.getColumnIndexOrThrow("a")),
                            c.getString(c.getColumnIndexOrThrow("b")),
                            c.getString(c.getColumnIndexOrThrow("c")),
                            c.getString(c.getColumnIndexOrThrow("d"))
                        ),
                        answer = c.getInt(c.getColumnIndexOrThrow("answer")),
                        explanation = c.getString(
                            c.getColumnIndexOrThrow("explanation")
                        ) ?: "",
                        wrong = c.getInt(c.getColumnIndexOrThrow("wrong"))
                    )
                )
            }
        }
        return result
    }

    fun markWrong(id: Long) {
        writableDatabase.execSQL(
            "UPDATE questions SET wrong = wrong + 1 WHERE id = ?",
            arrayOf(id)
        )
    }

    fun delete(id: Long) {
        writableDatabase.delete(
            "questions",
            "id = ?",
            arrayOf(id.toString())
        )
    }
}

// ------------------------------------------------------------
// TESSERACT SETUP AND OCR
// ------------------------------------------------------------

private fun performOcr(context: Context, bitmap: Bitmap): String {
    val dataDir = File(context.filesDir, "tesseract")
    val tessdataDir = File(dataDir, "tessdata")
    tessdataDir.mkdirs()

    listOf("ben.traineddata", "eng.traineddata").forEach { model ->
        val target = File(tessdataDir, model)
        if (!target.exists() || target.length() == 0L) {
            context.assets.open("tessdata/$model").use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }

    val api = TessBaseAPI()
    try {
        check(api.init(dataDir.absolutePath, "ben+eng")) {
            "Tesseract চালু করা যায়নি"
        }
        api.setImage(bitmap)
        return api.utF8Text?.trim().orEmpty()
    } finally {
        api.recycle()
    }
}

/**
 * Scan left and right columns independently, then join in reading order.
 * This also helps questions that start near the bottom of the left column
 * and continue at the top of the right column.
 */
private fun performTwoColumnOcr(
    context: Context,
    source: Bitmap
): String {
    val width = source.width
    val height = source.height

    if (width < 4) return performOcr(context, source)

    val middle = width / 2
    val gutter = (width * 0.018f).toInt().coerceAtLeast(2)
    val leftWidth = (middle - gutter).coerceAtLeast(1)
    val rightX = (middle + gutter).coerceAtMost(width - 1)
    val rightWidth = (width - rightX).coerceAtLeast(1)

    val left = Bitmap.createBitmap(source, 0, 0, leftWidth, height)
    val right = Bitmap.createBitmap(source, rightX, 0, rightWidth, height)

    return try {
        val leftText = performOcr(context, left)
        val rightText = performOcr(context, right)
        leftText + "\n" + rightText
    } finally {
        left.recycle()
        right.recycle()
    }
}

// ------------------------------------------------------------
// BANGLA MCQ PARSER
// Supports numbered questions, ক./খ./গ./ঘ. options, and
// answer markers such as "উ. ক" / "উঃ খ" / "উত্তর: গ".
// OCR is imperfect, so all parsed questions must be reviewed.
// ------------------------------------------------------------

private fun normalizeDigits(input: String): String {
    val map = mapOf(
        '০' to '0', '১' to '1', '২' to '2', '৩' to '3', '৪' to '4',
        '৫' to '5', '৬' to '6', '৭' to '7', '৮' to '8', '৯' to '9'
    )
    return buildString {
        input.forEach { append(map[it] ?: it) }
    }
}

private fun answerIndex(label: String): Int {
    return when (label.trim().lowercase()) {
        "ক", "a" -> 0
        "খ", "b" -> 1
        "গ", "c" -> 2
        "ঘ", "d" -> 3
        else -> -1
    }
}

private fun parseQuestions(ocrText: String): List<OcrDraft> {
    val lines = ocrText.lines()
        .map { it.replace('\u00A0', ' ').trim() }
        .filter { it.isNotBlank() }

    val questionStart = Regex(
        "^(?:প্রশ্ন\\s*)?[০-৯0-9]{1,3}\\s*[.)।:]\\s*"
    )

    val starts = lines.indices.filter { questionStart.containsMatchIn(lines[it]) }
    if (starts.isEmpty()) return emptyList()

    val answerRegex = Regex(
        "(?:সঠিক\\s*উত্তর|উত্তর|উ\\s*[.ঃ:]|উঃ|Ans(?:wer)?)\\s*[:：.-]?\\s*([কখগঘA-Da-d])",
        RegexOption.IGNORE_CASE
    )
    val optionRegex = Regex(
        "(?:^|\\s)([কখগঘ])\\s*[.)।:]\\s*"
    )
    val pageNoise = Regex(
        "^(পৃষ্ঠা|পৃষ্ঠাঃ|পৃষ্ঠাঃ|অনূদিত|অভূতপূর্ব|সম্পাদিত|প্রকাশক)"
    )

    val output = mutableListOf<OcrDraft>()

    for (blockNumber in starts.indices) {
        val from = starts[blockNumber]
        val to = if (blockNumber + 1 < starts.size) starts[blockNumber + 1] else lines.size
        val block = lines.subList(from, to)

        val questionBuilder = StringBuilder()
        val optionBuilders = List(4) { StringBuilder() }
        var currentOption = -1
        var answer = -1
        val explanation = StringBuilder()

        block.forEachIndexed { lineIndex, raw ->
            var line = raw
            if (lineIndex == 0) {
                line = line.replaceFirst(questionStart, "").trim()
            }

            if (pageNoise.containsMatchIn(line)) return@forEachIndexed

            val answerMatch = answerRegex.find(line)
            if (answerMatch != null) {
                answer = answerIndex(answerMatch.groupValues[1])
                line = answerRegex.replace(line, " ").trim()
            }

            if (line.startsWith("ব্যাখ্যা") || line.startsWith("সমাধান")) {
                explanation.append(" ")
                    .append(line.substringAfter(':', line).trim())
                return@forEachIndexed
            }

            val matches = optionRegex.findAll(line).toList()
            if (matches.isEmpty()) {
                if (currentOption >= 0) {
                    optionBuilders[currentOption].append(" ").append(line)
                } else if (line.isNotBlank()) {
                    questionBuilder.append(" ").append(line)
                }
                return@forEachIndexed
            }

            val firstStart = matches.first().groups[1]?.range?.first
                ?: matches.first().range.first
            val prefix = line.substring(0, firstStart).trim()
            if (prefix.isNotBlank()) {
                if (currentOption >= 0) {
                    optionBuilders[currentOption].append(" ").append(prefix)
                } else {
                    questionBuilder.append(" ").append(prefix)
                }
            }

            matches.forEachIndexed { matchIndex, match ->
                val label = match.groupValues[1]
                val index = answerIndex(label)
                if (index in 0..3) {
                    currentOption = index
                    val contentStart = match.range.last + 1
                    val contentEnd = if (matchIndex + 1 < matches.size) {
                        matches[matchIndex + 1].range.first
                    } else {
                        line.length
                    }
                    val value = line.substring(contentStart, contentEnd).trim()
                    if (value.isNotBlank()) {
                        optionBuilders[index].append(" ").append(value)
                    }
                }
            }
        }

        val question = questionBuilder.toString()
            .replace(Regex("\\s+"), " ")
            .trim()

        val options = optionBuilders.map {
            it.toString().replace(Regex("\\s+"), " ").trim()
        }

        if (question.isNotBlank() && options.all { it.isNotBlank() }) {
            output.add(
                OcrDraft(
                    question = question,
                    options = options,
                    answer = answer,
                    explanation = explanation.toString()
                        .replace(Regex("\\s+"), " ")
                        .trim()
                )
            )
        }
    }

    return output
}

// ------------------------------------------------------------
// ACTIVITY
// ------------------------------------------------------------

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                BCSMasterApp()
            }
        }
    }
}

// ------------------------------------------------------------
// APP HOME / NAVIGATION
// ------------------------------------------------------------

@Composable
fun BCSMasterApp() {
    val context = LocalContext.current
    val db = remember { QuestionDB(context) }

    var page by remember { mutableStateOf("home") }
    var refresh by remember { mutableIntStateOf(0) }

    Column(
        Modifier.fillMaxSize().padding(16.dp)
    ) {
        Text("BCS MASTER", style = MaterialTheme.typography.headlineMedium)
        Text("Offline BCS Preparation")
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { page = "add" }) { Text("প্রশ্ন যোগ") }
            Button(onClick = { page = "bank"; refresh++ }) { Text("প্রশ্ন ব্যাংক") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { page = "quiz"; refresh++ }) { Text("পরীক্ষা") }
            Button(onClick = { page = "wrong"; refresh++ }) { Text("ভুল প্রশ্ন") }
        }
        OutlinedButton(onClick = { page = "ocr" }) {
            Text("বই স্ক্যান করে প্রশ্ন যোগ")
        }

        HorizontalDivider()

        when (page) {
            "add" -> AddQuestionScreen(db) {
                refresh++
                page = "bank"
            }
            "ocr" -> OcrScreen(db) {
                refresh++
                page = "bank"
            }
            "bank" -> key(refresh) {
                QuestionListScreen(db, false) { refresh++ }
            }
            "wrong" -> key(refresh) {
                QuestionListScreen(db, true) { refresh++ }
            }
            "quiz" -> key(refresh) {
                QuizScreen(db)
            }
            else -> {
                Column(Modifier.padding(top = 24.dp)) {
                    Text("স্বাগতম!")
                    Text("সংরক্ষিত প্রশ্ন: ${db.getAll().size}")
                    Text("ভুল প্রশ্ন: ${db.getAll(true).size}")
                    Text("সম্পূর্ণ অফলাইন অনুশীলন")
                }
            }
        }
    }
}

// ------------------------------------------------------------
// MANUAL QUESTION ENTRY
// ------------------------------------------------------------

@Composable
fun AddQuestionScreen(db: QuestionDB, onSaved: () -> Unit) {
    var subject by remember { mutableStateOf("সাধারণ জ্ঞান") }
    var question by remember { mutableStateOf("") }
    val options = remember { mutableStateListOf("", "", "", "") }
    var answer by remember { mutableIntStateOf(0) }
    var explanation by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
    ) {
        Text("নতুন প্রশ্ন", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            subject, { subject = it },
            label = { Text("বিষয়") }, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            question, { question = it },
            label = { Text("প্রশ্ন") }, modifier = Modifier.fillMaxWidth()
        )
        options.forEachIndexed { i, value ->
            OutlinedTextField(
                value, { options[i] = it },
                label = { Text("অপশন ${'ক' + i}") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        Text("সঠিক উত্তর")
        Row(verticalAlignment = Alignment.CenterVertically) {
            (0..3).forEach { i ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = answer == i, onClick = { answer = i })
                    Text("${'ক' + i}")
                }
            }
        }
        OutlinedTextField(
            explanation, { explanation = it },
            label = { Text("ব্যাখ্যা") }, modifier = Modifier.fillMaxWidth()
        )
        Text(message)
        Button(onClick = {
            if (question.isNotBlank() && options.all { it.isNotBlank() }) {
                val id = db.add(
                    Question(
                        subject = subject,
                        question = question,
                        options = options.toList(),
                        answer = answer,
                        explanation = explanation
                    )
                )
                if (id > 0) onSaved() else message = "সংরক্ষণ হয়নি"
            } else {
                message = "সব ঘর পূরণ করুন"
            }
        }) {
            Text("সংরক্ষণ")
        }
    }
}

// ------------------------------------------------------------
// OCR SCREEN: CAMERA / GALLERY / REVIEW / SAVE
// ------------------------------------------------------------

@Composable
fun OcrScreen(db: QuestionDB, onSaved: () -> Unit) {
    val context = LocalContext.current

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var status by remember { mutableStateOf("ছবি নির্বাচন করুন") }
    var loading by remember { mutableStateOf(false) }
    var subject by remember { mutableStateOf("সাধারণ জ্ঞান") }
    val drafts = remember { mutableStateListOf<OcrDraft>() }

    val gallery = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                bitmap = context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it)
                }
                drafts.clear()
                status = if (bitmap != null) "ছবি নির্বাচন হয়েছে" else "ছবি খোলা যায়নি"
            } catch (e: Exception) {
                status = "ছবি খোলা যায়নি: ${e.message}"
            }
        }
    }

    val camera = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { captured ->
        if (captured != null) {
            bitmap = captured
            drafts.clear()
            status = "ক্যামেরার ছবি নেওয়া হয়েছে"
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
    ) {
        Text("AUTO QUESTION SCANNER", style = MaterialTheme.typography.titleLarge)

        Button(onClick = { camera.launch(null) }) {
            Text("ক্যামেরা দিয়ে ছবি তুলুন")
        }
        Button(onClick = {
            gallery.launch(
                PickVisualMediaRequest(
                    ActivityResultContracts.PickVisualMedia.ImageOnly
                )
            )
        }) {
            Text("গ্যালারি থেকে ছবি নিন")
        }

        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "বইয়ের পৃষ্ঠা",
                modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                contentScale = ContentScale.Fit
            )
        }

        OutlinedTextField(
            subject, { subject = it },
            label = { Text("বিষয়") }, modifier = Modifier.fillMaxWidth()
        )

        Button(
            enabled = bitmap != null && !loading,
            onClick = {
                val image = bitmap ?: return@Button
                loading = true
                status = "দুই কলাম স্ক্যান হচ্ছে..."

                Thread {
                    val parsed = try {
                        val raw = performTwoColumnOcr(context, image)
                        parseQuestions(raw)
                    } catch (e: Exception) {
                        emptyList()
                    }

                    (context as ComponentActivity).runOnUiThread {
                        drafts.clear()
                        drafts.addAll(parsed)
                        status = if (parsed.isEmpty()) {
                            "প্রশ্ন শনাক্ত হয়নি। পরিষ্কার ছবি দিন বা অন্যভাবে তুলুন।"
                        } else {
                            "${parsed.size}টি সম্ভাব্য প্রশ্ন পাওয়া গেছে। সংরক্ষণের আগে যাচাই করুন।"
                        }
                        loading = false
                    }
                }.start()
            }
        ) {
            Text(if (loading) "স্ক্যান হচ্ছে..." else "দুই কলাম স্ক্যান ও প্রশ্ন আলাদা করুন")
        }

        Text(status)

        drafts.forEachIndexed { index, draft ->
            Card(
                Modifier.fillMaxWidth().padding(vertical = 6.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("প্রশ্ন ${index + 1}", style = MaterialTheme.typography.titleMedium)

                    OutlinedTextField(
                        value = draft.question,
                        onValueChange = { drafts[index] = draft.copy(question = it) },
                        label = { Text("প্রশ্ন") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    draft.options.forEachIndexed { optionIndex, option ->
                        OutlinedTextField(
                            value = option,
                            onValueChange = { newValue ->
                                val changed = draft.options.toMutableList()
                                changed[optionIndex] = newValue
                                drafts[index] = draft.copy(options = changed)
                            },
                            label = { Text("অপশন ${'ক' + optionIndex}") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Text("সঠিক উত্তর নির্বাচন করুন")
                    Column {
                        (0..3).forEach { optionIndex ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = draft.answer == optionIndex,
                                    onClick = {
                                        drafts[index] = draft.copy(answer = optionIndex)
                                    }
                                )
                                Text("${'ক' + optionIndex}")
                            }
                        }
                    }

                    OutlinedTextField(
                        value = draft.explanation,
                        onValueChange = {
                            drafts[index] = draft.copy(explanation = it)
                        },
                        label = { Text("ব্যাখ্যা (থাকলে)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (draft.answer !in 0..3) {
                        Text(
                            "উত্তর শনাক্ত হয়নি—নিজে সঠিক উত্তর নির্বাচন করুন।",
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    TextButton(onClick = { drafts.removeAt(index) }) {
                        Text("এই প্রশ্ন বাদ দিন")
                    }
                }
            }
        }

        Button(
            enabled = drafts.isNotEmpty() && drafts.all {
                it.question.isNotBlank() &&
                    it.options.size == 4 &&
                    it.options.all(String::isNotBlank) &&
                    it.answer in 0..3
            },
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                var savedCount = 0
                drafts.toList().forEach { draft ->
                    val id = db.add(
                        Question(
                            subject = subject,
                            question = draft.question,
                            options = draft.options,
                            answer = draft.answer,
                            explanation = draft.explanation
                        )
                    )
                    if (id > 0) savedCount++
                }
                drafts.clear()
                status = "$savedCount টি প্রশ্ন ডেটাবেজে সংরক্ষণ হয়েছে"
                onSaved()
            }
        ) {
            Text("যাচাই করা সব প্রশ্ন সংরক্ষণ করুন")
        }

        Spacer(Modifier.height(24.dp))
    }
}

// ------------------------------------------------------------
// QUESTION BANK / WRONG QUESTIONS
// ------------------------------------------------------------

@Composable
fun QuestionListScreen(
    db: QuestionDB,
    wrongOnly: Boolean,
    onDelete: () -> Unit
) {
    val questions = remember { db.getAll(wrongOnly) }

    Column(Modifier.fillMaxSize()) {
        Text(
            if (wrongOnly) "ভুল প্রশ্ন" else "প্রশ্ন ব্যাংক",
            style = MaterialTheme.typography.titleLarge
        )

        if (questions.isEmpty()) Text("কোনো প্রশ্ন নেই")

        LazyColumn {
            items(questions, key = { it.id }) { q ->
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 5.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(q.subject)
                        Text(q.question, style = MaterialTheme.typography.titleMedium)
                        q.options.forEachIndexed { i, option ->
                            Text("${'ক' + i}. $option")
                        }
                        Text("সঠিক উত্তর: ${'ক' + q.answer}")
                        if (q.explanation.isNotBlank()) {
                            Text("ব্যাখ্যা: ${q.explanation}")
                        }
                        Text("ভুল হয়েছে: ${q.wrong} বার")
                        TextButton(onClick = {
                            db.delete(q.id)
                            onDelete()
                        }) {
                            Text("মুছে ফেলুন")
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------
// CUSTOM EXAM: USER CHOOSES QUESTION COUNT AND TIME
// ------------------------------------------------------------

@Composable
fun QuizScreen(db: QuestionDB) {
    val allQuestions = remember { db.getAll() }

    var questionCountText by remember { mutableStateOf("20") }
    var minutesText by remember { mutableStateOf("20") }
    var setupError by remember { mutableStateOf("") }

    var questions by remember { mutableStateOf<List<Question>>(emptyList()) }
    var index by remember { mutableIntStateOf(0) }
    var selectedAnswers by remember { mutableStateOf<Map<Long, Int>>(emptyMap()) }
    var remainingSeconds by remember { mutableIntStateOf(0) }
    var examStarted by remember { mutableStateOf(false) }
    var examFinished by remember { mutableStateOf(false) }

    // Countdown runs only while the exam is active.
    LaunchedEffect(examStarted, examFinished, remainingSeconds) {
        if (examStarted && !examFinished && remainingSeconds > 0) {
            kotlinx.coroutines.delay(1000)
            remainingSeconds -= 1
        } else if (examStarted && remainingSeconds <= 0) {
            examFinished = true
        }
    }

    fun finishExam() {
        examFinished = true
        examStarted = false
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
    ) {
        Text("পরীক্ষার সেটিংস", style = MaterialTheme.typography.titleLarge)

        if (!examStarted && !examFinished) {
            Text("প্রশ্ন ব্যাংকে মোট ${allQuestions.size}টি প্রশ্ন আছে।")

            OutlinedTextField(
                value = questionCountText,
                onValueChange = {
                    questionCountText = it.filter(Char::isDigit)
                    setupError = ""
                },
                label = { Text("কয়টি প্রশ্ন পরীক্ষা দেবেন?") },
                supportingText = { Text("যেমন: ২০, ৫০, ১০০") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = minutesText,
                onValueChange = {
                    minutesText = it.filter(Char::isDigit)
                    setupError = ""
                },
                label = { Text("সময় কত মিনিট?") },
                supportingText = { Text("যেমন: ২০, ৩০, ৬০") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (setupError.isNotBlank()) {
                Text(
                    setupError,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val count = questionCountText.toIntOrNull()
                    val minutes = minutesText.toIntOrNull()

                    when {
                        count == null || count <= 0 ->
                            setupError = "প্রশ্নের সংখ্যা ১ বা তার বেশি দিন।"
                        minutes == null || minutes <= 0 ->
                            setupError = "সময় ১ মিনিট বা তার বেশি দিন।"
                        allQuestions.isEmpty() ->
                            setupError = "প্রথমে প্রশ্ন ব্যাংকে প্রশ্ন যোগ করুন।"
                        count > allQuestions.size ->
                            setupError = "প্রশ্ন ব্যাংকে মাত্র ${allQuestions.size}টি প্রশ্ন আছে। এর বেশি দেওয়া যাবে না।"
                        else -> {
                            questions = allQuestions.shuffled().take(count)
                            index = 0
                            selectedAnswers = emptyMap()
                            remainingSeconds = minutes * 60
                            examFinished = false
                            examStarted = true
                            setupError = ""
                        }
                    }
                }
            ) {
                Text("পরীক্ষা শুরু করুন")
            }

            Text(
                "পরীক্ষা শুরু করার আগে প্রশ্নের সংখ্যা ও সময় নিজে নির্ধারণ করতে পারবেন।",
                color = MaterialTheme.colorScheme.secondary
            )
        } else {
            val total = questions.size
            val answeredCount = selectedAnswers.size

            if (examFinished) {
                val score = questions.count { q ->
                    selectedAnswers[q.id] == q.answer
                }
                val unanswered = total - answeredCount

                Text("পরীক্ষা শেষ", style = MaterialTheme.typography.headlineSmall)
                Text("মোট প্রশ্ন: $total")
                Text("সঠিক উত্তর: $score")
                Text("ভুল উত্তর: ${answeredCount - score}")
                Text("উত্তর দেননি: $unanswered")
                Text("প্রাপ্ত নম্বর: $score / $total")

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        examStarted = false
                        examFinished = false
                        questions = emptyList()
                        selectedAnswers = emptyMap()
                        index = 0
                    }
                ) {
                    Text("নতুন পরীক্ষা সেট করুন")
                }
            } else if (questions.isNotEmpty()) {
                val q = questions[index]
                val selected = selectedAnswers[q.id] ?: -1
                val minutesLeft = remainingSeconds / 60
                val secondsLeft = remainingSeconds % 60
                val timeText = "%02d:%02d".format(minutesLeft, secondsLeft)

                Text(
                    "সময় বাকি: $timeText",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (remainingSeconds <= 60)
                        MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
                Text("প্রশ্ন ${index + 1} / $total")
                Text("উত্তর দিয়েছেন: $answeredCount / $total")

                LinearProgressIndicator(
                    progress = { (index + 1).toFloat() / total.toFloat() },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))
                Text(q.subject, color = MaterialTheme.colorScheme.secondary)
                Text(q.question, style = MaterialTheme.typography.titleMedium)

                q.options.forEachIndexed { optionIndex, option ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = selected == optionIndex,
                            onClick = {
                                selectedAnswers =
                                    selectedAnswers + (q.id to optionIndex)
                            }
                        )
                        Text(option)
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(
                        enabled = index > 0,
                        onClick = { index-- }
                    ) {
                        Text("আগের প্রশ্ন")
                    }

                    Button(
                        enabled = index < total - 1,
                        onClick = { index++ }
                    ) {
                        Text("পরের প্রশ্ন")
                    }
                }

                Spacer(Modifier.height(12.dp))
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { finishExam() }
                ) {
                    Text("পরীক্ষা শেষ করুন")
                }
            }
        }
    }
}
