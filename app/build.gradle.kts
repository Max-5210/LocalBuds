import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.localbuds"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.localbuds"
        minSdk = 28 // DynamicsProcessing (the pro EQ) needs Android 9+
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    sourceSets {
        getByName("main") {
            res.directories.add("build/generated/customIcons/res")
            assets.directories.add("build/generated/customIcons/assets")
        }
    }

    androidResources {
        localeFilters += listOf("en")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ""
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        aidl = true // ICodecService, the Shizuku user service interface
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    packaging {
        dex {
            useLegacyPackaging = true
        }
        jniLibs {
            useLegacyPackaging = true
        }
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "DebugProbesKt.bin")
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":protocol"))
    implementation(project(":dsp"))

    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

    // Shizuku user-level codec service
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // Root shell (com.localbuds.root) via libsu
    implementation("com.github.topjohnwu.libsu:core:6.0.0")

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.register("syncCustomIcons") {
    val dropInDir = layout.projectDirectory.dir("custom_icons")
    val outResDir = layout.buildDirectory.dir("generated/customIcons/res")
    val outAssetsDir = layout.buildDirectory.dir("generated/customIcons/assets")
    inputs.dir(dropInDir).optional()
    outputs.dir(outResDir)
    outputs.dir(outAssetsDir)
    doLast {
        val resRoot = outResDir.get().asFile
        val assetsRoot = outAssetsDir.get().asFile
        resRoot.deleteRecursively()
        assetsRoot.deleteRecursively()

        val drawableDir = File(resRoot, "drawable").apply { mkdirs() }
        // app_icon.png (full-bleed artwork, tile centred) becomes the adaptive launcher foreground.
        // The layer-list's negative offsets zoom it slightly so the tile fills the launcher mask.
        val customAppIcon = dropInDir.file("app_icon.png").asFile
        if (customAppIcon.isFile) {
            val nodpiDir = File(resRoot, "drawable-nodpi").apply { mkdirs() }
            customAppIcon.copyTo(File(nodpiDir, "ic_launcher_custom_png.png"), overwrite = true)
            File(drawableDir, "ic_launcher_foreground.xml").writeText(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <layer-list xmlns:android="http://schemas.android.com/apk/res/android">
                    <item
                        android:left="-6dp"
                        android:top="-6dp"
                        android:right="-6dp"
                        android:bottom="-6dp"
                        android:drawable="@drawable/ic_launcher_custom_png" />
                </layer-list>
                """.trimIndent()
            )
        } else {
            File(drawableDir, "ic_launcher_foreground.xml").writeText(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <inset xmlns:android="http://schemas.android.com/apk/res/android"
                    android:drawable="@drawable/ic_launcher_foreground_default"
                    android:inset="0dp" />
                """.trimIndent()
            )
        }

        // Adaptive icon XMLs are generated so the themed (monochrome) layer is only used for the
        // built-in artwork: with a custom app_icon.png the launcher must show that picture, not a
        // monochrome silhouette of the old art.
        val mipmapDir = File(resRoot, "mipmap-anydpi-v26").apply { mkdirs() }
        val monochromeLine = if (customAppIcon.isFile) "" else "    <monochrome android:drawable=\"@drawable/ic_launcher_monochrome\" />\n"
        val adaptiveXml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<adaptive-icon xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
            "    <background android:drawable=\"@drawable/ic_launcher_background\" />\n" +
            "    <foreground android:drawable=\"@drawable/ic_launcher_foreground\" />\n" +
            monochromeLine +
            "</adaptive-icon>\n"
        File(mipmapDir, "ic_launcher.xml").writeText(adaptiveXml)
        File(mipmapDir, "ic_launcher_round.xml").writeText(adaptiveXml)
    }
}

tasks.named("preBuild").configure {
    dependsOn("syncCustomIcons")
}

tasks.register("assembleGithubDebug") {
    dependsOn("assembleDebug")
}



