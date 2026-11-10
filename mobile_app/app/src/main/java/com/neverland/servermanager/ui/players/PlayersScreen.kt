package com.neverland.servermanager.ui.players

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VerifiedUser
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neverland.servermanager.domain.model.BackupFileEntry
import com.neverland.servermanager.domain.model.BackupResult
import com.neverland.servermanager.domain.model.GameModeOption
import com.neverland.servermanager.domain.model.OnlinePlayer
import com.neverland.servermanager.domain.model.PlayerConnectionEvent
import com.neverland.servermanager.domain.model.PlayerEventType
import com.neverland.servermanager.domain.model.WhitelistEntry
import com.neverland.servermanager.ui.theme.BedrockBorderOutline
import com.neverland.servermanager.ui.theme.BedrockDarkBackground
import com.neverland.servermanager.ui.theme.BedrockSurface
import com.neverland.servermanager.ui.theme.BedrockSurfaceElevated
import com.neverland.servermanager.ui.theme.ConsoleFontFamily
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
import kotlinx.coroutines.launch

/**
 * ==============================================================================
 * Never Land Server Manager - Tela de Jogadores e Hot-Backups (PlayersScreen.kt)
 * ==============================================================================
 *
 * Interface completa e interativa para gestão de jogadores e proteção de mundos:
 * 1. Aba Online: Lista jogadores conectados em tempo real com avatar estilizado,
 *    XUID, controles de Kick, Ban e Comandos Rápidos In-Game (Gamemode / OP).
 * 2. Aba Whitelist: Gerenciamento completo do `allowlist.json` (adicionar,
 *    remover, buscar e recarregar em memória via `allowlist reload`).
 * 3. Aba Histórico: Eventos de conexão, spawn e desconexão com XUID copiável e
 *    botão de 1 toque para adicionar um jogador diretamente à Whitelist.
 * 4. Aba Backups: Acionamento da rotina de Hot-Backup atômico (`mc backup` com
 *    protocolo `save hold` / `save resume`), cronômetro em tempo real e lista
 *    dos arquivos `.tar.gz` armazenados em `/home/opc/minecraft/backups`.
 * ==============================================================================
 */
