package com.neverland.servermanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.neverland.servermanager.data.repository.ServerRepositoryImpl
import com.neverland.servermanager.data.security.InMemorySecureStorage
import com.neverland.servermanager.data.security.SecurePreferences
import com.neverland.servermanager.data.security.SecureStorage
import com.neverland.servermanager.data.ssh.SshManager
import com.neverland.servermanager.domain.model.ConnectionState
import com.neverland.servermanager.domain.repository.IServerRepository
import com.neverland.servermanager.domain.repository.ISshManager
import com.neverland.servermanager.ui.console.ConsoleScreen
import com.neverland.servermanager.ui.console.ConsoleViewModel
import com.neverland.servermanager.ui.dashboard.DashboardScreen
import com.neverland.servermanager.ui.dashboard.DashboardViewModel
import com.neverland.servermanager.ui.navigation.Screen
import com.neverland.servermanager.ui.players.PlayersScreen
import com.neverland.servermanager.ui.players.PlayersSubTab
import com.neverland.servermanager.ui.players.PlayersViewModel
import com.neverland.servermanager.ui.settings.SettingsScreen
import com.neverland.servermanager.ui.settings.SettingsViewModel
import com.neverland.servermanager.ui.theme.BedrockBorderOutline
import com.neverland.servermanager.ui.theme.BedrockDarkBackground
import com.neverland.servermanager.ui.theme.BedrockSurface
import com.neverland.servermanager.ui.theme.BedrockSurfaceElevated
import com.neverland.servermanager.ui.theme.ConsoleBackgroundDark
import com.neverland.servermanager.ui.theme.ConsoleFontFamily
import com.neverland.servermanager.ui.theme.DiamondBlueDark
import com.neverland.servermanager.ui.theme.DiamondBlueLight
import com.neverland.servermanager.ui.theme.DiamondBlueSecondary
import com.neverland.servermanager.ui.theme.GoldYellowDark
import com.neverland.servermanager.ui.theme.GoldYellowTertiary
import com.neverland.servermanager.ui.theme.GrassGreenDark
import com.neverland.servermanager.ui.theme.GrassGreenPrimary
import com.neverland.servermanager.ui.theme.NeverLandTheme
import com.neverland.servermanager.ui.theme.RedstoneRedDark
import com.neverland.servermanager.ui.theme.RedstoneRedError
import com.neverland.servermanager.ui.theme.TextDisabledGrey
import com.neverland.servermanager.ui.theme.TextPrimaryWhite
import com.neverland.servermanager.ui.theme.TextSecondaryMuted

/**
 * ==============================================================================
 * Never Land Server Manager - Atividade Principal (MainActivity.kt)
 * ==============================================================================
 *
 * Ponto de entrada nativo da interface de usuário da aplicação Android.
 *
 * Arquitetura e Decisões de Implementação:
 * 1. Single-Activity Architecture: Utiliza uma única [ComponentActivity] como container
 *    da árvore declarativa do Jetpack Compose, eliminando a fragmentação de ciclo de vida
 *    e garantindo transições suaves entre as quatro seções operacionais.
 * 2. Edge-to-Edge: Ativa suporte a [enableEdgeToEdge], desenhando sob as barras de
 *    sistema nativas do Android (Status Bar e Navigation Bar) conforme exigido no
 *    Android 15 (targetSdk 35) e mantendo compatibilidade retroativa até o minSdk 26.
 * 3. Integração Completa dos 4 Módulos Reativos:
 *    - [DashboardScreen]: Telemetria de RAM (Cgroups v2), Uptime, Jogadores, FIFO e Ciclo de Vida.
 *    - [ConsoleScreen]: Terminal interativo REPL com syntax highlighting, filtros, atalhos e broadcast.
 *    - [PlayersScreen]: Gestão de Jogadores Online, Whitelist/Allowlist, Histórico XUID, Kick, Ban e Hot-Backup.
 *    - [SettingsScreen]: Configuração SSH, Cofre Android KeyStore, importação de chaves e Diagnóstico da VM.
 * 4. Auto-Conexão Inteligente: Ao abrir o app no smartphone, conecta automaticamente ao servidor
 *    Never Land OCI utilizando as credenciais persistidas no Android KeyStore.
 * ==============================================================================
 */
class MainActivity : ComponentActivity() {

