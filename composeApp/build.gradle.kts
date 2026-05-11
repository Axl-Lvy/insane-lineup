import java.util.Properties
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    androidTarget { compilerOptions { jvmTarget.set(JvmTarget.JVM_11) } }

    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("insane-lineup")
        browser { commonWebpackConfig { outputFileName = "insane-lineup.js" } }
        binaries.executable()
    }

    // JVM target exists purely to host live integration tests (`./gradlew :composeApp:jvmTest`).
    // No JVM app artifact ships — the test runner just needs a real JVM dispatcher for
    // supabase-kt's lifecycle hooks and a working Looper-free environment.
    jvm { compilerOptions { jvmTarget.set(JvmTarget.JVM_11) } }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.material.icons.extended)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)

            implementation(project.dependencies.platform(libs.supabase.bom))
            implementation(libs.supabase.auth)
            implementation(libs.supabase.postgrest)

            implementation(libs.multiplatform.settings)

            implementation(libs.qrose)

            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
        }

        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.play.services.code.scanner)
        }

        iosMain.dependencies { implementation(libs.ktor.client.darwin) }

        val wasmJsMain by getting { dependencies { implementation(libs.ktor.client.js) } }

        val jvmMain by getting { dependencies { implementation(libs.ktor.client.okhttp) } }

        commonTest.dependencies { implementation(kotlin("test")) }

        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }

    compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }
}

compose.resources {
    publicResClass = false
    packageOfResClass = "fr.axllvy.insane.resources"
    generateResClass = auto
}

android {
    namespace = "fr.axllvy.insane"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "fr.axllvy.insane"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 3
        versionName = "0.2.0"
    }

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res")

    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }

    val keystorePropsFile = rootProject.file("keystore/keystore.properties")
    if (keystorePropsFile.exists()) {
        val keystoreProps =
            Properties().apply { keystorePropsFile.inputStream().use { stream -> load(stream) } }
        signingConfigs {
            create("release") {
                storeFile = rootProject.file("keystore/keystore.jks")
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
        buildTypes { getByName("release") { signingConfig = signingConfigs.getByName("release") } }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
