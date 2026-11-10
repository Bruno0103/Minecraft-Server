package com.neverland.servermanager.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neverland.servermanager.domain.model.ConnectionState
import com.neverland.servermanager.domain.model.LifecycleAction
import com.neverland.servermanager.domain.model.ServerHealth
import com.neverland.servermanager.domain.model.ServerStatus
import com.neverland.servermanager.ui.theme.BedrockBorderOutline
import com.neverland.servermanager.ui.theme.BedrockDarkBackground
import com.neverland.servermanager.ui.theme.BedrockSurface
import com.neverland.servermanager.ui.theme.BedrockSurfaceElevated
import com.neverland.servermanager.ui.theme.DiamondBlueDark
import com.neverland.servermanager.ui.theme.DiamondBlueLight
import com.neverland.servermanager.ui.theme.DiamondBlueSecondary
import com.neverland.servermanager.ui.theme.GoldYellowDark
import com.neverland.servermanager.ui.theme.GoldYellowTertiary
import com.neverland.servermanager.ui.theme.GrassGreenDark
import com.neverland.servermanager.ui.theme.GrassGreenPrimary
import com.neverland.servermanager.ui.theme.RedstoneRedDark
import com.neverland.servermanager.ui.theme.RedstoneRedError
import com.neverland.servermanager.ui.theme.TextDisabledGrey
import com.neverland.servermanager.ui.theme.TextPrimaryWhite
import com.neverland.servermanager.ui.theme.TextSecondaryMuted

/**
 * ==============================================================================
 * Never Land Server Manager - Tela de Dashboard (DashboardScreen.kt)
 * ==============================================================================
 *
 * Painel central de controle e telemetria em tempo real do servidor Never Land BDS.
 *
 * Principais Funcionalidades:
 * 1. Indicador e Banner Dinâmico de Conexão SSH (com atalho para Configurações).
 * 2. Card de Status Geral com Badge Semântico (Verde Saudável, Âmbar Alerta, Vermelho Crítico/Parado).
 * 3. Card de Consumo de Memória RAM com Barra Tricolor de Progresso (Faixas Cgroups v2: 600M / 750M).
 * 4. Card de Jogadores Conectados com atalho direto para a aba de Jogadores.
 * 5. Card de Integridade do Canal Console (Named Pipe FIFO /run/bedrock.console).
 * 6. Controles de Ciclo de Vida (Iniciar, Parar, Reiniciar) blindados com AlertDialog modal de confirmação.
 * 7. Botão de Atualização com animação contínua de rotação 360° durante o carregamento.
 * ==============================================================================
 */
