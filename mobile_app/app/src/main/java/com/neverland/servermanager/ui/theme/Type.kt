package com.neverland.servermanager.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * ==============================================================================
 * Never Land Server Manager - Sistema Tipográfico Material 3 (Type.kt)
 * ==============================================================================
 * 
 * Configura as escalas tipográficas da aplicação utilizando as diretrizes do Material 3,
 * complementadas por definições especializadas para o terminal de logs e painéis métricos.
 * 
 * Filosofia de Design:
 * - Textos de interface utilizam a [FontFamily.Default] (Roboto no Android), garantindo
 *   clareza, internacionalização e renderização limpa em qualquer densidade de pixels.
 * - Módulos de terminal, saída de comandos e diagnósticos utilizam estritamente
 *   [FontFamily.Monospace] nativa do sistema, assegurando alinhamento columnar perfeito,
 *   espaçamento invariável de caracteres e ausência de dependências de fontes externas
 *   que possam onerar o build inicial.
 * ==============================================================================
 */

/**
 * Família de fontes monoespaçada nativa do Android para exibição de logs e terminal.
 */
val ConsoleFontFamily: FontFamily = FontFamily.Monospace

/**
 * Tipografia principal do aplicativo Never Land (Material 3 Typography).
 */
val NeverLandTypography = Typography(
    // Títulos de grande impacto (ex.: Cabeçalho da tela de Dashboard)
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.sp
    ),

    // Subtítulos de seções (ex.: Títulos de Cards de Recursos, Gráficos de RAM)
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.15.sp
    ),

    // Títulos de Cards operacionais e itens de lista de jogadores
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),

    // Títulos secundários de diálogos e formulários de conexão SSH
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.15.sp
    ),

    // Texto de corpo principal (ex.: descrições, labels de campos de entrada)
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),

    // Texto de corpo secundário (ex.: detalhes de versão, timestamps, legendas)
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.25.sp
    ),

    // Rótulos de botões de ação (Start, Stop, Restart, Conectar)
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.5.sp
    ),

    // Badges de status compactos (ex.: ONLINE, OFFLINE, XUID)
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp
    )
)

// ==============================================================================
// Estilos Tipográficos Especializados para Terminal / Console e Telemetria
// ==============================================================================

/**
 * Estilo para linhas de log exibidas no terminal interativo.
 * Usa fonte mono-espaçada com entrelinha compacta para maximizar o número
 * de linhas visíveis na tela do celular.
 */
val ConsoleLogTextStyle = TextStyle(
    fontFamily = ConsoleFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.sp,
    color = DiamondBlueLight
)

/**
 * Estilo para o campo de entrada (input de texto) de comandos do console BDS.
 */
val ConsoleInputTextStyle = TextStyle(
    fontFamily = ConsoleFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 18.sp,
    letterSpacing = 0.sp,
    color = TextPrimaryWhite
)

/**
 * Estilo para badges e tags de severidade no console ([INFO], [WARN], [ERROR], [CHAT]).
 */
val ConsoleBadgeTextStyle = TextStyle(
    fontFamily = ConsoleFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 10.sp,
    lineHeight = 12.sp,
    letterSpacing = 0.5.sp
)

/**
 * Estilo de alto impacto para exibição numérica de telemetria no Dashboard
 * (ex.: "458 MiB", "9/10 Jogadores", "38 ms").
 */
val MetricNumberTextStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.ExtraBold,
    fontSize = 26.sp,
    lineHeight = 30.sp,
    letterSpacing = 0.sp,
    color = TextPrimaryWhite
)
