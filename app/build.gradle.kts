import java.util.Base64
import java.util.Properties
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// ---- Backend configuration (never committed: local.properties or CI environment variables) ----
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun backendValue(name: String): String =
    (localProps.getProperty(name) ?: System.getenv(name) ?: "").trim()

val supabaseUrl = backendValue("SUPABASE_URL")
val supabaseAnonKey = backendValue("SUPABASE_ANON_KEY")

// Hard stop: a secret / service_role key must never end up inside the APK.
fun isSecretKey(key: String): Boolean {
    if (key.startsWith("sb_secret_")) return true
    val parts = key.split('.')
    if (parts.size != 3) return false
    val payload = runCatching { String(Base64.getUrlDecoder().decode(parts[1])) }.getOrDefault("")
    return Regex("\"role\"\\s*:\\s*\"service_role\"").containsMatchIn(payload)
}
check(!isSecretKey(supabaseAnonKey)) {
    "SUPABASE_ANON_KEY is a SECRET (service_role) key. Use the anon/publishable key — the secret key must stay on the server."
}

android {
    namespace = "az.market.app"
    compileSdk = 36

    defaultConfig {
        // NOTE: final applicationId is confirmed with the store owner before release (Stage 15).
        applicationId = "az.market.app"
        minSdk = 29 // Android 10+
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
    }

    androidResources {
        // Azerbaijani is the default UI language; en/ru reserved for the language picker (Stage 13).
        localeFilters += listOf("az", "en", "ru")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.auth)
    implementation(libs.supabase.storage)
    implementation(libs.supabase.realtime)
    implementation(libs.supabase.functions)
    implementation(libs.zxing.core)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.ktor.client.okhttp)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}

// ---- Release safety net: fail the build if a server secret ever ends up inside an APK ----
abstract class VerifyNoSecretsInApk : DefaultTask() {
    @get:InputFiles
    abstract val apkDirectory: ConfigurableFileCollection

    @TaskAction
    fun scan() {
        val jwt = Regex("eyJ[A-Za-z0-9_-]{8,}\\.(eyJ[A-Za-z0-9_-]{8,})\\.[A-Za-z0-9_-]{8,}")
        val patterns = mapOf(
            "Supabase secret key" to Regex("sb_secret_[A-Za-z0-9_-]{10,}"),
            "private key" to Regex("-----BEGIN (RSA |EC )?PRIVATE KEY-----"),
            "service account JSON" to Regex("\"type\"\\s*:\\s*\"service_account\""),
        )
        val findings = mutableListOf<String>()
        val apks = apkDirectory.asFileTree.matching { include("**/*.apk") }.files
        apks.forEach { apk ->
            ZipFile(apk).use { zip ->
                zip.entries().asSequence().filter { !it.isDirectory }.forEach { entry ->
                    // ISO-8859-1 maps every byte 1:1, so binary dex content can be regex-scanned safely.
                    val text = zip.getInputStream(entry).readBytes().toString(Charsets.ISO_8859_1)
                    patterns.forEach { (label, rx) -> if (rx.containsMatchIn(text)) findings += "${apk.name}!${entry.name}: $label" }
                    jwt.findAll(text).forEach { m ->
                        val payload = runCatching {
                            String(Base64.getUrlDecoder().decode(m.groupValues[1]))
                        }.getOrDefault("")
                        if (Regex("\"role\"\\s*:\\s*\"service_role\"").containsMatchIn(payload)) {
                            findings += "${apk.name}!${entry.name}: Supabase service_role JWT"
                        }
                    }
                }
            }
        }
        if (findings.isNotEmpty()) {
            throw GradleException("Server secrets found in APK:\n" + findings.joinToString("\n"))
        }
        logger.lifecycle("verifyNoSecretsInApk: ${apks.size} APK(s) scanned, no server secrets found")
    }
}

listOf("debug", "release").forEach { variant ->
    val cap = variant.replaceFirstChar { it.uppercase() }
    val verify = tasks.register<VerifyNoSecretsInApk>("verifyNoSecretsIn${cap}Apk") {
        apkDirectory.from(layout.buildDirectory.dir("outputs/apk/$variant"))
    }
    tasks.matching { it.name == "assemble$cap" }.configureEach { finalizedBy(verify) }
}
