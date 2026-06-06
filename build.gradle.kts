buildscript {
  repositories {
    mavenCentral()
    google()
  }
  configurations.all {
    resolutionStrategy {
      force("org.jetbrains.kotlinx:kotlinx-metadata-jvm:0.10.0")
      force("org.jetbrains.kotlin:kotlin-metadata-jvm:2.0.0")
    }
  }
}

allprojects {
  configurations.all {
    resolutionStrategy {
      force("org.jetbrains.kotlinx:kotlinx-metadata-jvm:0.10.0")
      force("org.jetbrains.kotlin:kotlin-metadata-jvm:2.0.0")
    }
  }
}

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.kotlin.android) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.roborazzi) apply false
  alias(libs.plugins.secrets) apply false
  alias(libs.plugins.dagger.hilt) apply false
}
