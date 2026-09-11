plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
}

android {
  namespace = "dev.ykro.trailaid"
  compileSdk { version = release(37) }

  defaultConfig {
    applicationId = "dev.ykro.trailaid"
    minSdk = 26
    targetSdk = 37
    versionCode = 1
    versionName = "1.0.0"
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }

  packaging {
    resources {
      merges += "**/META-INF/INDEX.LIST"
      merges += "**/META-INF/DEPENDENCIES"
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
}

kotlin { jvmToolchain(17) }

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
  implementation(platform(libs.androidx.compose.bom))

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.navigation.compose)

  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.animation)
  debugImplementation(libs.androidx.compose.ui.tooling)

  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  ksp(libs.androidx.room.compiler)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.play.services.location)
  implementation(libs.androidx.camera.core)
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)

  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.timber)
  implementation(libs.coil.compose)

  implementation(libs.adk.core)
  implementation(libs.adk.litertlm)
  implementation(libs.litertlm.android)
  ksp(libs.adk.processor)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
}