@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    onRefreshClick: () -> Unit,
    onToggleAutoRefresh: () -> Unit,
    onLifecycleActionClick: (LifecycleAction) -> Unit,
    onConfirmLifecycleAction: () -> Unit,
    onDismissConfirmationDialog: () -> Unit,
    onNavigateToPlayers: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onClearSnackbar: () -> Unit,
    onTriggerHotBackup: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }

    // Efeito para disparar exibição de Snackbar quando uma nova mensagem surgir
    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            onClearSnackbar()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BedrockDarkBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Cabeçalho de Ações Rápidas (Auto-Refresh Toggle e Botão Atualizar com Rotação 360°)
            DashboardHeaderBar(
                isLoading = uiState.isLoading,
                isAutoRefreshEnabled = uiState.isAutoRefreshEnabled,
                onRefreshClick = onRefreshClick,
                onToggleAutoRefresh = onToggleAutoRefresh
            )

            // 2. Banner de Estado da Conexão SSH
            SshConnectionStatusBanner(
                connectionState = uiState.connectionState,
                onNavigateToSettings = onNavigateToSettings
            )

            // 3. Indicador de Ação em Andamento (Start/Stop/Restart)
            AnimatedVisibility(
                visible = uiState.isActionInProgress,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                ActionInProgressCard(message = uiState.actionProgressMessage ?: "Processando instrução...")
            }

            // 4. Mensagem de Erro de Telemetria (se houver)
            uiState.errorMessage?.let { error ->
                ErrorMessageBanner(message = error, onRetry = onRefreshClick)
            }

            // 5. Card de Status Geral do Servidor
            ServerStatusOverviewCard(
                status = uiState.serverStatus,
                isLoading = uiState.isLoading && uiState.serverStatus == null
            )

            // 6. Card de Telemetria do Processador (CPU Total, Processo BDS & Load Average)
            ServerCpuMetricsCard(status = uiState.serverStatus)

            // 7. Card de Telemetria de Memória RAM (Cgroups v2)
            ServerMemoryMetricsCard(status = uiState.serverStatus)

            // 7. Grid de Cards Rápidos: Jogadores Conectados & Canal FIFO
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Card de Jogadores Online com atalho para a aba Jogadores
                OnlinePlayersCard(
                    status = uiState.serverStatus,
                    onNavigateToPlayers = onNavigateToPlayers,
                    modifier = Modifier.weight(1f)
                )

                // Card de Integridade do Canal FIFO (/run/bedrock.console)
                FifoChannelCard(
                    status = uiState.serverStatus,
                    modifier = Modifier.weight(1f)
                )
            }

            // 8. Seção de Controles de Ciclo de Vida do Servidor e Hot-Backup Rápido
            LifecycleControlsSection(
                isServerRunning = uiState.isServerRunning,
                canExecute = uiState.canExecuteActions,
                onActionClick = onLifecycleActionClick,
                onTriggerHotBackup = onTriggerHotBackup
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Host de Snackbar ancorado na base
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )

        // Diálogo Modal de Confirmação para Ações Críticas (Stop e Restart)
        uiState.pendingConfirmationAction?.let { pendingAction ->
            LifecycleConfirmationDialog(
                action = pendingAction,
                onConfirm = onConfirmLifecycleAction,
                onDismiss = onDismissConfirmationDialog
            )
        }
    }
}

// ==============================================================================
// Sub-componentes Visuais da Dashboard
// ==============================================================================

/**
 * Barra superior da dashboard com controle de auto-refresh e botão de sincronização manual.
 */
@Composable
private fun DashboardHeaderBar(
    isLoading: Boolean,
    isAutoRefreshEnabled: Boolean,
    onRefreshClick: () -> Unit,
    onToggleAutoRefresh: () -> Unit
) {
    // Animação contínua de rotação 360° para o botão de refresh durante o carregamento
    val infiniteTransition = rememberInfiniteTransition(label = "RefreshRotationTransition")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RefreshRotationAngle"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = BedrockSurface),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BedrockBorderOutline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Atualização Automática",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimaryWhite
                )
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = isAutoRefreshEnabled,
                    onCheckedChange = { onToggleAutoRefresh() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = GrassGreenPrimary,
                        checkedTrackColor = GrassGreenDark,
                        uncheckedThumbColor = TextDisabledGrey,
                        uncheckedTrackColor = BedrockSurfaceElevated
                    )
                )
            }

            IconButton(
                onClick = onRefreshClick,
                enabled = !isLoading
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Atualizar telemetria",
                    tint = DiamondBlueSecondary,
                    modifier = Modifier
                        .size(26.dp)
                        .then(if (isLoading) Modifier.rotate(rotationAngle) else Modifier)
                )
            }
        }
    }
}

/**
 * Banner de exibição do estado de conexão da sessão SSH.
 */
