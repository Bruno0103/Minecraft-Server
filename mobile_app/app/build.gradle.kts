// ==============================================================================
// Never Land Server Manager - Build Script do Módulo Principal (:app)
// ==============================================================================
// Configura a compilação do APK Android, compatibilidade de bytecode Java 17,
// suporte nativo a Jetpack Compose e catálogo de dependências com SSHJ e BouncyCastle.
// ==============================================================================

plugins {
    // Aplica o plugin de aplicação Android oficial
    id("com.android.application")
    // Aplica o suporte nativo à linguagem Kotlin para Android
    id("org.jetbrains.kotlin.android")
    // Aplica o plugin oficial do compilador Jetpack Compose para Kotlin 2.0+
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    // Namespace único do pacote de código-fonte da aplicação
    namespace = "com.neverland.servermanager"

    // Versão da API do Android SDK utilizada para compilar o código (Android 15)
    compileSdk = 35

    defaultConfig {
        // Identificador exclusivo da aplicação no dispositivo e Google Play
        applicationId = "com.neverland.servermanager"

        // Nível mínimo de API compatível (Android 8.0 Oreo - cobre >96% dos dispositivos)
        minSdk = 26

        // Nível de API alvo otimizado (Android 15)
        targetSdk = 35

        // Código sequencial da versão da aplicação (incrementado a cada release)
        versionCode = 1

        // Nome legível da versão da aplicação exibido aos usuários
        versionName = "1.0.0"

        // Executor oficial de testes instrumentados na JVM / Dispositivo Android
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Habilita suporte a recursos vetoriais do AndroidX em versões anteriores da API
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            // Em builds de debug inicial, manter falso para facilitar depuração
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // Habilita flags de depuração direta para testes em ambiente de desenvolvimento
            isDebuggable = true
        }
    }

    compileOptions {
        // Alinha o bytecode gerado pelo javac com Java 17 LTS (Eclipse Adoptium)
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        // Configura o compilador Kotlin para gerar bytecode compatível com a JVM 17
        jvmTarget = "17"
    }

    buildFeatures {
        // Ativa a geração de código e UI reativa do Jetpack Compose
        compose = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources {
            // Exclui arquivos de metadados duplicados em JARs de terceiros (SSHJ e BouncyCastle)
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/INDEX.LIST",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*",
                "/META-INF/versions/**",
                "META-INF/versions/**",
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            )
        }
    }
}

dependencies {
    // --------------------------------------------------------------------------
    // 1. AndroidX Core, Ciclo de Vida e Integração de Atividade com Compose
    // --------------------------------------------------------------------------
    // Extensões idiomáticas do Kotlin para APIs essenciais do Android
    implementation("androidx.core:core-ktx:1.15.0")
    // Ciclo de vida reativo e suporte a escopos de corrotinas (lifecycleScope)
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    // Suporte ao ViewModel reativo em funções Composable
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    // Integração da Activity nativa com a árvore de renderização do Compose
    implementation("androidx.activity:activity-compose:1.9.3")

    // --------------------------------------------------------------------------
    // 2. Jetpack Compose BOM (Bill of Materials) e Componentes de Interface
    // --------------------------------------------------------------------------
    // BOM para alinhamento unificado e compatibilidade garantida de versões do Compose
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)

    // Biblioteca de renderização visual e primitivas da UI do Jetpack Compose
    implementation("androidx.compose.ui:ui")
    // Gerenciamento de elementos gráficos, cores e bitmaps do Compose
    implementation("androidx.compose.ui:ui-graphics")
    // Suporte a pré-visualizações (@Preview) no Android Studio / sidecars
    implementation("androidx.compose.ui:ui-tooling-preview")
    // Componentes fundamentais de layout, rolagem e gestos
    implementation("androidx.compose.foundation:foundation")
    // Design System oficial Material Design 3 com suporte a tema escuro
    implementation("androidx.compose.material3:material3")
    // Conjunto completo de ícones estendidos do Material Design
    implementation("androidx.compose.material:material-icons-extended")
    // Navegação declarativa entre telas com suporte a backstack
    implementation("androidx.navigation:navigation-compose:2.8.3")

    // --------------------------------------------------------------------------
    // 3. Cliente de Conexão SSHv2 e Comunicação Remota com a VM
    // --------------------------------------------------------------------------
    // SSHJ: Biblioteca moderna e robusta para conexões seguras SSHv2, canais de comando e SFTP
    implementation("com.hierynomus:sshj:0.39.0")

    // --------------------------------------------------------------------------
    // 4. Provedor Criptográfico BouncyCastle
    // --------------------------------------------------------------------------
    // Fornece suporte a chaves Ed25519 e chaves RSA modernas em formato OpenSSH,
    // contornando limitações do provedor de segurança nativo do Android
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")

    // --------------------------------------------------------------------------
    // 5. Kotlinx Coroutines (Assincronismo e I/O de Rede)
    // --------------------------------------------------------------------------
    // Execução não bloqueante de chamadas SSH em background (Dispatchers.IO)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // --------------------------------------------------------------------------
    // 6. Armazenamento Seguro e Criptografia Local (Android KeyStore)
    // --------------------------------------------------------------------------
    // EncryptedSharedPreferences e MasterKey para persistência segura de credenciais SSH
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // --------------------------------------------------------------------------
    // 7. Testes Unitários e Testes de Interface
    // --------------------------------------------------------------------------
    // Framework de testes unitários locais JUnit 4 para validação de parsers e regras de negócio
    testImplementation("junit:junit:4.13.2")
    // Suporte a testes assíncronos e StateFlow com runTest em Coroutines
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")

    // Testes instrumentados no dispositivo ou emulador
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    // Ferramentas de depuração ativas exclusivamente no modo de debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
