import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun prop(name: String): String = localProps.getProperty(name, "")

android {
    namespace = "com.capstone.snapspot"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.capstone.snapspot"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "GEMINI_API_KEY", "\"${prop("GEMINI_API_KEY")}\"")
        buildConfigField("String", "GEMINI_MODEL", "\"${prop("GEMINI_MODEL").ifEmpty { "gemini-3.5-flash-lite" }}\"")
        buildConfigField("String", "KAKAO_REST_KEY", "\"${prop("KAKAO_REST_KEY")}\"")
        buildConfigField("String", "KAKAO_NATIVE_KEY", "\"${prop("KAKAO_NATIVE_KEY")}\"")
        manifestPlaceholders["kakaoNativeKey"] = prop("KAKAO_NATIVE_KEY")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // The Kakao map SDK ships native code for ARM only. Packaging x86 libraries from other dependencies
        // would make an x86 emulator start the app as x86 and fail to load the map; with ARM only, phones run
        // natively and emulators with ARM translation run it translated.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    testOptions {
        unitTests.all { test ->
            // Live API smoke tests are off unless run with -PliveApi=true. Keys come from local.properties
            // and are handed to the test JVM only; they are never printed.
            val live = project.findProperty("liveApi") == "true"
            test.systemProperty("live.api", live.toString())
            if (live) {
                test.systemProperty("live.gemini.key", prop("GEMINI_API_KEY"))
                test.systemProperty("live.gemini.model", prop("GEMINI_MODEL").ifEmpty { "gemini-3.5-flash-lite" })
                test.systemProperty("live.kakao.rest.key", prop("KAKAO_REST_KEY"))
            }
        }
    }

    // A release key is read from local.properties (RELEASE_STORE_FILE, RELEASE_STORE_PASSWORD, RELEASE_KEY_ALIAS,
    // RELEASE_KEY_PASSWORD). Without it the release build is signed with the debug key, so it still installs on a
    // test phone. The key file and passwords never go into the repository.
    val releaseKeyFile = prop("RELEASE_STORE_FILE")
    signingConfigs {
        if (releaseKeyFile.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(releaseKeyFile)
                storePassword = prop("RELEASE_STORE_PASSWORD")
                keyAlias = prop("RELEASE_KEY_ALIAS")
                keyPassword = prop("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    // Room writes its schema here so schema changes show up in review.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(libs.play.services.location)
    implementation(libs.androidx.work.runtime)
    implementation(libs.coil.compose)
    implementation(libs.kakao.maps)
    implementation(libs.androidx.exifinterface)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    // Compose tests pull in an old Espresso that breaks on the newest Android; pin a fixed one.
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.navigation.testing)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