@Composable
fun PlayersScreen(
    uiState: PlayersUiState,
    onSelectTab: (PlayersSubTab) -> Unit,
    onRefresh: () -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onOpenAddWhitelistDialog: (String) -> Unit,
    onDismissAddWhitelistDialog: () -> Unit,
    onNewWhitelistGamertagChanged: (String) -> Unit,
    onConfirmAddWhitelist: (String?) -> Unit,
    onRemoveFromWhitelist: (String) -> Unit,
    onReloadWhitelist: () -> Unit,
    onRequestKick: (String) -> Unit,
    onRequestBan: (String) -> Unit,
    onModerationReasonChanged: (String) -> Unit,
    onConfirmModeration: () -> Unit,
    onDismissModeration: () -> Unit,
    onOpenQuickActions: (String) -> Unit,
    onDismissQuickActions: () -> Unit,
    onChangeGameMode: (String, GameModeOption) -> Unit,
    onChangeOperator: (String, Boolean) -> Unit,
    onTriggerHotBackup: () -> Unit,
    onClearSnackbar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    val clipboard = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Cabeçalho e Botão de Sincronização
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Jogadores & Backups",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryWhite
                    )
                    Text(
                        text = "Controle de Allowlist, Moderação e Hot-Backup",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondaryMuted
                    )
                }

                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onRefresh()
                    },
                    enabled = !uiState.isLoading
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = DiamondBlueSecondary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Atualizar dados",
                            tint = DiamondBlueSecondary
                        )
                    }
                }
            }

            // 2. Seletor de 4 Abas (Online, Whitelist, Histórico, Backups)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PlayersSubTab.entries.forEach { tab ->
                    val isSelected = uiState.selectedTab == tab
                    val countBadge = when (tab) {
                        PlayersSubTab.ONLINE -> "${uiState.onlinePlayers.size}"
                        PlayersSubTab.WHITELIST -> "${uiState.whitelist.size}"
                        PlayersSubTab.HISTORY -> "${uiState.playerHistory.size}"
                        PlayersSubTab.BACKUPS -> "${uiState.savedBackups.size}"
                    }

                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelectTab(tab)
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) GrassGreenDark else BedrockSurfaceElevated,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) GrassGreenPrimary else BedrockBorderOutline
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = tab.title,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) TextPrimaryWhite else TextSecondaryMuted,
                                maxLines = 1
                            )
                            Text(
                                text = countBadge,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = if (isSelected) GrassGreenPrimary else DiamondBlueSecondary
                            )
                        }
                    }
                }
            }

            // 3. Banner de Ação Administrativa em Andamento
            AnimatedVisibility(visible = uiState.isActionInProgress) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = DiamondBlueDark.copy(alpha = 0.25f),
                    border = BorderStroke(1.dp, DiamondBlueSecondary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = DiamondBlueSecondary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = uiState.actionProgressText ?: "Processando...",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimaryWhite
                        )
                    }
                }
            }

            // 4. Conteúdo Dinâmico da Sub-Aba Selecionada
            Box(modifier = Modifier.weight(1f)) {
                when (uiState.selectedTab) {
                    PlayersSubTab.ONLINE -> OnlinePlayersTabContent(
                        onlinePlayers = uiState.onlinePlayers,
                        onKick = onRequestKick,
                        onBan = onRequestBan,
                        onQuickActions = onOpenQuickActions,
                        onOpenAddWhitelist = { onOpenAddWhitelistDialog("") }
                    )

                    PlayersSubTab.WHITELIST -> WhitelistTabContent(
                        whitelist = uiState.filteredWhitelist,
                        searchQuery = uiState.searchQuery,
                        onSearchQueryChanged = onSearchQueryChanged,
                        onAddClick = { onOpenAddWhitelistDialog("") },
                        onReloadClick = onReloadWhitelist,
                        onRemoveClick = onRemoveFromWhitelist,
                        onCopyXuid = { xuid ->
                            clipboard.setText(AnnotatedString(xuid))
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("XUID $xuid copiado!")
                            }
                        }
                    )

                    PlayersSubTab.HISTORY -> PlayerHistoryTabContent(
                        events = uiState.filteredHistory,
                        searchQuery = uiState.searchQuery,
                        onSearchQueryChanged = onSearchQueryChanged,
                        onAddWhitelist = { tag -> onConfirmAddWhitelist(tag) },
                        onBanPlayer = onRequestBan,
                        onCopyXuid = { xuid ->
                            clipboard.setText(AnnotatedString(xuid))
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("XUID $xuid copiado!")
                            }
                        }
                    )

                    PlayersSubTab.BACKUPS -> BackupsTabContent(
                        isBackupRunning = uiState.isBackupRunning,
                        elapsedSeconds = uiState.backupElapsedSeconds,
                        lastResult = uiState.lastBackupResult,
                        backups = uiState.savedBackups,
                        onTriggerBackup = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onTriggerHotBackup()
                        }
                    )
                }
            }
        }

        // Snackbar Host
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )

        // Modal de Adicionar Jogador à Whitelist
        if (uiState.showAddWhitelistDialog) {
            AddWhitelistDialog(
                gamertag = uiState.newWhitelistGamertag,
                onGamertagChanged = onNewWhitelistGamertagChanged,
                onConfirm = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onConfirmAddWhitelist(null)
                },
                onDismiss = onDismissAddWhitelistDialog
            )
        }

        // Modal de Confirmação de Kick / Ban
        uiState.pendingModeration?.let { moderation ->
            ModerationConfirmationDialog(
                moderation = moderation,
                onReasonChanged = onModerationReasonChanged,
                onConfirm = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onConfirmModeration()
                },
                onDismiss = onDismissModeration
            )
        }

        // Modal de Comandos Rápidos In-Game para Jogador (Gamemode & OP)
        uiState.selectedPlayerForQuickActions?.let { playerTag ->
            PlayerQuickActionsDialog(
                gamertag = playerTag,
                onSelectGameMode = { mode -> onChangeGameMode(playerTag, mode) },
                onGrantOp = { onChangeOperator(playerTag, true) },
                onRevokeOp = { onChangeOperator(playerTag, false) },
                onDismiss = onDismissQuickActions
            )
        }
    }
}

