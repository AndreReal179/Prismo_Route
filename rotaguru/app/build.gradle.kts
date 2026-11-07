plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Versão: o número base fica aqui; o GitHub Actions completa com o nº da atualização dentro dela
// (3.0 → 3.0.0, 3.0.1, 3.0.2…). Para lançar uma versão "grande" nova, troque só o baseVersion (ex.: "3.1").
val baseVersion = "3.0"
val appVersionName = (findProperty("appVersionName") as String?)?.takeIf { it.isNotBlank() } ?: "$baseVersion.0"
val buildNumber = (findProperty("buildNumber") as String?)?.toIntOrNull() ?: 0
val ghRepo = (findProperty("ghRepo") as String?)?.takeIf { it.isNotBlank() } ?: "AndreReal179/Prismo_Route"

android {
    namespace = "com.rotaguru"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.rotaguru"
        minSdk = 26
        targetSdk = 34
        // 1000 + nº do build: sempre maior que as versões antigas (a 2.5 original era 16)
        versionCode = 1000 + buildNumber
        versionName = appVersionName
        // repositório "dono/nome" onde o app procura atualizações (vem do GitHub Actions)
        buildConfigField("String", "GH_REPO", "\"$ghRepo\"")
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
    buildFeatures {
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