    // Instâncias compartilhadas do gerenciador SSH e repositório do servidor
    private val sshManager: ISshManager by lazy { SshManager() }
    private val serverRepository: IServerRepository by lazy { ServerRepositoryImpl(sshManager) }
    private val secureStorage: SecureStorage by lazy { SecurePreferences.getInstance(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Habilita o modo Edge-to-Edge moderno do AndroidX
        enableEdgeToEdge()

        // Provedor da chave oficial da VM Never Land empacotada nos assets do APK
        val bundledKeyProvider: () -> String? = {
            try {
                assets.open("neverland_oci.key").bufferedReader().use { it.readText() }
            } catch (_: Exception) {
                null
            }
        }

        // Inicializa a árvore de componentes visuais do Jetpack Compose
        setContent {
            NeverLandTheme {
                NeverLandMainScaffold(
                    sshManager = sshManager,
                    serverRepository = serverRepository,
                    secureStorage = secureStorage,
                    bundledOciKeyProvider = bundledKeyProvider
                )
            }
        }
    }
}

/**
 * Estrutura visual mestre (Scaffold) do Never Land Server Manager.
 *
 * Gerencia o estado de navegação ativo entre as quatro telas fundamentais
 * utilizando [rememberSaveable] com rota primitiva `String`, preservando
 * a seleção durante rotações de tela sem falhas de serialização de `ImageVector`.
 */
@Composable
fun NeverLandMainScaffold(
    sshManager: ISshManager? = null,
    serverRepository: IServerRepository? = null,
    secureStorage: SecureStorage? = null,
    bundledOciKeyProvider: () -> String? = { null }
) {
    val context = LocalContext.current

    // Instâncias de fallback gerenciadas via remember caso não injetadas explicitamente
    val effectiveSshManager = sshManager ?: remember { SshManager() }
    val effectiveServerRepo = serverRepository ?: remember(effectiveSshManager) {
        ServerRepositoryImpl(effectiveSshManager)
    }
    val effectiveSecureStorage = secureStorage ?: remember {
        try {
            SecurePreferences.getInstance(context)
        } catch (_: Throwable) {
            InMemorySecureStorage()
        }
    }

    // ViewModels das 4 seções operacionais do aplicativo
    val dashboardViewModel: DashboardViewModel = viewModel(
        factory = DashboardViewModel.Factory(effectiveSshManager, effectiveServerRepo)
    )
    val consoleViewModel: ConsoleViewModel = viewModel(
        factory = ConsoleViewModel.Factory(effectiveSshManager, effectiveServerRepo)
    )
    val playersViewModel: PlayersViewModel = viewModel(
        factory = PlayersViewModel.Factory(effectiveSshManager, effectiveServerRepo)
    )
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.Factory(
            sshManager = effectiveSshManager,
            serverRepository = effectiveServerRepo,
            secureStorage = effectiveSecureStorage,
            bundledOciKeyProvider = bundledOciKeyProvider
        )
    )

    // Coleta reativa dos estados das 4 telas
    val dashboardUiState by dashboardViewModel.uiState.collectAsState()
    val consoleUiState by consoleViewModel.uiState.collectAsState()
    val playersUiState by playersViewModel.uiState.collectAsState()
    val settingsUiState by settingsViewModel.uiState.collectAsState()

    // Auto-conexão automática na inicialização caso haja credenciais válidas salvas no KeyStore
    LaunchedEffect(Unit) {
        if (!effectiveSshManager.isConnected() && effectiveSecureStorage.hasSavedCredentials()) {
            val savedConfig = effectiveSecureStorage.loadSshConfig()
            if (savedConfig.hasValidCredentials()) {
                effectiveSshManager.connect(savedConfig)
            }
        }
    }

    // Estado reativo da rota atualmente selecionada na BottomNavigationBar (String serializável)
    var currentRoute by rememberSaveable { mutableStateOf(Screen.Dashboard.route) }
    val currentScreen = Screen.items.firstOrNull { it.route == currentRoute } ?: Screen.Dashboard