@Composable
private fun SshConnectionStatusBanner(
    connectionState: ConnectionState,
    onNavigateToSettings: () -> Unit
) {
    when (connectionState) {
        is ConnectionState.Connected -> {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = GrassGreenDark.copy(alpha = 0.2f),
                border = androidx.compose.foundation.BorderStroke(1.dp, GrassGreenPrimary.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(GrassGreenPrimary)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "SSH Conectado (${connectionState.host}:${connectionState.port})",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimaryWhite,
                        fontWeight = FontWeight.Medium
                    )
                    connectionState.latencyMs?.let { latency ->
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "${latency}ms",
                            style = MaterialTheme.typography.labelSmall,
                            color = DiamondBlueLight
                        )
                    }
                }
            }
        }
        is ConnectionState.Connecting -> {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = GoldYellowDark.copy(alpha = 0.2f),
                border = androidx.compose.foundation.BorderStroke(1.dp, GoldYellowTertiary.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = GoldYellowTertiary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Autenticando sessão SSH com o servidor...",
                        style = MaterialTheme.typography.bodySmall,
                        color = GoldYellowTertiary
                    )
                }
            }
        }
        is ConnectionState.Disconnected -> {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = BedrockSurfaceElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, BedrockBorderOutline),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToSettings() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.WifiOff,
                        contentDescription = "Desconectado",
                        tint = TextDisabledGrey,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SSH Desconectado",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryWhite
                        )
                        Text(
                            text = "Toque para configurar credenciais de acesso",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondaryMuted
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Ir para configurações",
                        tint = DiamondBlueSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        is ConnectionState.Error -> {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = RedstoneRedDark.copy(alpha = 0.25f),
                border = androidx.compose.foundation.BorderStroke(1.dp, RedstoneRedError),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToSettings() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = "Erro de conexão",
                        tint = RedstoneRedError,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Falha de Conexão SSH",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = RedstoneRedError
                        )
                        Text(
                            text = connectionState.message,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimaryWhite,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Card informativo exibido enquanto uma ação de ciclo de vida está em execução.
 */
@Composable
private fun ActionInProgressCard(message: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = DiamondBlueDark.copy(alpha = 0.25f),
        border = androidx.compose.foundation.BorderStroke(1.dp, DiamondBlueSecondary),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.5.dp,
                color = DiamondBlueSecondary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimaryWhite
            )
        }
    }
}

/**
 * Banner de exibição de erro de telemetria.
 */
@Composable
private fun ErrorMessageBanner(message: String, onRetry: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = RedstoneRedDark.copy(alpha = 0.2f),
        border = androidx.compose.foundation.BorderStroke(1.dp, RedstoneRedError.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Aviso de erro",
                tint = RedstoneRedError,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = TextPrimaryWhite,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onRetry) {
                Text(text = "Tentar", color = DiamondBlueSecondary)
            }
        }
    }
}

/**
 * Card de Status Geral do Servidor com badge semântico de alto contraste.
 */
@Composable
private fun ServerStatusOverviewCard(
    status: ServerStatus?,
    isLoading: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BedrockSurface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BedrockBorderOutline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = "Status do Servidor",
                        tint = GrassGreenPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Status Operacional",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryWhite
                    )
                }

                // Badge Dinâmico de Saúde Operacional
                val health = status?.health ?: ServerHealth.OFFLINE
                val badgeColor = Color(health.colorHex)

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = badgeColor.copy(alpha = 0.2f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(badgeColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (status?.isActive == true) "ONLINE" else "OFFLINE",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = DiamondBlueSecondary)
                }
            } else {
                // Detalhes operacionais em grade simples
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "SUBESTADO",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondaryMuted
                        )
                        Text(
                            text = status?.substate?.uppercase() ?: "DESCONHECIDO",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (status?.isActive == true) GrassGreenPrimary else RedstoneRedError
                        )
                    }

                    Column {
                        Text(
                            text = "PID DO PROCESSO",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondaryMuted
                        )
                        Text(
                            text = status?.pid?.toString() ?: "Nenhum",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimaryWhite
                        )
                    }

                    Column {
                        Text(
                            text = "INTEGRIDADE",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondaryMuted
                        )
                        Text(
                            text = (status?.health ?: ServerHealth.OFFLINE).label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color((status?.health ?: ServerHealth.OFFLINE).colorHex)
                        )
                    }
                }

                status?.uptimeText?.let { uptime ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Iniciado em: $uptime",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondaryMuted
                    )
                }
            }
        }
    }
}

