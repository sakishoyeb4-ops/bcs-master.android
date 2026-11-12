package com.bcsmaster.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
val navy = Color(0xFF102A43)
val green = Color(0xFF16865C)

Scaffold(
    containerColor = Color(0xFFF4F7FA)
) { padding ->

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
    ) {

        Text(
            text = "BCS MASTER",
            fontSize = 27.sp,
            fontWeight = FontWeight.Bold,
            color = navy
        )

        Text(
            text = "Your Complete BCS Preparation Partner",
            fontSize = 13.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = navy
            )
        ) {
            Column(
                modifier = Modifier.padding(22.dp)
            ) {
                Text(
                    text = "Welcome to BCS Master!",
                    color = Color.White,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Prepare today. Succeed tomorrow.",
                    color = Color.White.copy(alpha = 0.85f)
                )

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "Your journey to becoming a BCS Cadre starts here.",
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        Text(
            text = "Your Dashboard",
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            color = navy
        )

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DashboardStat(
                title = "Subjects",
                value = "10",
                modifier = Modifier.weight(1f)
            )
            DashboardStat(
                title = "Questions",
                value = "0",
                modifier = Modifier.weight(1f)
            )
            DashboardStat(
                title = "Tests",
                value = "0",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(26.dp))

        Text(
            text = "Start Learning",
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            color = navy
        )

        Spacer(modifier = Modifier.height(12.dp))

        LearningCard(
            emoji = "📚",
            title = "Subject-wise MCQ",
            subtitle = "Practice questions by subject"
        )

        LearningCard(
            emoji = "📝",
            title = "Model Test",
            subtitle = "Take a full-length BCS mock exam"
        )

        LearningCard(
            emoji = "📖",
            title = "Previous BCS Questions",
            subtitle = "Practice previous examination questions"
        )

        LearningCard(
            emoji = "🔤",
            title = "English Vocabulary",
            subtitle = "Build your vocabulary every day"
        )

        LearningCard(
            emoji = "📊",
            title = "My Progress",
            subtitle = "Track your preparation and results"
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFE0F2E9)
            )
        ) {
            Column(
                modifier = Modifier.padding(18.dp)
            ) {
                Text(
                    text = "💡 Daily Motivation",
                    fontWeight = FontWeight.Bold,
                    color = green,
                    fontSize = 17.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Consistency is the key to success. Study a little every day and keep moving forward!",
                    color = navy
                )
            }
        }

        Spacer(modifier = Modifier.height(30.dp))

        Text(
            text = "BCS MASTER • Version 1.0",
            modifier = Modifier.align(Alignment.CenterHorizontally),
            color = Color.Gray,
            fontSize = 12.sp
        )
    }
}

}

@Composable
fun DashboardStat(
title: String,
value: String,
modifier: Modifier = Modifier
) {
Card(
modifier = modifier,
shape = RoundedCornerShape(14.dp),
colors = CardDefaults.cardColors(
containerColor = Color.White
)
) {
Column(
modifier = Modifier.padding(12.dp),
horizontalAlignment = Alignment.CenterHorizontally
) {
Text(
text = value,
fontSize = 25.sp,
fontWeight = FontWeight.Bold,
color = Color(0xFF16865C)
)

        Text(
            text = title,
            fontSize = 11.sp,
            color = Color.DarkGray
        )
    }
}

}

@Composable
fun LearningCard(
emoji: String,
title: String,
subtitle: String
) {
Card(
modifier = Modifier
.fillMaxWidth()
.padding(vertical = 6.dp),
shape = RoundedCornerShape(15.dp),
colors = CardDefaults.cardColors(
containerColor = Color.White
)
) {
Row(
modifier = Modifier.padding(16.dp),
verticalAlignment = Alignment.CenterVertically
) {
Text(
text = emoji,
fontSize = 28.sp
)

        Spacer(modifier = Modifier.width(15.dp))

        Column {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color(0xFF102A43)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
    }
}

}