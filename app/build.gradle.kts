plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.inputmethod.switcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.inputmethod.switcher"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    // 修改 APK 输出文件名
    applicationVariants.all {
        val variant = this
        variant.outputs
            .map { it as com.android.build.gradle.internal.api.BaseVariantOutputImpl }
            .forEach { output ->
                val outputFileName = "InputMethodSwitcher-${variant.versionName}.apk"
                output.outputFileName = outputFileName
            }
    }
}

dependencies {
    // 零外部依赖。仅使用框架 android.app.Activity / android.content.Intent，
    // 配合 R8 混淆压缩后 release APK 约 20–40 KB。
}
