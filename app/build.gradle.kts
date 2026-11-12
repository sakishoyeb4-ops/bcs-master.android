
import java.net.URL

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.bcsmaster.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bcsmaster.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

val generatedTesseractAssets =
    layout.buildDirectory.dir("generated/tesseractAssets")

val downloadTesseractModels by tasks.registering {
    val modelDir = generatedTesseractAssets.get().dir("tessdata")

    outputs.files(
        modelDir.file("ben.traineddata"),
        modelDir.file("eng.traineddata")
    )

    doLast {
        val dir = modelDir.get().asFile
        dir.mkdirs()

        val models = mapOf(
            "ben.traineddata" to
                "https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/main/ben.traineddata",
            "eng.traineddata" to
                "https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/main/eng.traineddata"
        )

        models.forEach { (name, url) ->
            val target = dir.resolve(name)

            if (!target.exists() || target.length() == 0L) {
                logger.lifecycle("Downloading OCR model: $name")
                URL(url).openStream().use { input ->
                    target.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }
}

android.sourceSets.getByName("main")
    .assets.srcDir(generatedTesseractAssets)

tasks.named("preBuild").configure {
    dependsOn(downloadTesseractModels)
}

dependencies {
    val composeBom =
        platform("androidx.compose:compose-bom:2025.04.01")

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    implementation(
        "cz.adaptech.tesseract4android:tesseract4android:4.8.0"
    )

    debugImplementation("androidx.compose.ui:ui-tooling")
}
