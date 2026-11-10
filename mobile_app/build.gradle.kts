// ==============================================================================
// Never Land Server Manager - Build Script Raiz (build.gradle.kts)
// ==============================================================================
// Declara as versões globais dos plugins utilizados no projeto sem aplicá-los na
// raiz (apply false), permitindo que os submódulos apliquem conforme necessário.
// ==============================================================================

plugins {
    // Android Gradle Plugin (AGP) versão 8.7.3
    // Fornece a infraestrutura de compilação, empacotamento do APK e geração de recursos Android
    id("com.android.application") version "8.7.3" apply false

    // Plugin Kotlin para compilação de código Android nativo versão 2.0.21
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false

    // Novo Plugin do Compilador Compose para Kotlin 2.0+ (JetBrains Compose Compiler Plugin)
    // A partir do Kotlin 2.0, o compilador do Compose é desacoplado do AGP e integrado nativamente
    // ao ciclo de compilação do Kotlin, dispensando a diretiva legada 'kotlinCompilerExtensionVersion'
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
