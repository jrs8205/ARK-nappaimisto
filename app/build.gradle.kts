import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

// Julkaisuavaimen tiedot luetaan gitin ulkopuolisesta tiedostosta;
// ilman sitä release-build jää allekirjoittamatta (esim. CI).
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "org.jarsi.ark"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.jarsi.ark.nappaimisto"
        minSdk = 26
        targetSdk = 36
        versionCode = 9
        versionName = "0.12.0"

        ndk {
            // Vain puhelinten arkkitehtuurit: ML Kitin käännöskirjaston
            // x86-versiot ovat emulaattoreita varten ja veivät APK:sta
            // yli puolet (~35 Mt).
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Julkaisu-APK saa versiollisen nimen (esim. ARK-nappaimisto-v0.11.0.apk),
    // jotta GitHub-releasen lataus on tunnistettava. Debug-nimi säilyy
    // ennallaan asennusskriptejä varten.
    applicationVariants.all {
        if (buildType.name == "release") {
            val apkName = "ark-nappaimisto-v$versionName.apk"
            outputs.all {
                (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl)
                    .outputFileName = apkName
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.preference)
    implementation(libs.material)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.emojipicker)
    // Ensiasennuksen esittelysivut pyyhkäistävinä.
    implementation(libs.androidx.viewpager2)
    // Paikallinen käännös: mallit ladataan kerran, käännös tapahtuu laitteella.
    implementation(libs.mlkit.translate)
    implementation(libs.mlkit.language.id)
    // Sanelun suoratoisto: WebSocket OpenAI:n realtime-transkriptioon.
    implementation(libs.okhttp)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    // Yksikkötesteissä android.jarin org.json on pelkkä tynkä; oikea
    // toteutus tuodaan testien luokkapolulle.
    testImplementation(libs.json)
}