// ==============================================================================
// Sub-telas de Jogadores Online, Whitelist, Histórico e Backups
// ==============================================================================

@Composable
private fun OnlinePlayersTabContent(
    onlinePlayers: List<OnlinePlayer>,
    onKick: (String) -> Unit,
    onBan: (String) -> Unit,
    onQuickActions: (String) -> Unit,
    onOpenAddWhitelist: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (onlinePlayers.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = BedrockSurface),
                border = BorderStroke(1.dp, BedrockBorderOutline)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.People,
                        contentDescription = null,
                        tint = DiamondBlueSecondary.copy(alpha = 0.6f),
                        modifier = Modifier.size(46.dp)
                    )
                    Text(
                        text = "Nenhum jogador online no momento",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryWhite
                    )
                    Text(
                        text = "O servidor Never Land aguarda conexões autorizadas na porta UDP/TCP 19132. Você pode gerenciar a Whitelist ou consultar o Histórico de XUIDs nas abas acima.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondaryMuted,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = onOpenAddWhitelist,
                        colors = ButtonDefaults.buttonColors(containerColor = GrassGreenDark),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Autorizar Novo Jogador na Whitelist", fontSize = 12.sp)
                    }
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(onlinePlayers, key = { it.gamertag }) { player ->
                    OnlinePlayerCard(
                        player = player,
                        onKick = { onKick(player.gamertag) },
                        onBan = { onBan(player.gamertag) },
                        onQuickActions = { onQuickActions(player.gamertag) }
                    )
                }
            }
        }
    }
}

