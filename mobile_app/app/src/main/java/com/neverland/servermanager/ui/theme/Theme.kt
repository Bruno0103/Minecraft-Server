package com.neverland.servermanager.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * ==============================================================================
 * Never Land Server Manager - Tema Principal Material 3 (Theme.kt)
 * ==============================================================================
 * 
 * Configura o sistema de design visual da aplicação utilizando o Material 3 (Material You),
 * adaptado com uma paleta de alto contraste inspirada no universo Minecraft Bedrock.
 * 
 * Destaques de Arquitetura:
 * 1. Dark Theme Especializado: Criado especificamente para painéis de monitoramento,
 *    terminais de comando e dashboards operacionais com fundo em Bedrock Dark (#121212).
 * 2. Gestão de Barras de Sistema: Sincroniza a cor da StatusBar e NavigationBar com
 *    a janela do Android através de [WindowCompat.getInsetsController].
 * 3. Integração com Tipografia: Associa os estilos de texto e famílias tipográficas
 *    definidos em [NeverLandTypography].
 * ==============================================================================
 */

/**
 * Esquema de Cores Escuro Oficial do Never Land Server Manager (Dark Color Scheme).
 * Projetado para máxima legibilidade, contraste elevado e baixo consumo de energia.
 */
private val NeverLandDarkColorScheme = darkColorScheme(
    // Acentos Primários - Verde Grama Minecraft (#4CAF50)
    primary = GrassGreenPrimary,
    onPrimary = Color.Black,
    primaryContainer = GrassGreenDark,
    onPrimaryContainer = TextPrimaryWhite,

    // Acentos Secundários - Azul Diamante (#00BCD4)
    secondary = DiamondBlueSecondary,
    onSecondary = Color.Black,
    secondaryContainer = DiamondBlueDark,
    onSecondaryContainer = TextPrimaryWhite,

    // Acentos Terciários - Amarelo Ouro (#FFC107)
    tertiary = GoldYellowTertiary,
    onTertiary = Color.Black,
    tertiaryContainer = GoldYellowDark,
    onTertiaryContainer = TextPrimaryWhite,

    // Estrutura de Telas e Fundo - Bedrock Dark (#121212)
    background = BedrockDarkBackground,
    onBackground = TextPrimaryWhite,

    // Superfície de Cards e Módulos - Cinza Rocha (#1E1E1E)
    surface = BedrockSurface,
    onSurface = TextPrimaryWhite,

    // Superfícies Elevadas (Modais, Menus, BottomSheet) - Cinza Ardósia (#262626)
    surfaceVariant = BedrockSurfaceElevated,
    onSurfaceVariant = TextSecondaryMuted,

    // Erros e Falhas Críticas - Vermelho Redstone (#F44336)
    error = RedstoneRedError,
    onError = TextPrimaryWhite,
    errorContainer = RedstoneRedDark,
    onErrorContainer = TextPrimaryWhite,

    // Linhas de Divisão e Bordas
    outline = BedrockBorderOutline,
    outlineVariant = Color(0xFF263238)
)

/**
 * Esquema de Cores Claro de Suporte (Light Color Scheme).
 * Mantém as cores essenciais do universo Never Land caso o sistema operacional force o modo diurno.
 */
private val NeverLandLightColorScheme = lightColorScheme(
    primary = GrassGreenDark,
    onPrimary = TextPrimaryWhite,
    primaryContainer = GrassGreenLight,
    onPrimaryContainer = Color.Black,

    secondary = DiamondBlueDark,
    onSecondary = TextPrimaryWhite,
    secondaryContainer = DiamondBlueLight,
    onSecondaryContainer = Color.Black,

    tertiary = GoldYellowDark,
    onTertiary = Color.Black,
    tertiaryContainer = GoldYellowLight,
    onTertiaryContainer = Color.Black,

    background = Color(0xFFF5F5F5),
    onBackground = Color(0xFF1A1A1A),

    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1A1A),

    surfaceVariant = Color(0xFFE0E0E0),
    onSurfaceVariant = Color(0xFF424242),

    error = RedstoneRedError,
    onError = TextPrimaryWhite,
    errorContainer = Color(0xFFFFCDD2),
    onErrorContainer = RedstoneRedDark,

    outline = Color(0xFF9E9E9E),
    outlineVariant = Color(0xFFBDBDBD)
)

/**
 * Composable do Tema Never Land.
 * 
 * Envolve a árvore de componentes da interface, aplicando o esquema de cores,
 * tipografia e estilização de janelas nativas.
 * 
 * @param darkTheme Determina se o tema escuro deve ser aplicado. Padrão: true (identidade do app).
 * @param content Conteúdo visual do Jetpack Compose que receberá o tema.
 */
@Composable
fun NeverLandTheme(
    darkTheme: Boolean = true, // Prioriza Dark Theme por padrão para estética gamer/terminal
    content: @Composable () -> Unit
) {
    // Seleciona o ColorScheme apropriado conforme a preferência de tema
    val colorScheme = if (darkTheme) NeverLandDarkColorScheme else NeverLandLightColorScheme

    // Configuração das barras de status e navegação do Android
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                // Configura a cor de fundo da StatusBar para acompanhar o Bedrock Dark
                window.statusBarColor = colorScheme.background.toArgb()
                // Configura a cor da NavigationBar inferior para acompanhar a barra de menus
                window.navigationBarColor = colorScheme.surface.toArgb()

                // Define o contraste dos ícones do sistema (StatusBar e NavigationBar):
                // No tema escuro, os ícones devem ser claros (isAppearanceLightStatusBars = false)
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    // Aplicação global do MaterialTheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = NeverLandTypography,
        content = content
    )
}
