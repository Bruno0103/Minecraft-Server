package com.neverland.servermanager.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * ==============================================================================
 * Never Land Server Manager - Destinos de Navegação (Screen.kt)
 * ==============================================================================
 *
 * Define o modelo de rotas e destinos da aplicação Android nativa "Never Land Control",
 * utilizada para administração e telemetria do servidor Bedrock Dedicated Server (BDS).
 *
 * Filosofia de Design e Arquitetura:
 * 1. Padrão Sealed Class: Permite uma representação segura e finita de destinos no
 *    grafo de navegação, facilitando verificações exaustivas em expressões `when`.
 * 2. Integração com Material Design 3: Cada destino encapsula uma rota identificadora
 *    (String única), um rótulo legível em Português do Brasil para exibição em menus
 *    e um ícone vetorial temático do catálogo estendido do Material Icons.
 * 3. Escalabilidade: Novos destinos, fluxos modais ou telas de detalhe podem ser
 *    acrescentados de forma limpa e tipada.
 * ==============================================================================
 */
sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    /**
     * Tela de Dashboard (Painel de Controle Principal).
     * Apresenta o status geral da unit systemd bedrock.service, métricas de memória RAM
     * obtidas via cgroups v2, contagem de jogadores online vs limite, uptime, integridade
     * do canal named pipe /run/bedrock.console e botões de ciclo de vida (Start/Stop/Restart).
     */
    data object Dashboard : Screen(
        route = "dashboard",
        title = "Dashboard",
        icon = Icons.Default.Dashboard
    )

    /**
     * Tela de Console Interativo (Terminal BDS).
     * Fornece janela de visualização de logs em tempo real com syntax highlight,
     * terminal escuro com fonte mono-espaçada, atalhos rápidos para comandos do utilitário mc
     * (/list, mc recursos, mc rede, mc config) e campo de texto para envio de comandos arbitrários.
     */
    data object Console : Screen(
        route = "console",
        title = "Console",
        icon = Icons.Default.Terminal
    )

    /**
     * Tela de Gestão de Jogadores (Player Management).
     * Oferece abas para visualização de jogadores atualmente online (com ações rápidas de Kick e Ban),
     * administração completa da Allowlist/Whitelist (adicionar Gamertag via FAB, remover e listar)
     * e histórico de logins/desconexões com identificadores XUID coletados de mc jogadores.
     */
    data object Players : Screen(
        route = "players",
        title = "Jogadores",
        icon = Icons.Default.People
    )

    /**
     * Tela de Configurações & Diagnóstico do Servidor.
     * Permite a parametrização do endereço do host SSH (IP 157.151.2.91 ou FQDN), porta,
     * usuário de acesso (opc), método de autenticação (chave privada Ed25519/RSA ou senha),
     * botão de teste de conexão com feedback imediato e atalhos para relatórios do sistema operacional.
     */
    data object Settings : Screen(
        route = "settings",
        title = "Configurações",
        icon = Icons.Default.Settings
    )

    companion object {
        /**
         * Lista imutável contendo os quatro destinos fundamentais exibidos
         * na BottomNavigationBar inferior da MainActivity.
         *
         * Nota Arquitetural (Prevenção de NullPointerException na JVM/ART):
         * Definida com getter dinâmico (`get()`) em vez de inicializador estático no `<clinit>`
         * da superclasse `Screen`. Na JVM/ART, quando uma subclasse `data object` (como
         * `Screen.Dashboard`) é referenciada pela primeira vez, a superclasse `Screen` é
         * inicializada antes das subclasses `data object`, o que faria uma lista estática
         * capturar referências `null` antes de `Dashboard.INSTANCE` existir.
         */
        val items: List<Screen>
            get() = listOf(Dashboard, Console, Players, Settings)
    }
}