@Composable
private fun OnlinePlayerCard(
    player: OnlinePlayer,
    onKick: () -> Unit,
    onBan: () -> Unit,
    onQuickActions: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = BedrockSurface),
        border = BorderStroke(1.dp, GrassGreenPrimary.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Avatar Voxel com iniciais
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(GrassGreenDark),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = player.avatarInitials,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimaryWhite
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = player.gamertag,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimaryWhite
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(GrassGreenPrimary)
                            )
                        }
                        Text(
                            text = player.xuid?.let { "XUID: $it" } ?: "Conectado via RakNet/NetherNet",
                            fontFamily = ConsoleFontFamily,
                            fontSize = 11.sp,
                            color = DiamondBlueSecondary
                        )
                        player.connectedSince?.let { since ->
                            Text(
                                text = "Última entrada: $since",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondaryMuted
                            )
                        }
                    }
                }
            }

            // Botões de Controle do Jogador
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onQuickActions,
                    colors = ButtonDefaults.buttonColors(containerColor = DiamondBlueDark),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.SportsEsports, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Modo / OP", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onKick,
                    colors = ButtonDefaults.buttonColors(containerColor = GoldYellowDark),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Expulsar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onBan,
                    colors = ButtonDefaults.buttonColors(containerColor = RedstoneRedDark),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Banir", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun WhitelistTabContent(
    whitelist: List<WhitelistEntry>,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onAddClick: () -> Unit,
    onReloadClick: () -> Unit,
    onRemoveClick: (String) -> Unit,
    onCopyXuid: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Barra de Ações da Allowlist
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onAddClick,
                colors = ButtonDefaults.buttonColors(containerColor = GrassGreenDark),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Adicionar Gamertag", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onReloadClick,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, DiamondBlueSecondary)
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    tint = DiamondBlueSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Reload", color = DiamondBlueSecondary, fontSize = 12.sp)
            }
        }

        // Busca rápida na Allowlist
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChanged,
            placeholder = {
                Text("Buscar na Whitelist por Gamertag ou XUID...", fontSize = 12.sp, color = TextDisabledGrey)
            },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = DiamondBlueSecondary, modifier = Modifier.size(18.dp))
            },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = BedrockSurface,
                unfocusedContainerColor = BedrockSurface,
                focusedBorderColor = DiamondBlueSecondary,
                unfocusedBorderColor = BedrockBorderOutline
            ),
            textStyle = MaterialTheme.typography.bodySmall.copy(color = TextPrimaryWhite),
            modifier = Modifier.fillMaxWidth()
        )

        if (whitelist.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = BedrockSurface),
                border = BorderStroke(1.dp, BedrockBorderOutline)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = TextDisabledGrey,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Nenhum registro encontrado na Allowlist",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimaryWhite
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(whitelist, key = { it.name }) { entry ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = BedrockSurface),
                        border = BorderStroke(1.dp, BedrockBorderOutline)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(BedrockSurfaceElevated),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = entry.avatarInitials,
                                        fontWeight = FontWeight.Bold,
                                        color = DiamondBlueSecondary,
                                        fontSize = 13.sp
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column {
                                    Text(
                                        text = entry.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimaryWhite
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = entry.xuid?.let { "XUID: $it" } ?: "XUID pendente (1º login)",
                                            fontFamily = ConsoleFontFamily,
                                            fontSize = 10.sp,
                                            color = TextSecondaryMuted,
                                            modifier = Modifier.clickable(enabled = entry.xuid != null) {
                                                entry.xuid?.let(onCopyXuid)
                                            }
                                        )
                                        if (entry.xuid != null) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "Copiar XUID",
                                                tint = DiamondBlueSecondary,
                                                modifier = Modifier
                                                    .size(11.dp)
                                                    .clickable { onCopyXuid(entry.xuid) }
                                            )
                                        }
                                    }
                                }
                            }

                            IconButton(onClick = { onRemoveClick(entry.name) }) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Remover da Whitelist",
                                    tint = RedstoneRedError
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerHistoryTabContent(
    events: List<PlayerConnectionEvent>,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onAddWhitelist: (String) -> Unit,
    onBanPlayer: (String) -> Unit,
    onCopyXuid: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChanged,
            placeholder = {
                Text("Filtrar histórico por Gamertag ou XUID...", fontSize = 12.sp, color = TextDisabledGrey)
            },
            leadingIcon = {
                Icon(Icons.Default.History, contentDescription = null, tint = DiamondBlueSecondary, modifier = Modifier.size(18.dp))
            },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = BedrockSurface,
                unfocusedContainerColor = BedrockSurface,
                focusedBorderColor = DiamondBlueSecondary,
                unfocusedBorderColor = BedrockBorderOutline
            ),
            textStyle = MaterialTheme.typography.bodySmall.copy(color = TextPrimaryWhite),
            modifier = Modifier.fillMaxWidth()
        )

        if (events.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = BedrockSurface),
                border = BorderStroke(1.dp, BedrockBorderOutline)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Nenhum evento de conexão encontrado no journald recente.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondaryMuted,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(events) { ev ->
                    val badgeColor = when (ev.eventType) {
                        PlayerEventType.CONNECTED -> GrassGreenPrimary
                        PlayerEventType.SPAWNED -> DiamondBlueSecondary
                        PlayerEventType.DISCONNECTED -> GoldYellowTertiary
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = BedrockSurface),
                        border = BorderStroke(1.dp, BedrockBorderOutline)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = badgeColor.copy(alpha = 0.18f),
                                        border = BorderStroke(1.dp, badgeColor)
                                    ) {
                                        Text(
                                            text = ev.eventType.label,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = badgeColor,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = ev.gamertag,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimaryWhite
                                    )
                                }

                                Text(
                                    text = ev.timestamp,
                                    fontFamily = ConsoleFontFamily,
                                    fontSize = 10.sp,
                                    color = TextSecondaryMuted
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable(enabled = ev.xuid != null) {
                                        ev.xuid?.let(onCopyXuid)
                                    }
                                ) {
                                    Text(
                                        text = ev.xuid?.let { "XUID: $it" } ?: "XUID não informado",
                                        fontFamily = ConsoleFontFamily,
                                        fontSize = 11.sp,
                                        color = DiamondBlueLight
                                    )
                                    if (ev.xuid != null) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copiar XUID",
                                            tint = DiamondBlueSecondary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Surface(
                                        onClick = { onAddWhitelist(ev.gamertag) },
                                        shape = RoundedCornerShape(6.dp),
                                        color = GrassGreenDark.copy(alpha = 0.4f),
                                        border = BorderStroke(1.dp, GrassGreenPrimary)
                                    ) {
                                        Text(
                                            text = "+ Whitelist",
                                            fontSize = 10.sp,
                                            color = GrassGreenPrimary,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }

                                    Surface(
                                        onClick = { onBanPlayer(ev.gamertag) },
                                        shape = RoundedCornerShape(6.dp),
                                        color = RedstoneRedDark.copy(alpha = 0.4f),
                                        border = BorderStroke(1.dp, RedstoneRedError)
                                    ) {
                                        Text(
                                            text = "Banir",
                                            fontSize = 10.sp,
                                            color = RedstoneRedError,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupsTabContent(
    isBackupRunning: Boolean,
    elapsedSeconds: Int,
    lastResult: BackupResult?,
    backups: List<BackupFileEntry>,
    onTriggerBackup: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Card Principal de Disparo do Hot-Backup Atômico
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = BedrockSurface),
            border = BorderStroke(1.dp, DiamondBlueSecondary)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = null,
                        tint = DiamondBlueSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Hot-Backup Atômico (LevelDB)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryWhite
                        )
                        Text(
                            text = "Protocolo Mojang: save hold • save query • tar.gz • save resume",
                            style = MaterialTheme.typography.labelSmall,
                            color = DiamondBlueLight
                        )
                    }
                }

                Text(
                    text = "Cria um snapshot consistente do mundo Never Land sem desconectar os jogadores online e mantendo retenção em /home/opc/minecraft/backups.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondaryMuted
                )

                if (isBackupRunning) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(BedrockSurfaceElevated)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Congelando chunks e compactando mundo...",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = GoldYellowTertiary
                            )
                            Text(
                                text = "${elapsedSeconds}s",
                                fontFamily = ConsoleFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = DiamondBlueSecondary
                            )
                        }
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = DiamondBlueSecondary,
                            trackColor = BedrockDarkBackground
                        )
                    }
                } else {
                    Button(
                        onClick = onTriggerBackup,
                        colors = ButtonDefaults.buttonColors(containerColor = DiamondBlueDark),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Executar Hot-Backup Agora (mc backup)",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Exibe o resultado do último backup disparado nesta sessão
                lastResult?.let { res ->
                    val resColor = if (res.success) GrassGreenPrimary else RedstoneRedError
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = resColor.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, resColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = resColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = if (res.success) "Backup Concluído (${"%.1f".format(res.elapsedSeconds)}s)" else "Falha no Backup",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = resColor
                                )
                                Text(
                                    text = "Arquivo: ${res.filename}",
                                    fontFamily = ConsoleFontFamily,
                                    fontSize = 11.sp,
                                    color = TextPrimaryWhite
                                )
                            }
                        }
                    }
                }
            }
        }

        // Lista de Arquivos Salvos no Disco da VM
        Text(
            text = "Arquivos Salvos em /home/opc/minecraft/backups (${backups.size})",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = TextPrimaryWhite
        )

        if (backups.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = BedrockSurface),
                border = BorderStroke(1.dp, BedrockBorderOutline)
            ) {
                Text(
                    text = "Nenhum arquivo .tar.gz listado ou sessão SSH desconectada.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondaryMuted,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(backups, key = { it.filename }) { file ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = BedrockSurface),
                        border = BorderStroke(1.dp, BedrockBorderOutline)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = file.filename,
                                    fontFamily = ConsoleFontFamily,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimaryWhite,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Criado em: ${file.dateText}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondaryMuted
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = BedrockSurfaceElevated,
                                border = BorderStroke(1.dp, GrassGreenPrimary.copy(alpha = 0.6f))
                            ) {
                                Text(
                                    text = file.sizeText,
                                    fontFamily = ConsoleFontFamily,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GrassGreenPrimary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==============================================================================
// Diálogos Modais (Adicionar Whitelist, Kick/Ban e Ações Rápidas In-Game)
// ==============================================================================

@Composable
private fun AddWhitelistDialog(
    gamertag: String,
    onGamertagChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.PersonAdd,
                contentDescription = null,
                tint = GrassGreenPrimary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Adicionar à Whitelist",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimaryWhite
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Informe a Gamertag exata da conta Microsoft/Xbox Live. O comando 'allowlist add' e 'allowlist reload' serão executados imediatamente:",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondaryMuted
                )
                OutlinedTextField(
                    value = gamertag,
                    onValueChange = onGamertagChanged,
                    placeholder = { Text("Ex.: Bruno0103V", fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GrassGreenPrimary,
                        unfocusedBorderColor = BedrockBorderOutline
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = gamertag.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = GrassGreenDark)
            ) {
                Text("Autorizar Jogador", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancelar", color = TextSecondaryMuted)
            }
        },
        containerColor = BedrockSurface,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun ModerationConfirmationDialog(
    moderation: PendingModerationAction,
    onReasonChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val accentColor = if (moderation.isBan) RedstoneRedError else GoldYellowTertiary

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (moderation.isBan) Icons.Default.Block else Icons.Default.ExitToApp,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(34.dp)
            )
        },
        title = {
            Text(
                text = if (moderation.isBan) "Banir ${moderation.gamertag}" else "Expulsar ${moderation.gamertag}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimaryWhite
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (moderation.isBan) {
                        "Esta ação desconectará '${moderation.gamertag}' imediatamente e removerá sua Gamertag da Allowlist do servidor Never Land."
                    } else {
                        "Esta ação desconectará '${moderation.gamertag}' da sessão atual com o motivo abaixo:"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondaryMuted
                )

                OutlinedTextField(
                    value = moderation.reason,
                    onValueChange = onReasonChanged,
                    label = { Text("Motivo exibido na tela do jogador") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = BedrockBorderOutline
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (moderation.isBan) RedstoneRedDark else GoldYellowDark
                )
            ) {
                Text(
                    text = if (moderation.isBan) "Confirmar Banimento" else "Confirmar Expulsão",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancelar", color = TextSecondaryMuted)
            }
        },
        containerColor = BedrockSurface,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun PlayerQuickActionsDialog(
    gamertag: String,
    onSelectGameMode: (GameModeOption) -> Unit,
    onGrantOp: () -> Unit,
    onRevokeOp: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.SportsEsports,
                contentDescription = null,
                tint = DiamondBlueSecondary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Comandos In-Game: $gamertag",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimaryWhite
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Alterar Modo de Jogo (gamemode):",
                    style = MaterialTheme.typography.labelMedium,
                    color = DiamondBlueSecondary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    GameModeOption.entries.take(2).forEach { mode ->
                        Button(
                            onClick = { onSelectGameMode(mode) },
                            colors = ButtonDefaults.buttonColors(containerColor = BedrockSurfaceElevated),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(mode.displayName, fontSize = 11.sp, color = TextPrimaryWhite)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    GameModeOption.entries.drop(2).forEach { mode ->
                        Button(
                            onClick = { onSelectGameMode(mode) },
                            colors = ButtonDefaults.buttonColors(containerColor = BedrockSurfaceElevated),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(mode.displayName, fontSize = 11.sp, color = TextPrimaryWhite)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Privilégios de Operador (OP):",
                    style = MaterialTheme.typography.labelMedium,
                    color = GoldYellowTertiary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onGrantOp,
                        colors = ButtonDefaults.buttonColors(containerColor = GrassGreenDark),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Dar OP", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onRevokeOp,
                        colors = ButtonDefaults.buttonColors(containerColor = RedstoneRedDark),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Tirar OP (Deop)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Fechar", color = TextSecondaryMuted)
            }
        },
        containerColor = BedrockSurface,
        shape = RoundedCornerShape(16.dp)
    )
}