    Scaffold(
        topBar = {
            NeverLandTopBar(
                currentScreen = currentScreen,
                connectionState = dashboardUiState.connectionState,
                onStatusBadgeClick = {
                    currentRoute = Screen.Settings.route
                },
                onRefreshClick = {
                    if (!effectiveSshManager.isConnected()) {
                        settingsViewModel.testSshConnectionNow()
                    }
                    when (currentScreen) {
                        is Screen.Dashboard -> dashboardViewModel.refreshStatus(silent = false)
                        is Screen.Console -> consoleViewModel.refreshLogs(silent = false)
                        is Screen.Players -> playersViewModel.refreshAllData()
                        is Screen.Settings -> settingsViewModel.testSshConnectionNow()
                    }
                }
            )
        },
        bottomBar = {
            NeverLandBottomBar(
                currentScreen = currentScreen,
                onScreenSelected = { selectedScreen ->
                    currentRoute = selectedScreen.route
                }
            )
        },
        containerColor = BedrockDarkBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                is Screen.Dashboard -> DashboardScreen(
                    uiState = dashboardUiState,
                    onRefreshClick = {
                        if (!effectiveSshManager.isConnected()) {
                            settingsViewModel.testSshConnectionNow()
                        } else {
                            dashboardViewModel.refreshStatus(silent = false)
                        }
                    },
                    onToggleAutoRefresh = { dashboardViewModel.toggleAutoRefresh() },
                    onLifecycleActionClick = { action -> dashboardViewModel.requestLifecycleAction(action) },
                    onConfirmLifecycleAction = { dashboardViewModel.confirmLifecycleAction() },
                    onDismissConfirmationDialog = { dashboardViewModel.dismissConfirmationDialog() },
                    onNavigateToPlayers = {
                        playersViewModel.selectTab(PlayersSubTab.ONLINE)
                        currentRoute = Screen.Players.route
                    },
                    onNavigateToSettings = { currentRoute = Screen.Settings.route },
                    onClearSnackbar = { dashboardViewModel.clearSnackbar() },
                    onTriggerHotBackup = {
                        currentRoute = Screen.Players.route
                        playersViewModel.triggerHotBackup()
                    }
                )

                is Screen.Console -> ConsoleScreen(
                    uiState = consoleUiState,
                    onCommandInputChanged = { consoleViewModel.updateCommandInput(it) },
                    onSendCommand = { consoleViewModel.sendCurrentCommand() },
                    onSendQuickCommand = { consoleViewModel.sendQuickCommand(it) },
                    onRefreshLogs = { consoleViewModel.refreshLogs(silent = false) },
                    onToggleAutoScroll = { consoleViewModel.toggleAutoScroll() },
                    onToggleLiveTail = { consoleViewModel.toggleLiveTail() },
                    onSelectFilter = { consoleViewModel.selectFilter(it) },
                    onToggleSearch = { consoleViewModel.toggleSearchVisibility() },
                    onSearchQueryChanged = { consoleViewModel.updateSearchQuery(it) },
                    onClearConsole = { consoleViewModel.clearConsoleBuffer() },
                    onOpenBroadcastDialog = { consoleViewModel.setShowBroadcastDialog(true) },
                    onDismissBroadcastDialog = { consoleViewModel.setShowBroadcastDialog(false) },
                    onBroadcastInputChanged = { consoleViewModel.updateBroadcastInput(it) },
                    onConfirmBroadcast = { consoleViewModel.confirmSendBroadcast() },
                    onClearSnackbar = { consoleViewModel.clearSnackbar() }
                )

                is Screen.Players -> PlayersScreen(
                    uiState = playersUiState,
                    onSelectTab = { playersViewModel.selectTab(it) },
                    onRefresh = { playersViewModel.refreshAllData() },
                    onSearchQueryChanged = { playersViewModel.updateSearchQuery(it) },
                    onOpenAddWhitelistDialog = { playersViewModel.openAddWhitelistDialog(it) },
                    onDismissAddWhitelistDialog = { playersViewModel.dismissAddWhitelistDialog() },
                    onNewWhitelistGamertagChanged = { playersViewModel.updateNewWhitelistGamertag(it) },
                    onConfirmAddWhitelist = { playersViewModel.confirmAddToWhitelist(it) },
                    onRemoveFromWhitelist = { playersViewModel.removeFromWhitelist(it) },
                    onReloadWhitelist = { playersViewModel.reloadWhitelist() },
                    onRequestKick = { playersViewModel.requestKickPlayer(it) },
                    onRequestBan = { playersViewModel.requestBanPlayer(it) },
                    onModerationReasonChanged = { playersViewModel.updateModerationReason(it) },
                    onConfirmModeration = { playersViewModel.confirmModerationAction() },
                    onDismissModeration = { playersViewModel.dismissModerationDialog() },
                    onOpenQuickActions = { playersViewModel.openQuickActionsForPlayer(it) },
                    onDismissQuickActions = { playersViewModel.dismissQuickActionsDialog() },
                    onChangeGameMode = { tag, mode -> playersViewModel.changePlayerGameMode(tag, mode) },
                    onChangeOperator = { tag, grant -> playersViewModel.changePlayerOperator(tag, grant) },
                    onTriggerHotBackup = { playersViewModel.triggerHotBackup() },
                    onClearSnackbar = { playersViewModel.clearSnackbar() }
                )

                is Screen.Settings -> SettingsScreen(
                    uiState = settingsUiState,
                    onHostChanged = { settingsViewModel.updateHost(it) },
                    onPortChanged = { settingsViewModel.updatePort(it) },
                    onUsernameChanged = { settingsViewModel.updateUsername(it) },
                    onAuthTypeChanged = { settingsViewModel.updateAuthType(it) },
                    onPrivateKeyChanged = { settingsViewModel.updatePrivateKeyPem(it) },
                    onPassphraseChanged = { settingsViewModel.updatePassphrase(it) },
                    onPasswordChanged = { settingsViewModel.updatePassword(it) },
                    onToggleKeyVisibility = { settingsViewModel.toggleKeyVisibility() },
                    onTogglePasswordVisibility = { settingsViewModel.togglePasswordVisibility() },
                    onLoadDefaultProfile = { settingsViewModel.loadDefaultNeverLandProfile() },
                    onKeyFileImported = { settingsViewModel.onKeyFileImported(it) },
                    onSaveAndConnect = { settingsViewModel.saveAndConnect() },
                    onTestConnection = { settingsViewModel.testSshConnectionNow() },
                    onDisconnect = { settingsViewModel.disconnectSsh() },
                    onClearCredentials = { settingsViewModel.clearSavedCredentials() },
                    onRunDiagnostic = { settingsViewModel.runDiagnostic(it) },
                    onDismissDiagnostic = { settingsViewModel.dismissDiagnosticDialog() },
                    onClearSnackbar = { settingsViewModel.clearSnackbar() }
                )
            }
        }
    }
}

