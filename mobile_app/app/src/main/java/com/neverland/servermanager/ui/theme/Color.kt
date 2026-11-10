package com.neverland.servermanager.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * ==============================================================================
 * Never Land Server Manager - Paleta de Cores Material 3 (Color.kt)
 * ==============================================================================
 * 
 * Define a paleta de cores oficial do aplicativo móvel de administração do
 * servidor Never Land Minecraft Bedrock.
 * 
 * Inspiração Visual:
 * A identidade visual combina a estética dos blocos e minérios icônicos do Minecraft
 * (Grama, Diamante, Redstone, Ouro e Bedrock) com uma interface moderna de alta
 * usabilidade no padrão Material 3, garantindo excelente contraste, legibilidade
 * e ergonomia tátil em ambientes de iluminação reduzida (Dark Theme).
 * 
 * As cores de console e alertas também mantêm estrita paridade com a paleta
 * consagrada do painel administrativo desktop (mc_gui.py).
 * ==============================================================================
 */

// ==============================================================================
// 1. Cores Primárias - Verde Grama (Grass Green)
// Representa o status "Online / Ativo", vitalidade do servidor e ações afirmativas.
// ==============================================================================

/** Verde Grama Vibrante (#4CAF50) - Cor primária do tema e badge de servidor online */
val GrassGreenPrimary = Color(0xFF4CAF50)

/** Verde Grama Escuro (#2E7D32) - Usado em containers primários e botões com ênfase média */
val GrassGreenDark = Color(0xFF2E7D32)

/** Verde Suave (#81C784) - Variante mais clara para textos e ícones sobre fundos escuros */
val GrassGreenLight = Color(0xFF81C784)

/** Verde de Sucesso no Terminal (#55C94F) - Mensagens de sucesso do utilitário mc */
val TerminalGreenSuccess = Color(0xFF55C94F)


// ==============================================================================
// 2. Cores Secundárias - Azul Diamante (Diamond Blue)
// Representa a conexão de rede, telemetria de latência, XUIDs e métricas operacionais.
// ==============================================================================

/** Azul Diamante (#00BCD4) - Cor secundária do tema, chips de status de rede e latência */
val DiamondBlueSecondary = Color(0xFF00BCD4)

/** Azul Diamante Escuro (#00838F) - Containers secundários e bordas ativas */
val DiamondBlueDark = Color(0xFF00838F)

/** Azul Céu / Diamante Claro (#8BD0EE) - Logs informativos e cabeçalhos de terminal */
val DiamondBlueLight = Color(0xFF8BD0EE)

/** Azul Ciano Neon (#55D7FF) - Cursor piscante do console e destaques de comando */
val TerminalBlueCyan = Color(0xFF55D7FF)


// ==============================================================================
// 3. Cores de Alerta / Destrutivas - Redstone Red
// Representa o status "Offline / Parado", falhas de conexão, erros e ações de risco (Stop, Kick, Ban).
// ==============================================================================

/** Vermelho Redstone (#F44336) - Indicador de servidor desligado, falhas e botões de perigo */
val RedstoneRedError = Color(0xFFF44336)

/** Vermelho Redstone Escuro (#B71C1C) - Containers de erro e fundos de diálogos de alerta */
val RedstoneRedDark = Color(0xFFB71C1C)

/** Vermelho Redstone Suave (#E57373) - Textos de mensagens de erro detalhadas */
val RedstoneRedLight = Color(0xFFE57373)

/** Vermelho de Erro no Terminal (#D94B45) - Linhas de erro fatal e stacktraces do BDS */
val TerminalRedError = Color(0xFFD94B45)


// ==============================================================================
// 4. Cores Terciárias - Ouro / Âmbar (Gold Yellow)
// Representa advertências, reinicialização agendada, backup em progresso e estados transitórios.
// ==============================================================================

/** Amarelo Ouro Minecraft (#FFC107) - Alertas, aviso prévio aos jogadores e cronômetros */
val GoldYellowTertiary = Color(0xFFFFC107)

/** Ouro Queimado Escuro (#FFA000) - Containers terciários de aviso */
val GoldYellowDark = Color(0xFFFFA000)

/** Amarelo Suave (#FFE082) - Chips informativos de memória RAM em nível moderado */
val GoldYellowLight = Color(0xFFFFE082)

/** Âmbar de Alerta no Terminal (#F3C64E) - Warnings emitidos pelo servidor Bedrock */
val TerminalAmberWarning = Color(0xFFF3C64E)


// ==============================================================================
// 5. Cores Especiais de Console e Chat
// Utilizadas no terminal interativo para diferenciar mensagens globais de broadcast.
// ==============================================================================

/** Roxo / Magenta (#E040FB) - Mensagens de chat in-game (/say) e anúncios do servidor */
val TerminalChatPurple = Color(0xFFE040FB)


// ==============================================================================
// 6. Superfícies, Fundos e Estrutura - Bedrock Dark
// Tema escuro profundo com base em rocha-matriz (Bedrock), economizando bateria em telas OLED.
// ==============================================================================

/** Preto Bedrock Base (#121212) - Fundo de tela principal de toda a aplicação */
val BedrockDarkBackground = Color(0xFF121212)

/** Cinza Rocha Superfície (#1E1E1E) - Superfície de Cards, listas e campos de texto */
val BedrockSurface = Color(0xFF1E1E1E)

/** Cinza Ardósia Elevado (#262626) - Superfície de modais, diálogos e menus suspensos */
val BedrockSurfaceElevated = Color(0xFF262626)

/** Cinza de Linha de Grade / Borda (#37474F) - Bordas sutis de separação de seções */
val BedrockBorderOutline = Color(0xFF37474F)

/** Azul Marinho Profundo (#102238) - Fundo exclusivo da janela do console/terminal */
val ConsoleBackgroundDark = Color(0xFF102238)


// ==============================================================================
// 7. Tipografia e Conteúdo (Textos e Ícones)
// Alto contraste e clareza de leitura sobre superfícies escuras.
// ==============================================================================

/** Branco Puro (#FFFFFF) - Títulos, valores de métricas e texto principal */
val TextPrimaryWhite = Color(0xFFFFFFFF)

/** Cinza Claro Metálico (#B0BEC5) - Rótulos secundários, unidades de medida e dicas */
val TextSecondaryMuted = Color(0xFFB0BEC5)

/** Cinza Desabilitado (#607D8B) - Botões e campos em estado inativo */
val TextDisabledGrey = Color(0xFF607D8B)
