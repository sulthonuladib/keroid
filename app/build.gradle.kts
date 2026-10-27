import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.hilt)
  alias(libs.plugins.ksp)
  jacoco
}

val appVersion = Properties().apply {
  rootProject.file("version.properties").inputStream().use { load(it) }
}

android {
  namespace = "com.sulthonuladib.keroid"
  compileSdk = 37
  buildToolsVersion = "37.0.0"

  // Local releases use the existing sideload identity outside the repository.
  // CI requests an unsigned APK and signs it in a separate, protected job.
  val unsignedRelease = providers.gradleProperty("keroid.unsignedRelease")
    .map { it.toBooleanStrict() }.getOrElse(false)
  val local = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
  }
  val signingDir = File(System.getProperty("user.home"), ".local/share/keroid/signing")
  val releaseKeystore = local.getProperty("keroid.storeFile")?.let { rootProject.file(it) }
    ?: File(signingDir, "release.p12").takeIf { it.exists() }
  val releasePassword = local.getProperty("keroid.storePassword")
    ?: File(signingDir, "password").takeIf { it.exists() }?.readText()?.trim()
  signingConfigs {
    if (!unsignedRelease && releaseKeystore != null && !releasePassword.isNullOrBlank()) {
      create("release") {
        storeFile = releaseKeystore
        storePassword = releasePassword
        keyAlias = local.getProperty("keroid.keyAlias") ?: "keroid-release"
        keyPassword = local.getProperty("keroid.keyPassword") ?: releasePassword
      }
    }
  }

  defaultConfig {
    applicationId = "com.sulthonuladib.keroid"
    minSdk = 24
    targetSdk = 36
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    versionCode = appVersion.getProperty("versionCode").toInt().also {
      require(it in 1..2_100_000_000) { "versionCode must be a positive Android version code" }
    }
    versionName = appVersion.getProperty("versionName").also {
      require(it.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+"))) { "versionName must be X.Y.Z" }
    }
    // The Go core and terminal JNI are built only for these two ABIs. Do not
    // advertise 32-bit support through transitive dependencies' native code.
    ndk {
      abiFilters += listOf("arm64-v8a", "x86_64")
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      signingConfig = signingConfigs.findByName("release")
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    compose = true
    aidl = false
    buildConfig = false
    shaders = false
  }

  packaging {
    resources {
      // Keep Apache-licensed dependencies' licence markers in the APK; only the
      // LGPL marker is excluded (nothing here is LGPL).
      excludes += "/META-INF/LGPL2.1"
    }
  }
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  implementation(files("libs/keroid-core.aar"))
  implementation(libs.hilt.android)
  ksp(libs.hilt.compiler)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.androidx.material.icons)
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)
}