// ==============================================================================
// Componentes de Topo e Base (TopBar & BottomBar)
// ==============================================================================

/**
 * Barra superior da aplicação (Material 3 TopAppBar).
 *
 * Exibe o título oficial "Never Land Control", o subtítulo de ambiente do servidor,
 * um badge clicável de estado da conexão SSH e um botão de ação rápida para atualizar dados.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeverLandTopBar(
    currentScreen: Screen,
    connectionState: ConnectionState? = null,
    onStatusBadgeClick: () -> Unit = {},
    onRefreshClick: () -> Unit
) {
    val (statusLabel, statusDotColor) = when (connectionState) {
        is ConnectionState.Connected -> "SSH Conectado" to GrassGreenPrimary
        is ConnectionState.Connecting -> "Conectando..." to GoldYellowTertiary
        is ConnectionState.Error -> "SSH Erro" to RedstoneRedError
        is ConnectionState.Disconnected, null -> "SSH Pronto" to TextDisabledGrey
    }

    TopAppBar(
        title = {
            Column {
                Text(
                    text = "Never Land Control",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryWhite
                )
                Text(
                    text = "${currentScreen.title} • OCI Oracle Linux 9.7",
                    style = MaterialTheme.typography.bodySmall,
                    color = DiamondBlueSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        actions = {
            // Badge clicável de status da conexão SSH (leva para Ajustes ao tocar)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = BedrockSurfaceElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, BedrockBorderOutline),
                modifier = Modifier
                    .padding(end = 4.dp)
                    .clickable { onStatusBadgeClick() }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusDotColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimaryWhite
                    )
                }
            }

            // Botão de atualização da tela ativa
            IconButton(onClick = onRefreshClick) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Atualizar telemetria do servidor",
                    tint = TextPrimaryWhite
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = BedrockSurface,
            titleContentColor = TextPrimaryWhite
        )
    )
}

/**
 * Barra de navegação inferior (Material 3 NavigationBar).
 */
@Composable
fun NeverLandBottomBar(
    currentScreen: Screen,
    onScreenSelected: (Screen) -> Unit
) {
    NavigationBar(
        containerColor = BedrockSurface,
        tonalElevation = 8.dp
    ) {
        Screen.items.forEach { screen ->
            val isSelected = currentScreen.route == screen.route

            NavigationBarItem(
                selected = isSelected,
                onClick = { onScreenSelected(screen) },
                icon = {
                    Icon(
                        imageVector = screen.icon,
                        contentDescription = screen.title
                    )
                },
                label = {
                    Text(
                        text = screen.title,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = GrassGreenPrimary,
                    selectedTextColor = GrassGreenPrimary,
                    indicatorColor = GrassGreenDark.copy(alpha = 0.4f),
                    unselectedIconColor = TextSecondaryMuted,
                    unselectedTextColor = TextSecondaryMuted
                )
            )
        }
    }
}

// ==============================================================================
// Componentes Auxiliares Compartilhados e Placeholders Legados
// ==============================================================================

/**
 * Card métrico com valor de destaque, subtítulo informativo e barra opcional.
 */
@Composable
fun MetricCard(
    title: String,
    value: String,
    subtext: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    progress: Float? = null
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = BedrockSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, BedrockBorderOutline)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondaryMuted
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimaryWhite
            )

            if (progress != null) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = accentColor,
                    trackColor = BedrockSurfaceElevated
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = subtext,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondaryMuted,
                fontSize = 10.sp
            )
        }
    }
}

/**
 * Linha de informação de configuração chave-valor.
 */
@Composable
fun ConfigInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = TextSecondaryMuted)
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimaryWhite
        )
    }
}

/**
 * Botão retangular para comandos de diagnóstico avançado do sistema.
 */
@Composable
fun DiagnosticButton(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = BedrockSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, BedrockBorderOutline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimaryWhite
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondaryMuted
                )
            }
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = DiamondBlueSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
