// ==============================================================================
// Never Land Server Manager - Configuração de Módulos e Repositórios (settings.gradle.kts)
// ==============================================================================
// Este arquivo configura a resolução de plugins, fontes de dependências Maven e os
// módulos participantes do projeto Android Never Land.
// ==============================================================================

pluginManagement {
    repositories {
        // Repositório oficial do Google para Android Gradle Plugin e ferramentas do SDK
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        // Repositório central para plugins do ecossistema Kotlin e Java
        mavenCentral()
        // Portal oficial de plugins do Gradle
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // Garante que nenhum módulo individual declare repositórios próprios, centralizando
    // o gerenciamento de repositórios neste arquivo settings.gradle.kts
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Dependências de componentes do Android, AndroidX e Google Play Services
        google()
        // Dependências de terceiros, incluindo SSHJ, BouncyCastle, Coroutines e JUnit
        mavenCentral()
    }
}

// Nome oficial do projeto raiz exibido no Gradle e no Android Studio
rootProject.name = "NeverLandServerManager"

// Inclusão do módulo da aplicação Android (:app)
include(":app")
