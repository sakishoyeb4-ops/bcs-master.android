
package com.bcsmaster.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
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

private val Navy = Color(0xFF102A43)
private val Green = Color(0xFF16865C)
private val Background = Color(0xFFF4F7FA)

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

@Composable
fun BCSMasterApp() {
    val context = LocalContext.current
    var selectedImage by remember { mutableStateOf<Bitmap?>(null) }
    var status by remember { mutableStateOf("এখনো কোনো ছবি নির্বাচন করা হয়নি") }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            selectedImage = bitmap
            status = "ক্যামেরা থেকে ছবি নেওয়া হয়েছে"
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use {
                    selectedImage = BitmapFactory.decodeStream(it)
                }
                status = if (selectedImage != null) {
                    "Gallery থেকে ছবি নেওয়া হয়েছে"
                } else {
                    "ছবি পড়া যায়নি"
                }
            } catch (e: Exception) {
                status = "ছবি খুলতে সমস্যা হয়েছে"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
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
            fontSize = 14.sp,
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
                    "Job Solution বইয়ের MCQ ছবি তুলে নিজের প্রশ্ন ব্যাংক তৈরি করো।",
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
            onClick = { cameraLauncher.launch(null) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Green
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("📷  ক্যামেরা দিয়ে ছবি তুলুন")
        }

        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            onClick = {
                galleryLauncher.launch(
                    ActivityResultContracts.PickVisualMedia.ImageOnly
                )
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("🖼  Gallery থেকে ছবি নিন")
        }

        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
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
                    fontSize = 17.sp
                )

                Spacer(Modifier.height(12.dp))

                if (selectedImage != null) {
                    Image(
                        bitmap = selectedImage!!.asImageBitmap(),
                        contentDescription = "Selected question page",
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
                            .background(
                                Background,
                                RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "এখানে বইয়ের ছবি দেখা যাবে",
                            color = Color.Gray
                        )
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

        Spacer(Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFE0F2E9)
            )
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "পরবর্তী ধাপ: OCR",
                    fontWeight = FontWeight.Bold,
                    color = Green,
                    fontSize = 17.sp
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    "ছবি থেকে বাংলা ও ইংরেজি লেখা শনাক্ত করে প্রশ্ন ও অপশন তৈরি করা হবে।",
                    color = Navy
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            "BCS MASTER • Offline Learning",
            modifier = Modifier.align(Alignment.CenterHorizontally),
            color = Color.Gray,
            fontSize = 12.sp
        )
    }
}

