import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.Properties
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.aboutlibraries)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.dagger.hilt.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.baselineprofile)
    id("kotlin-parcelize")
}

abstract class CopyThirdPartyNotices : DefaultTask() {
    @get:InputFile
    abstract val sourceFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Inject
    abstract val fileSystemOperations: FileSystemOperations

    @TaskAction
    fun copyNotice() {
        fileSystemOperations.copy {
            from(sourceFile)
            into(outputDirectory)
        }
    }
}

/**
 * Puts the Whisper speech model (used to align lyrics to the audio, word by word) into the APK
 * assets. The 57 MB model is not committed to git: it is downloaded once into the Gradle user
 * home and always verified against its SHA-256 before it is packaged.
 */
abstract class FetchWhisperModel : DefaultTask() {
    @get:Input
    abstract val url: Property<String>

    @get:Input
    abstract val sha256: Property<String>

    @get:Internal
    abstract val cacheDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    private fun hashOf(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    @TaskAction
    fun fetch() {
        val expected = sha256.get()
        val cached = cacheDirectory.file("ggml-base-q5_1-$expected.bin").get().asFile
        if (!cached.exists() || hashOf(cached) != expected) {
            cached.parentFile.mkdirs()
            val partial = File(cached.parentFile, cached.name + ".part")
            URI(url.get()).toURL().openStream().use { input ->
                partial.outputStream().use { output -> input.copyTo(output) }
            }
            val actual = hashOf(partial)
            if (actual != expected) {
                partial.delete()
                throw GradleException("Whisper model hash mismatch: expected $expected, got $actual")
            }
            if (cached.exists()) cached.delete()
            partial.renameTo(cached)
        }
        val target = outputDirectory.file("whisper/ggml-base-q5_1.bin").get().asFile
        target.parentFile.mkdirs()
        cached.copyTo(target, overwrite = true)
    }
}

// Load keystore properties early to avoid unresolved references inside the android block
val keystoreProperties = Properties().apply {
    val propFile = rootProject.file("keystore.properties")
    if (propFile.exists()) {
        propFile.inputStream().use { load(it) }
    }
}

val localProperties = Properties().apply {
    val propFile = rootProject.file("local.properties")
    if (propFile.exists()) {
        propFile.inputStream().use { load(it) }
    }
}

val enableAbiSplits = providers.gradleProperty("pixelplay.enableAbiSplits")
    .getOrElse("true")
    .toBoolean()

val enableComposeCompilerReports = providers.gradleProperty("pixelplay.enableComposeCompilerReports")
    .getOrElse("false")
    .toBoolean()

val generatedNoticesAssets = layout.buildDirectory.dir("generated/assets/thirdPartyNotices")
val fetchWhisperModel = tasks.register<FetchWhisperModel>("fetchWhisperModel") {
    url.set("https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin")
    sha256.set("422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898")
    cacheDirectory.set(File(gradle.gradleUserHomeDir, "caches/musicbabe-whisper"))
    outputDirectory.set(layout.buildDirectory.dir("generated/whisperModel"))
}

val copyThirdPartyNotices = tasks.register<CopyThirdPartyNotices>("copyThirdPartyNotices") {
    sourceFile.set(rootProject.layout.projectDirectory.file("THIRD_PARTY_NOTICES.md"))
    outputDirectory.set(generatedNoticesAssets)
}

@Suppress("DEPRECATION")
android {
    namespace = "com.theveloper.pixelplay"
    compileSdk = 37

    sourceSets {
        getByName("androidTest") {
            assets.directories.add(file("$projectDir/schemas").path)
        }
    }

    androidResources {
        noCompress.add("tflite")
        // The Whisper model is memory-mapped/read as is: keep it uncompressed in the APK.
        noCompress.add("ggml-base-q5_1.bin")
    }

    ndkVersion = "29.0.14206865"

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.31.6"
        }
    }

    packaging {
        resources {
            excludes += listOf(
                "META-INF/INDEX.LIST",
                "META-INF/DEPENDENCIES",
                "/META-INF/io.netty.versions.properties",
                "META-INF/CONTRIBUTORS.md",
                "META-INF/NOTICE.txt",
                "META-INF/NOTICE.md"
            )
            pickFirsts += listOf(
                "META-INF/LICENSE.md",
                "META-INF/LICENSE.txt"
            )
        }
    }

    defaultConfig {
        applicationId = "com.theveloper.pixelplay"
        minSdk = 30
        targetSdk = 37
        versionCode = (project.findProperty("APP_VERSION_CODE") as? String)?.toInt() ?: 1
        versionName = (project.findProperty("APP_VERSION_NAME") as? String) ?: "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val telegramApiId = localProperties.getProperty("TELEGRAM_API_ID")?.ifEmpty { null }
            ?: "2040"
        val telegramApiHash = localProperties.getProperty("TELEGRAM_API_HASH")?.ifEmpty { null }
            ?: "b18441a1ff607e10a989891a5462e627"
        buildConfigField("int", "TELEGRAM_API_ID", telegramApiId)
        buildConfigField("String", "TELEGRAM_API_HASH", "\"$telegramApiHash\"")
    }

    signingConfigs {
        create("release") {
            storeFile = file("$rootDir/vz-pixelplay.jks")
            storePassword = keystoreProperties.getProperty("storePassword") ?: "dummyPassword"
            keyAlias = keystoreProperties.getProperty("keyAlias") ?: "dummyAlias"
            keyPassword = keystoreProperties.getProperty("keyPassword") ?: "dummyPassword"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }

        release {
            val keystoreFile = file("$rootDir/vz-pixelplay.jks")
            signingConfig = if (keystoreFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        create("benchmark") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            isDebuggable = false
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.all { it.useJUnitPlatform() }
    }

    lint {
        checkReleaseBuilds = false
    }

    splits {
        abi {
            isEnable = enableAbiSplits
            reset()
            if (enableAbiSplits) {
                include("arm64-v8a", "armeabi-v7a")
                isUniversalApk = false
            }
        }
    }

    bundle {
        abi.enableSplit = true
        density.enableSplit = true
        language.enableSplit = true
    }
}

androidComponents {
    onVariants(selector().all()) { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(
            copyThirdPartyNotices,
            CopyThirdPartyNotices::outputDirectory,
        )
        variant.sources.assets?.addGeneratedSourceDirectory(
            fetchWhisperModel,
            FetchWhisperModel::outputDirectory,
        )
    }
}

composeCompiler {
    // StrongSkipping is now enabled by default.
    stabilityConfigurationFiles.add(layout.projectDirectory.file("compose_stability.conf"))
}

baselineProfile {
    // Keep release builds fast to invoke locally, but make generated profiles usable as
    // startup dex-layout input once they are checked into the app.
    automaticGenerationDuringBuild = false
    saveInSrc = true
    dexLayoutOptimization = true
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.generateKotlin", "true")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)

        if (enableComposeCompilerReports) {
            val buildDir = project.layout.buildDirectory.get().asFile.absolutePath
            freeCompilerArgs.addAll(
                "-P", "plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=$buildDir/compose_compiler_reports",
                "-P", "plugin:androidx.compose.compiler.plugins.kotlin:metricsDestination=$buildDir/compose_compiler_metrics"
            )
        }

        freeCompilerArgs.addAll(
            "-P", "plugin:androidx.compose.compiler.plugins.kotlin:stabilityConfigurationPath=${project.rootDir.absolutePath}/app/compose_stability.conf"
        )
    }
}

