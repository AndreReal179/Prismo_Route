plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.rotaguru"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.rotaguru"
        minSdk = 26
        targetSdk = 34
        versionCode = 8
        versionName = "1.7"
    }

    // Chave fixa (rotaguru.jks): todo APK sai com a MESMA assinatura, então dá para atualizar
    // por cima do app instalado, sem desinstalar. NÃO apague nem troque o arquivo rotaguru.jks.
    signingConfigs {
        create("rotaguru") {
            storeFile = file("rotaguru.jks")
            storePassword = "rotaguru123"
            keyAlias = "rotaguru"
            keyPassword = "rotaguru123"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("rotaguru")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("rotaguru")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
