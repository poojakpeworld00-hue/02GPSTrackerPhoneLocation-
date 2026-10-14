plugins {

    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
}

android {
    namespace = "io.homedeck.grid"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {

        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {

        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
    compilerOptions {

        freeCompilerArgs.add("-Xskip-metadata-version-check")
    }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

configurations.configureEach {
    exclude(group = "com.android.support")
}

dependencies {
    api(libs.fossify.commons)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.material)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.glide)
    implementation(libs.timber)

    testImplementation(libs.junit)

    testImplementation(libs.org.json)
}
