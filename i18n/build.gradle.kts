plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.moko.resources)
}

kotlin {
    android {
        namespace = "org.solsticesw.vivlia.i18n"
        compileSdk = 37
        minSdk = 26
    }

    sourceSets {
        commonMain {
            dependencies {
                api(libs.moko.resources)
                implementation(libs.moko.resources.compose)
            }
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

multiplatformResources {
    resourcesPackage.set("org.solsticesw.vivlia.i18n")
}