/**
 * Card de Telemetria do Processador (CPU) com barra de progresso dinâmica,
 * consumo dedicado do processo `bedrock_server`, Load Average do kernel Linux
 * (1m / 5m / 15m), contagem de vCPUs e identificação do modelo de hardware OCI.
 */
@Composable
private fun ServerCpuMetricsCard(status: ServerStatus?) {
    val cpuPercent = status?.cpuUsagePercent ?: 0f
    val bdsCpuPercent = status?.bdsCpuUsagePercent
    val loadAvg = status?.cpuLoadAverage ?: "-- / -- / --"
    val cores = status?.cpuCores
    val cpuModel = status?.cpuModel
    val fraction = (cpuPercent / 100f).coerceIn(0f, 1f)

    // Faixas de cor de carga de CPU: < 70% Verde, 70-89% Âmbar, >= 90% Vermelho
    val progressColor = when {
        cpuPercent >= 90f -> RedstoneRedError
        cpuPercent >= 70f -> GoldYellowTertiary
        else -> GrassGreenPrimary
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = BedrockSurface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BedrockBorderOutline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeveloperBoard,
                        contentDescription = "Processador CPU",
                        tint = DiamondBlueSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Processador (CPU)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryWhite
                        )
                        val subtitleParts = buildList {
                            if (!cpuModel.isNullOrBlank()) add(cpuModel)
                            if (cores != null) add("$cores vCPUs")
                        }
                        if (subtitleParts.isNotEmpty()) {
                            Text(
                                text = subtitleParts.joinToString(" • "),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondaryMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = if (status?.cpuUsagePercent != null) "%.1f%%".format(cpuPercent) else "--%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = progressColor
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Barra de Progresso de Utilização Global da CPU
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp)),
                color = progressColor,
                trackColor = BedrockSurfaceElevated
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (bdsCpuPercent != null) {
                        "Processo BDS: %.1f%%".format(bdsCpuPercent)
                    } else {
                        "Processo BDS: --"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimaryWhite,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Load (1/5/15m): $loadAvg",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondaryMuted
                )
            }
        }
    }
}

/**
 * Card de Memória RAM com barra de progresso colorida por faixas de consumo (Cgroups v2).
 */
@Composable
private fun ServerMemoryMetricsCard(status: ServerStatus?) {
    val usedMib = status?.memoryUsedMib ?: 0f
    val maxMib = status?.memoryMaxMib ?: 750f
    val alertMib = status?.memoryAlertMib ?: 600f
    val fraction = if (maxMib > 0f) (usedMib / maxMib).coerceIn(0f, 1f) else 0f
    val percent = (fraction * 100).toInt()

    // Faixas de cor: < 600 MiB Verde, 600-750 MiB Âmbar, > 750 MiB Vermelho
    val progressColor = when {
        usedMib > maxMib -> RedstoneRedError
        usedMib > alertMib -> GoldYellowTertiary
        else -> GrassGreenPrimary
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = BedrockSurface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BedrockBorderOutline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = "Memória RAM",
                        tint = DiamondBlueSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Memória RAM (Cgroups v2)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryWhite
                    )
                }

                Text(
                    text = "$percent%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = progressColor
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Barra de Progresso de Memória
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp)),
                color = progressColor,
                trackColor = BedrockSurfaceElevated
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (status?.memoryUsedMib != null) "Consumo: %.1f MiB".format(usedMib) else "Consumo: --",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimaryWhite,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Alerta: %.0f | Teto: %.0f MiB".format(alertMib, maxMib),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondaryMuted
                )
            }
        }
    }
}

/**
 * Card de Jogadores Conectados com atalho de navegação.
 */
@Composable
private fun OnlinePlayersCard(
    status: ServerStatus?,
    onNavigateToPlayers: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BedrockSurface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BedrockBorderOutline),
        modifier = modifier.clickable { onNavigateToPlayers() }
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.People,
                    contentDescription = "Jogadores",
                    tint = DiamondBlueSecondary,
                    modifier = Modifier.size(22.dp)
                )
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = "Acessar",
                    tint = TextSecondaryMuted,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "JOGADORES",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondaryMuted
            )

            Text(
                text = status?.playersRatioText ?: "0/10",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if ((status?.onlinePlayersCount ?: 0) > 0) GrassGreenPrimary else TextPrimaryWhite
            )
        }
    }
}