dependencies {
    // Core & Optimization
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.androidx.profileinstaller)
    "baselineProfile"(project(":baselineprofile"))

    // AndroidX & Compose
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.lifecycleprocess)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.constraintlayout.compose)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.animation)
    implementation(libs.androidx.palette.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.ui.text.google.fonts)
    implementation(libs.material)
    implementation(libs.androidx.appcompat)
    implementation(libs.aboutlibraries.core)
    implementation(libs.aboutlibraries.compose.m3)

    // DI & Navigation
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.navigation.runtime.ktx)

    // Storage & Paging
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.paging.common)

    // Media & Files
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.exoplayer.ffmpeg)
    implementation(libs.androidx.media3.exoplayer.midi)
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.mediarouter)
    implementation(libs.androidx.media)
    implementation(libs.coil.compose)
    implementation(libs.taglib)
    implementation(libs.jaudiotagger)
    implementation(libs.vorbisjava.core)
    implementation(libs.wavy.slider)
    implementation(libs.androidx.graphics.shapes)

    // Networking & Serialization
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.okhttp)
    // YouTube streaming and music videos (GPL-3.0, see THIRD_PARTY_NOTICES.md)
    implementation(libs.newpipe.extractor)
    implementation(libs.logging.interceptor)
    implementation(libs.gson)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)

    // Identity & Background
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.credentials)
    implementation(libs.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.androidx.security.crypto)
    // Voice separation for lyrics alignment (Spleeter, downloaded on first use).
    implementation(libs.onnxruntime.android)
    implementation(libs.google.play.services.cast.framework)
    implementation(libs.tdlib)

    // UI Utilities & Extra
    implementation(libs.timber)
    implementation(libs.generativeai)
    implementation(libs.smooth.corner.rect.android.compose)
    implementation(libs.reorderables)
    implementation(libs.codeview)
    implementation(libs.androidx.glance)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.kuromoji.ipadic)
    implementation(libs.pinyin4j.core)
    implementation(libs.accompanist.drawablepainter)
    implementation(libs.accompanist.permissions)
    implementation(libs.capturable) {
        exclude(group = "androidx.compose.animation")
        exclude(group = "androidx.compose.foundation")
        exclude(group = "androidx.compose.material")
        exclude(group = "androidx.compose.runtime")
        exclude(group = "androidx.compose.ui")
    }

    // Projects
    implementation(project(":shared"))

    // Testing (Unit)
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.jupiter.engine)
    // JUnit 4 (Vintage) — required for legacy JUnit 4 tests under useJUnitPlatform()
    testImplementation(libs.junit)
    testRuntimeOnly(libs.junit.vintage.engine)
    testRuntimeOnly(libs.junitplatformlauncher)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.truth)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.androidx.room.testing)
    testImplementation(kotlin("test"))

    // Testing (Instrumentation)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.mockk)
    androidTestImplementation(libs.worktesting)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.androidx.benchmark.macro.junit4)
    androidTestImplementation(libs.androidx.uiautomator)

    // Debug
    debugImplementation(platform(libs.androidx.compose.bom))
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    constraints {
        // Fix vulnerabilities in transitive dependencies
        implementation(libs.netty.common)
        implementation(libs.netty.handler)
        implementation(libs.netty.codec.http)
        implementation(libs.netty.codec.http2)
        implementation(libs.bouncycastle.bcprov)
        implementation(libs.bouncycastle.bcpkix)
        implementation(libs.commons.lang3)
        implementation(libs.jdom2)
        implementation(libs.jose4j)
        implementation(libs.apache.httpclient)
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
