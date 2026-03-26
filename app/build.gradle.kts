import java.util.Properties

plugins {
    id("com.android.application")
    // org.jetbrains.kotlin.android is blocked by AGP 9.0+ (built-in Kotlin support)
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.musicali.app"
    compileSdk = 36

    val localProps = Properties()
    val localPropsFile = rootProject.file("local.properties")
    if (localPropsFile.exists()) localProps.load(localPropsFile.inputStream())

    defaultConfig {
        applicationId = "com.musicali.app"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "YOUTUBE_API_KEY",
            "\"${localProps.getProperty("YOUTUBE_API_KEY", "")}\"")
        buildConfigField("String", "GOOGLE_CLIENT_ID",
            "\"${localProps.getProperty("GOOGLE_CLIENT_ID", "")}\"")
        val reverseClientId = "com.googleusercontent.apps." +
            localProps.getProperty("GOOGLE_CLIENT_ID", "").removeSuffix(".apps.googleusercontent.com")
        manifestPlaceholders["appAuthRedirectScheme"] = reverseClientId
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    // AGP 9.x built-in Kotlin: explicitly register test/java as a Kotlin source directory
    // so that .kt files in src/test/java are compiled by compileDebugUnitTestKotlin
    sourceSets {
        getByName("test") {
            java.srcDirs("src/test/java")
        }
    }

    buildFeatures {
        buildConfig = true
    }
}

// Set Kotlin JVM target (kotlinOptions removed in AGP 9.0; use task config instead)
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// Robolectric 4.14.1 ASM workaround for JDK 25 (class file version 69 unsupported)
tasks.withType<Test>().configureEach {
    jvmArgs(
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.util=ALL-UNNAMED"
    )
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Compose BOM (D-07: no individual version pins)
    val composeBom = platform("androidx.compose:compose-bom:2026.03.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Activity + Lifecycle
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Room
    val roomVersion = "2.8.4"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.2.1")

    // HTTP client
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // HTML parser
    implementation("org.jsoup:jsoup:1.21.1")

    // Auth — AppAuth PKCE + EncryptedSharedPreferences
    implementation("net.openid:appauth:0.11.1")
    implementation("androidx.security:security-crypto:1.1.0")

    // Credential Manager (Google account picker)
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.0.0")

    // Retrofit 3 BOM + converter (YouTube API client)
    val retrofitBom = platform("com.squareup.retrofit2:retrofit-bom:3.0.0")
    implementation(retrofitBom)
    implementation("com.squareup.retrofit2:retrofit")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization")

    // kotlinx.serialization JSON (for Retrofit converter + YouTube response models)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.59.2")
    ksp("com.google.dagger:hilt-android-compiler:2.59.2")
    implementation("androidx.hilt:hilt-navigation-compose:1.3.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("androidx.room:room-testing:$roomVersion")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.ext:junit-ktx:1.2.1")
    testImplementation("org.robolectric:robolectric:4.14.1") {
        // Force ASM 9.8 to support Java 25 (class file version 69)
        // Robolectric 4.14.1 ships ASM 9.7 which only handles up to Java 23
        exclude(group = "org.ow2.asm")
    }
    testImplementation("org.ow2.asm:asm:9.8")
    testImplementation("org.ow2.asm:asm-commons:9.8")
    testImplementation("org.ow2.asm:asm-tree:9.8")
    testImplementation("org.ow2.asm:asm-util:9.8")
    testImplementation("org.ow2.asm:asm-analysis:9.8")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
