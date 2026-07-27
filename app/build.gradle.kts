import java.util.Base64
import java.util.Properties
import java.security.KeyStore
import java.io.FileInputStream
import java.net.URI
import java.io.File
import java.nio.file.StandardCopyOption
import java.nio.file.Files

fun downloadSecretFile(fileName: String, targetFile: File) {
    val baseUrls = listOf(
        "https://raw.githubusercontent.com/cabharathikrishna-a11y/Secrets/main/$fileName",
        "https://raw.githubusercontent.com/cabharathikrishna-a11y/Secrets/master/$fileName"
    )
    for (urlStr in baseUrls) {
        try {
            val url = URI(urlStr).toURL()
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            val responseCode = connection.responseCode
            if (responseCode == 200) {
                connection.inputStream.use { inputStream ->
                    Files.copy(inputStream, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                println("SUCCESS: Pulled secret file '$fileName' from $urlStr")
                return
            } else {
                println("INFO: Response code $responseCode for $urlStr")
            }
        } catch (e: Exception) {
            println("WARNING: Could not fetch secret file '$fileName' from $urlStr: ${e.message}")
        }
    }
}

// Download the secret files at configuration time from the user's public Secrets repo
val secretsRepoEnabled = true
if (secretsRepoEnabled) {
    println("DEBUG: Starting download of secrets from external repository...")
    try {
        val rootEnv = rootProject.file(".env")
        downloadSecretFile(".env", rootEnv)

        val googleServicesJson = project.file("google-services.json")
        downloadSecretFile("google-services.json", googleServicesJson)

        val debugKeystoreBase64 = rootProject.file("debug.keystore.base64")
        downloadSecretFile("debug.keystore.base64", debugKeystoreBase64)
    } catch (e: Exception) {
        println("WARNING: Error during secret files download process: ${e.message}")
    }
}

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
  alias(libs.plugins.firebase.crashlytics)
  id("org.jetbrains.kotlin.plugin.serialization")
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.lifeos.com"
    minSdk = 24
    targetSdk = 36
    val customCode = project.findProperty("customVersionCode")?.toString()?.toIntOrNull()
    val customName = project.findProperty("customVersionName")?.toString()

    versionCode = customCode ?: 20
    versionName = customName ?: "20.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    
    val envProps = Properties()
    val envFile = rootProject.file(".env")
    if (envFile.exists()) {
        val fis = FileInputStream(envFile)
        try {
            envProps.load(fis)
        } finally {
            fis.close()
        }
    }
    val mapsApiKey = envProps.getProperty("MAPS_API_KEY") ?: "YOUR_MAPS_API_KEY"
    manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
  }

  signingConfigs {
    create("release") {
      val kFile = rootProject.file("debug.keystore")
      println("DEBUG: kFile path = ${kFile.absolutePath}, exists = ${kFile.exists()}")
      val base64File = rootProject.file("debug.keystore.base64")
      println("DEBUG: base64File path = ${base64File.absolutePath}, exists = ${base64File.exists()}")
      var isKeystoreValid = false
      if (kFile.exists() && kFile.length() > 0) {
        try {
          val ks = KeyStore.getInstance("PKCS12")
          FileInputStream(kFile).use { fis ->
            ks.load(fis, "android".toCharArray())
          }
          isKeystoreValid = true
          println("DEBUG: Existing debug.keystore loaded successfully and is valid!")
        } catch (e: Exception) {
          println("DEBUG: Existing debug.keystore is invalid or corrupted: ${e.message}")
        }
      }

      if (!isKeystoreValid) {
        if (base64File.exists() && base64File.length() > 0) {
          try {
            val base64Text = base64File.readText().replace(Regex("\\s"), "")
            if (base64Text.isNotEmpty()) {
              println("DEBUG: Attempting to decode debug.keystore.base64 to recreate debug.keystore...")
              val decoded = Base64.getDecoder().decode(base64Text)
              kFile.writeBytes(decoded)
              println("DEBUG: Decoded and wrote debug.keystore successfully! File size: ${kFile.length()}")
              try {
                val ks = KeyStore.getInstance("PKCS12")
                FileInputStream(kFile).use { fis ->
                  ks.load(fis, "android".toCharArray())
                }
                isKeystoreValid = true
                println("DEBUG: Decoded debug.keystore verified successfully!")
              } catch (ev: Exception) {
                println("DEBUG: Decoded keystore is still invalid! Error: ${ev.message}")
              }
            }
          } catch (e: Exception) {
            println("DEBUG: Error decoding base64: ${e.message}")
            e.printStackTrace()
          }
        }
      }

      if (!isKeystoreValid) {
        println("DEBUG: WARNING - debug.keystore is still invalid! The build may fail if debug.keystore is not created externally.")
      }

      storeFile = kFile
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      signingConfig = signingConfigs.getByName("release")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

ksp {
  arg("room.schemaLocation", "${projectDir}/schemas")
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.core)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)
  implementation(libs.androidx.camera.video)
  implementation(libs.androidx.camera.extensions)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.startup)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.androidx.work.multiprocess)
  implementation(libs.androidx.concurrent.futures.ktx)
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.session)
  implementation(libs.androidx.media3.ui)
  implementation(libs.androidx.media3.transformer)
  implementation(libs.androidx.media3.common)
  implementation(libs.androidx.media3.cast)
  implementation(libs.apache.commons.compress)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  implementation(platform(libs.firebase.bom))
  implementation(libs.firebase.database)
  implementation(libs.firebase.firestore)
  implementation(libs.firebase.analytics)
  implementation(libs.firebase.crashlytics)
  implementation(libs.firebase.messaging)
  implementation(libs.firebase.inappmessaging.display)
  // implementation(libs.firebase.perf)
  implementation(libs.firebase.auth)
  implementation(libs.firebase.appdistribution)
  implementation(libs.play.services.auth)
  implementation(libs.play.services.media.effect.enhancement)
  implementation(libs.kotlinx.coroutines.play.services)
  implementation(libs.mediapipe.tasks.genai)
  implementation(libs.play.services.maps)
  implementation(libs.maps.compose)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation("com.google.code.gson:gson:2.11.0")
  implementation(platform("io.github.jan-tennert.supabase:bom:3.0.3"))
  implementation("io.github.jan-tennert.supabase:postgrest-kt")
  implementation("io.github.jan-tennert.supabase:realtime-kt")
  implementation("io.ktor:ktor-client-android:3.0.1")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}




