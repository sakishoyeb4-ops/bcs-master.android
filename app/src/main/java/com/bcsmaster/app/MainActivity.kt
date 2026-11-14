
package com.bcsmaster.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File

private val Navy = Color(0xFF102A43)
private val Green = Color(0xFF16865C)
private val PageBackground = Color(0xFFF4F7FA)

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

private fun performOcr(
    context: android.content.Context,
    bitmap: Bitmap
): String {

    val dataDir = File(context.filesDir, "tesseract")
    val tessdataDir = File(dataDir, "tessdata")

    tessdataDir.mkdirs()

    val models = listOf(
        "ben.traineddata",
        "eng.traineddata"
    )

    for (model in models) {
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
        val initialized = api.init(
            dataDir.absolutePath,
            "ben+eng"
        )

        if (!initialized) {
            throw Exception("OCR চালু করা যায়নি")
        }

        api.setImage(bitmap)

        val result = api.utF8Text

        return result?.trim().orEmpty()

    } finally {
        api.end()
    }
}

@Composable
fun BCSMasterApp() {

    val context = LocalContext.current

    var selectedImage by remember {
        mutableStateOf<Bitmap?>(null)
    }

    var status by remember {
        mutableStateOf("এখনো কোনো ছবি নির্বাচন করা হয়নি")
    }

    var extractedText by remember {
        mutableStateOf("")
    }

    var isProcessing by remember {
        mutableStateOf(false)
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            selectedImage = bitmap
            extractedText = ""
            status = "ক্যামেরা থেকে ছবি নেওয়া হয়েছে"
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->

        if (uri != null) {
            try {
                val bitmap = context.contentResolver
                    .openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it)
                    }

                if (bitmap != null) {
                    selectedImage = bitmap
                    extractedText = ""
                    status = "Gallery থেকে ছবি নেওয়া হয়েছে"
                } else {
                    status = "ছবি পড়া যায়নি"
                }

            } catch (e: Exception) {
                status = "ছবি খুলতে সমস্যা হয়েছে"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
    ) {

        Text(
            "BCS MASTER",
            fontSize = 27.sp,
            fontWeight = FontWeight.Bold,
            color = Navy
        )

        Text(
            "Smart Job Solution Question Scanner",
            color = Color.Gray
        )

        Spacer(Modifier.height(22.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = Navy
            )
        ) {
            Column(Modifier.padding(20.dp)) {

                Text(
                    "ছবি থেকে পরীক্ষা তৈরি করো",
                    color = Color.White,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    "Job Solution বইয়ের ছবি তুলে প্রশ্ন ব্যাংক তৈরি করো।",
                    color = Color.White
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            "Question Scanner",
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            color = Navy
        )

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = {
                cameraLauncher.launch(null)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Green
            )
        ) {
            Text("ক্যামেরা দিয়ে ছবি তুলুন")
        }

        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            onClick = {
                galleryLauncher.launch(
                    PickVisualMediaRequest(
                        ActivityResultContracts
                            .PickVisualMedia.ImageOnly
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Gallery থেকে ছবি নিন")
        }

        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Text(
                    "ছবির Preview",
                    fontWeight = FontWeight.Bold,
                    color = Navy,
                    fontSize = 18.sp
                )

                Spacer(Modifier.height(12.dp))

                val image = selectedImage

                if (image != null) {
                    Image(
                        bitmap = image.asImageBitmap(),
                        contentDescription = "Question image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 350.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .background(PageBackground),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("এখানে বইয়ের ছবি দেখা যাবে")
                    }
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    status,
                    color = Green,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        Button(
            onClick = {

                val bitmap = selectedImage

                if (bitmap != null && !isProcessing) {

                    isProcessing = true
                    extractedText = ""
                    status = "ছবি থেকে লেখা শনাক্ত হচ্ছে..."

                    Thread {

                        val result = try {
                            performOcr(context, bitmap)
                        } catch (e: Exception) {
                            "OCR Error: ${e.message ?: "অজানা সমস্যা"}"
                        }

                        (context as ComponentActivity)
                            .runOnUiThread {

                                extractedText = result

                                status = when {
                                    result.startsWith("OCR Error:") ->
                                        "OCR চালাতে সমস্যা হয়েছে"

                                    result.isBlank() ->
                                        "কোনো লেখা শনাক্ত হয়নি"

                                    else ->
                                        "লেখা শনাক্ত সম্পন্ন হয়েছে"
                                }

                                isProcessing = false
                            }

                    }.start()
                }
            },
            enabled = selectedImage != null && !isProcessing,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Green
            ),
            shape = RoundedCornerShape(14.dp)
        ) {

            if (isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )

                Spacer(Modifier.width(10.dp))

                Text("লেখা শনাক্ত হচ্ছে...")
            } else {
                Text("ছবি থেকে লেখা শনাক্ত করুন")
            }
        }

        Spacer(Modifier.height(18.dp))

        if (extractedText.isNotEmpty()) {

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                )
            ) {

                Column(Modifier.padding(16.dp)) {

                    Text(
                        "OCR Result",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Navy
                    )

                    Spacer(Modifier.height(12.dp))

                    Text(
                        extractedText,
                        color = Color.DarkGray,
                        fontSize = 15.sp
                    )

                    Spacer(Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = {
                            val clipboard =
                                context.getSystemService(
                                    android.content.Context
                                        .CLIPBOARD_SERVICE
                                ) as android.content.ClipboardManager

                            clipboard.setPrimaryClip(
                                android.content.ClipData.newPlainText(
                                    "OCR Text",
                                    extractedText
                                )
                            )

                            status = "লেখা Copy করা হয়েছে"
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("লেখা Copy করুন")
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            "BCS MASTER • Offline OCR",
            modifier = Modifier.align(Alignment.CenterHorizontally),
            color = Color.Gray,
            fontSize = 12.sp
        )
    }
}
