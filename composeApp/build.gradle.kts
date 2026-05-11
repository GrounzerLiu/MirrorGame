import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
}

kotlin {
    jvm()
    
    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.gson)
            implementation(libs.javacv)
            implementation(libs.ffmpeg)
            implementation("org.bytedeco:ffmpeg:8.0.1-1.5.13:linux-x86_64")
        }
    }
}


compose.desktop {
    application {
        mainClass = "grouzerliu.mirrorgame.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "MirrorGame"
            packageVersion = "1.0.0"
        }
    }
}

// Apply Linux-specific AWT workaround to desktop run tasks.
tasks.withType<JavaExec>().configureEach {
    if (
        System.getProperty("os.name").contains("Linux", ignoreCase = true) &&
        name.contains("run", ignoreCase = true)
    ) {
        environment("_JAVA_AWT_WM_NONREPARENTING", "1")
    }
}