/**
 * Card de Integridade do Socket FIFO (/run/bedrock.console).
 */
@Composable
private fun FifoChannelCard(
    status: ServerStatus?,
    modifier: Modifier = Modifier
) {
    val isFifoOk = status?.isFifoAvailable == true
    val fifoColor = if (isFifoOk) GrassGreenPrimary else RedstoneRedError

    Card(
        colors = CardDefaults.cardColors(containerColor = BedrockSurface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BedrockBorderOutline),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Terminal,
                contentDescription = "Canal FIFO",
                tint = fifoColor,
                modifier = Modifier.size(22.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "SOCKET CONSOLE",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondaryMuted
            )

            Text(
                text = if (isFifoOk) "OPERACIONAL" else "BLOQUEADO",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = fifoColor
            )
        }
    }
}

/**
 * Seção com botões dedicados para controle de ciclo de vida do servidor (Start, Stop, Restart).
 */
@Composable
private fun LifecycleControlsSection(
    isServerRunning: Boolean,
    canExecute: Boolean,
    onActionClick: (LifecycleAction) -> Unit,
    onTriggerHotBackup: () -> Unit = {}
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BedrockSurface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BedrockBorderOutline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Text(
                text = "Gerenciamento do Ciclo de Vida",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimaryWhite
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Botão INICIAR
                Button(
                    onClick = { onActionClick(LifecycleAction.START) },
                    enabled = canExecute && !isServerRunning,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GrassGreenDark,
                        contentColor = TextPrimaryWhite,
                        disabledContainerColor = BedrockSurfaceElevated,
                        disabledContentColor = TextDisabledGrey
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Iniciar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // Botão REINICIAR
                Button(
                    onClick = { onActionClick(LifecycleAction.RESTART) },
                    enabled = canExecute && isServerRunning,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GoldYellowDark,
                        contentColor = TextPrimaryWhite,
                        disabledContainerColor = BedrockSurfaceElevated,
                        disabledContentColor = TextDisabledGrey
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Reiniciar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // Botão PARAR
                Button(
                    onClick = { onActionClick(LifecycleAction.STOP) },
                    enabled = canExecute && isServerRunning,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RedstoneRedDark,
                        contentColor = TextPrimaryWhite,
                        disabledContainerColor = BedrockSurfaceElevated,
                        disabledContentColor = TextDisabledGrey
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Parar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Botão Dedicado: Hot-Backup Consistente (save hold / save resume)
            Button(
                onClick = onTriggerHotBackup,
                enabled = canExecute,
                colors = ButtonDefaults.buttonColors(
                    containerColor = DiamondBlueDark,
                    contentColor = TextPrimaryWhite,
                    disabledContainerColor = BedrockSurfaceElevated,
                    disabledContentColor = TextDisabledGrey
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Executar Hot-Backup Consistente (mc backup)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Diálogo modal AlertDialog para confirmação de ações destrutivas ou de reinicialização.
 */
@Composable
private fun LifecycleConfirmationDialog(
    action: LifecycleAction,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val isStop = action == LifecycleAction.STOP
    val icon = if (isStop) Icons.Default.Stop else Icons.Default.RestartAlt
    val iconColor = if (isStop) RedstoneRedError else GoldYellowTertiary

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = action.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimaryWhite
            )
        },
        text = {
            Text(
                text = action.confirmationMessage
                    ?: "Confirma a execução de ${action.displayName}?",
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimaryWhite
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isStop) RedstoneRedDark else GoldYellowDark,
                    contentColor = TextPrimaryWhite
                )
            ) {
                Text(text = "Confirmar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(text = "Cancelar", color = TextSecondaryMuted)
            }
        },
        containerColor = BedrockSurface,
        shape = RoundedCornerShape(16.dp)
    )
}
