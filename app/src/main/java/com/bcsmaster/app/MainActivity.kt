
package com.bcsmaster.app

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File

// ---------------- DATABASE ----------------

data class Question(
    val id: Long = 0,
    val subject: String,
    val question: String,
    val options: List<String>,
    val answer: Int,
    val explanation: String,
    val wrong: Int = 0
)

class QuestionDB(context: Context) :
    SQLiteOpenHelper(context, "bcs_master.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
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
        """)
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) {}

    fun add(q: Question): Long {
        val values = ContentValues().apply {
            put("subject", q.subject)
            put("question", q.question)
            put("a", q.options[0])
            put("b", q.options[1])
            put("c", q.options[2])
            put("d", q.options[3])
            put("answer", q.answer)
            put("explanation", q.explanation)
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
                        id = c.getLong(0),
                        subject = c.getString(1),
                        question = c.getString(2),
                        options = listOf(
                            c.getString(3),
                            c.getString(4),
                            c.getString(5),
                            c.getString(6)
                        ),
                        answer = c.getInt(7),
                        explanation = c.getString(8) ?: "",
                        wrong = c.getInt(9)
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
            "questions", "id = ?", arrayOf(id.toString())
        )
    }
}

// ---------------- OCR ----------------

private fun performOcr(context: Context, bitmap: Bitmap): String {
    val dataDir = File(context.filesDir, "tesseract")
    val tessdataDir = File(dataDir, "tessdata")
    tessdataDir.mkdirs()

    listOf("ben.traineddata", "eng.traineddata").forEach { model ->
        val target = File(tessdataDir, model)
        if (!target.exists() || target.length() == 0L) {
            context.assets.open("tessdata/$model").use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    val api = TessBaseAPI()
    try {
        if (!api.init(dataDir.absolutePath, "ben+eng")) {
            error("OCR চালু করা যায়নি")
        }
        api.setImage(bitmap)
        return api.utF8Text?.trim().orEmpty()
    } finally {
        api.recycle()
    }
}

// ---------------- MAIN ACTIVITY ----------------

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

// ---------------- APP HOME ----------------

@Composable
fun BCSMasterApp() {
    val context = LocalContext.current
    val db = remember { QuestionDB(context) }

    var page by remember { mutableStateOf("home") }
    var refresh by remember { mutableIntStateOf(0) }

    MaterialTheme {
        Column(
            Modifier.fillMaxSize().padding(16.dp)
        ) {
            Text(
                "BCS MASTER",
                style = MaterialTheme.typography.headlineMedium
            )
            Text("Offline BCS Preparation")

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Button(onClick = { page = "add" }) {
                    Text("প্রশ্ন যোগ")
                }
                Button(onClick = {
                    page = "bank"
                    refresh++
                }) {
                    Text("প্রশ্ন ব্যাংক")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Button(onClick = {
                    page = "quiz"
                    refresh++
                }) {
                    Text("পরীক্ষা")
                }
                Button(onClick = {
                    page = "wrong"
                    refresh++
                }) {
                    Text("ভুল প্রশ্ন")
                }
            }

            OutlinedButton(onClick = { page = "ocr" }) {
                Text("বইয়ের ছবি থেকে OCR")
            }

            HorizontalDivider()

            when (page) {
                "add" -> AddQuestionScreen(db) {
                    refresh++
                    page = "bank"
                }

                "bank" -> {
                    key(refresh) {
                        QuestionListScreen(db, false) {
                            refresh++
                        }
                    }
                }

                "wrong" -> {
                    key(refresh) {
                        QuestionListScreen(db, true) {
                            refresh++
                        }
                    }
                }

                "quiz" -> {
                    key(refresh) {
                        QuizScreen(db)
                    }
                }

                "ocr" -> OcrScreen()

                else -> {
                    Column(Modifier.padding(top = 25.dp)) {
                        Text("স্বাগতম!")
                        Text("সংরক্ষিত প্রশ্ন: ${db.getAll().size}")
                        Text("ভুল প্রশ্ন: ${db.getAll(true).size}")
                        Text("সম্পূর্ণ অফলাইন প্রশ্ন অনুশীলন")
                    }
                }
            }
        }
    }
}

// ---------------- ADD QUESTION ----------------

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
        Text("নতুন প্রশ্ন সংরক্ষণ", style = MaterialTheme.typography.titleLarge)

        OutlinedTextField(
            subject, { subject = it },
            label = { Text("বিষয়") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            question, { question = it },
            label = { Text("প্রশ্ন") },
            modifier = Modifier.fillMaxWidth()
        )

        options.forEachIndexed { i, value ->
            OutlinedTextField(
                value, { options[i] = it },
                label = { Text("অপশন ${'A' + i}") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Text("সঠিক উত্তর")
        Row {
            options.indices.forEach { i ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(answer == i, { answer = i })
                    Text("${'A' + i}")
                }
            }
        }

        OutlinedTextField(
            explanation, { explanation = it },
            label = { Text("ব্যাখ্যা") },
            modifier = Modifier.fillMaxWidth()
        )

        Text(message)

        Button(onClick = {
            if (question.isNotBlank() && options.all { it.isNotBlank() }) {
                val id = db.add(
                    Question(
                        subject = subject.ifBlank { "সাধারণ" },
                        question = question,
                        options = options.toList(),
                        answer = answer,
                        explanation = explanation
                    )
                )
                if (id > 0) onSaved()
                else message = "প্রশ্ন সংরক্ষণ হয়নি"
            } else {
                message = "প্রশ্ন ও চারটি অপশন পূরণ করুন"
            }
        }) {
            Text("সংরক্ষণ করুন")
        }
    }
}

// ---------------- QUESTION BANK ----------------

@Composable
fun QuestionListScreen(
    db: QuestionDB,
    wrongOnly: Boolean,
    onDelete: () -> Unit
) {
    val questions = remember { db.getAll(wrongOnly) }

    Column {
        Text(
            if (wrongOnly) "ভুল প্রশ্ন" else "প্রশ্ন ব্যাংক",
            style = MaterialTheme.typography.titleLarge
        )

        if (questions.isEmpty()) {
            Text("এখনো কোনো প্রশ্ন নেই")
        }

        LazyColumn {
            items(questions, key = { it.id }) { q ->
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 5.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(q.subject)
                        Text(q.question, style = MaterialTheme.typography.titleMedium)

                        q.options.forEachIndexed { i, option ->
                            Text("${'A' + i}. $option")
                        }

                        Text("সঠিক উত্তর: ${'A' + q.answer}")
                        Text("ব্যাখ্যা: ${q.explanation}")
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

// ---------------- RANDOM MCQ EXAM ----------------

@Composable
fun QuizScreen(db: QuestionDB) {
    val questions = remember {
        db.getAll().shuffled().take(10)
    }

    var index by remember { mutableIntStateOf(0) }
    var selected by remember { mutableIntStateOf(-1) }
    var score by remember { mutableIntStateOf(0) }
    var checked by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Random MCQ পরীক্ষা", style = MaterialTheme.typography.titleLarge)

        if (questions.isEmpty()) {
            Text("আগে প্রশ্ন ব্যাংকে প্রশ্ন যোগ করুন")
        } else if (finished) {
            Text("পরীক্ষা শেষ!", style = MaterialTheme.typography.headlineSmall)
            Text("মোট নম্বর: $score / ${questions.size}")
            Text("ভুল উত্তর: ${questions.size - score}")

            Button(onClick = {
                index = 0
                selected = -1
                score = 0
                checked = false
                finished = false
            }) {
                Text("আবার পরীক্ষা")
            }
        } else {
            val q = questions[index]

            Text("প্রশ্ন ${index + 1} / ${questions.size}")
            Text(q.subject)
            Text(q.question, style = MaterialTheme.typography.titleMedium)

            q.options.forEachIndexed { i, option ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected == i,
                        onClick = {
                            if (!checked) selected = i
                        },
                        enabled = !checked
                    )
                    Text(option)
                }
            }

            if (checked) {
                Text(
                    if (selected == q.answer) "সঠিক উত্তর!"
                    else "ভুল! সঠিক উত্তর: ${'A' + q.answer}"
                )
                Text("ব্যাখ্যা: ${q.explanation}")
            }

            Button(
                enabled = selected >= 0 && !checked,
                onClick = {
                    checked = true
                    if (selected == q.answer) {
                        score++
                    } else {
                        db.markWrong(q.id)
                    }
                }
            ) {
                Text("উত্তর যাচাই")
            }

            Button(
                enabled = checked,
                onClick = {
                    if (index == questions.lastIndex) {
                        finished = true
                    } else {
                        index++
                        selected = -1
                        checked = false
                    }
                }
            ) {
                Text(
                    if (index == questions.lastIndex) "ফলাফল দেখুন"
                    else "পরবর্তী প্রশ্ন"
                )
            }
        }
    }
}

// ---------------- OCR SCANNER ----------------

@Composable
fun OcrScreen() {
    val context = LocalContext.current

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var result by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("ছবি নির্বাচন করুন") }
    var loading by remember { mutableStateOf(false) }

    val gallery = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                bitmap = context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it)
                }
                result = ""
                status = "ছবি নির্বাচন করা হয়েছে"
            } catch (e: Exception) {
                status = "ছবি খোলা যায়নি"
            }
        }
    }

    val camera = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) {
        if (it != null) {
            bitmap = it
            result = ""
            status = "ক্যামেরার ছবি নেওয়া হয়েছে"
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
    ) {
        Text("Book OCR Scanner", style = MaterialTheme.typography.titleLarge)

        Button(onClick = { camera.launch(null) }) {
            Text("ক্যামেরা")
        }

        Button(onClick = {
            gallery.launch(
                PickVisualMediaRequest(
                    ActivityResultContracts.PickVisualMedia.ImageOnly
                )
            )
        }) {
            Text("গ্যালারি")
        }

        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Book image",
                modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                contentScale = ContentScale.Fit
            )
        }

        Text(status)

        Button(
            enabled = bitmap != null && !loading,
            onClick = {
                loading = true
                val image = bitmap!!

                Thread {
                    val text = try {
                        performOcr(context, image)
                    } catch (e: Exception) {
                        "OCR Error: ${e.message}"
                    }

                    (context as ComponentActivity).runOnUiThread {
                        result = text
                        status = if (text.isBlank()) {
                            "কোনো লেখা পাওয়া যায়নি"
                        } else {
                            "OCR সম্পন্ন"
                        }
                        loading = false
                    }
                }.start()
            }
        ) {
            Text(if (loading) "অপেক্ষা করুন..." else "লেখা শনাক্ত করুন")
        }

        if (result.isNotEmpty()) {
            Text("OCR Result", style = MaterialTheme.typography.titleLarge)
            Text(result)

            Button(onClick = {
                val clipboard = context.getSystemService(
                    Context.CLIPBOARD_SERVICE
                ) as android.content.ClipboardManager

                clipboard.setPrimaryClip(
                    android.content.ClipData.newPlainText("OCR", result)
                )
                status = "লেখা Copy হয়েছে"
            }) {
                Text("লেখা Copy করুন")
            }
        }
    }
}
