package com.neverland.servermanager.ui.console

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neverland.servermanager.domain.model.ConsoleEntry
import com.neverland.servermanager.domain.model.ConsoleFilter
import com.neverland.servermanager.domain.model.ConsoleLineType
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
import com.neverland.servermanager.ui.theme.RedstoneRedError
import com.neverland.servermanager.ui.theme.TextDisabledGrey
import com.neverland.servermanager.ui.theme.TextPrimaryWhite
import com.neverland.servermanager.ui.theme.TextSecondaryMuted
import kotlinx.coroutines.launch

/**
 * ==============================================================================
 * Never Land Server Manager - Tela do Console Interativo (ConsoleScreen.kt)
 * ==============================================================================
 *
 * Terminal bidirecional completo para operação em tempo real do Bedrock Dedicated
 * Server via socket FIFO (`/run/bedrock.console`) e leitura do `journald`.
 *
 * Recursos Interativos:
 * 1. Coloração Sintática (Syntax Highlighting) por tipo de evento (Chat, Player,
 *    Comando Enviado, Resposta, Alerta, Erro e Info).
 * 2. Filtros Rápidos por Categoria e Barra de Busca Instantânea nos logs.
 * 3. Modo "Ao Vivo" (Live Tail) com atualização automática e controle de Auto-Scroll.
 * 4. Chips de Comandos Rápidos (Shortcuts) e Histórico de Comandos Recentes.
 * 5. Modal dedicado para Anúncio Global no Chat (`mc say`) com presets rápidos.
 * 6. Cópia instantânea de qualquer linha de log para a área de transferência ao tocar.
 * ==============================================================================
 */
@Composable
fun ConsoleScreen(
    uiState: ConsoleUiState,
    onCommandInputChanged: (String) -> Unit,
    onSendCommand: () -> Unit,
    onSendQuickCommand: (String) -> Unit,
    onRefreshLogs: () -> Unit,
    onToggleAutoScroll: () -> Unit,
    onToggleLiveTail: () -> Unit,
    onSelectFilter: (ConsoleFilter) -> Unit,
    onToggleSearch: () -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onClearConsole: () -> Unit,
    onOpenBroadcastDialog: () -> Unit,
    onDismissBroadcastDialog: () -> Unit,
    onBroadcastInputChanged: (String) -> Unit,
    onConfirmBroadcast: () -> Unit,
    onClearSnackbar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val clipboardManager = LocalClipboardManager.current

    val displayedEntries = uiState.filteredEntries

    // Rolagem automática para o final sempre que novas linhas chegarem e o Auto-scroll estiver ativo
    LaunchedEffect(displayedEntries.size, uiState.isAutoScrollEnabled) {
        if (uiState.isAutoScrollEnabled && displayedEntries.isNotEmpty()) {
            listState.animateScrollToItem(displayedEntries.size - 1)
        }
    }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            onClearSnackbar()
        }
    }

    // Presets de comandos rápidos do Bedrock e do utilitário mc.py
    val quickShortcuts = remember {
        listOf(
            "list" to "Jogadores (/list)",
            "allowlist list" to "Ver Allowlist",
            "save query" to "Status LevelDB",
            "time query day" to "Ver Dia",
            "time set day" to "Dia (Set Day)",
            "weather clear" to "Limpar Clima",
            "mc status" to "mc status",
            "mc recursos" to "mc recursos",
            "mc rede" to "mc rede",
            "mc config" to "mc config",
            "mc boot" to "mc boot"
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BedrockDarkBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. Barra de Controle do Terminal (Live Tail, Auto-Scroll, Broadcast, Busca, Limpar)
            ConsoleControlBar(
                isLiveTailEnabled = uiState.isLiveTailEnabled,
                isAutoScrollEnabled = uiState.isAutoScrollEnabled,
                isLoading = uiState.isLoadingLogs,
                onToggleLiveTail = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggleLiveTail()
                },
                onToggleAutoScroll = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggleAutoScroll()
                },
                onOpenBroadcast = onOpenBroadcastDialog,
                onToggleSearch = onToggleSearch,
                onRefresh = onRefreshLogs,
                onClear = onClearConsole
            )

            // 2. Barra de Pesquisa Expansível
            AnimatedVisibility(visible = uiState.isSearchVisible) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = onSearchQueryChanged,
                    placeholder = {
                        Text(
                            text = "Filtrar linhas do log (ex.: Steve, xuid, WARN, Pack)...",
                            fontSize = 12.sp,
                            color = TextDisabledGrey
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = DiamondBlueSecondary,
                            modifier = Modifier.size(18.dp)
                        )
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
            }

            // 3. Filtros por Categoria Semântica
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ConsoleFilter.entries.forEach { filter ->
                    val isSelected = uiState.selectedFilter == filter
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectFilter(filter) },
                        label = {
                            Text(
                                text = filter.label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = GrassGreenDark.copy(alpha = 0.55f),
                            selectedLabelColor = GrassGreenPrimary,
                            containerColor = BedrockSurface,
                            labelColor = TextSecondaryMuted
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = BedrockBorderOutline,
                            selectedBorderColor = GrassGreenPrimary
                        )
                    )
                }
            }

            // 4. Janela Principal do Terminal Escuro Monoespaçado
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(ConsoleBackgroundDark)
                    .border(1.dp, BedrockBorderOutline, RoundedCornerShape(14.dp))
            ) {
                if (displayedEntries.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = DiamondBlueSecondary.copy(alpha = 0.5f),
                            modifier = Modifier.size(42.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (uiState.isConnected) {
                                "Nenhuma linha encontrada para o filtro atual."
                            } else {
                                "Console pronto. Conecte via SSH ou envie um comando abaixo."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondaryMuted
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = displayedEntries,
                            key = { it.id }
                        ) { entry ->
                            ConsoleLogLineItem(
                                entry = entry,
                                onCopyClick = {
                                    clipboardManager.setText(AnnotatedString(entry.content))
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Linha copiada para a área de transferência")
                                    }
                                }
                            )
                        }
                    }
                }

                // Botão flutuante para descer até a linha mais recente caso o usuário tenha rolado para cima
                if (displayedEntries.size > 8) {
                    Surface(
                        onClick = {
                            coroutineScope.launch {
                                listState.animateScrollToItem(displayedEntries.size - 1)
                            }
                        },
                        shape = CircleShape,
                        color = BedrockSurfaceElevated.copy(alpha = 0.9f),
                        border = BorderStroke(1.dp, DiamondBlueSecondary),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                            .size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Rolar para o fim",
                                tint = DiamondBlueSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // 5. Barra Horizontal de Atalhos Rápidos de Comandos (Shortcuts)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                quickShortcuts.forEach { (cmd, label) ->
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSendQuickCommand(cmd)
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = BedrockSurfaceElevated,
                        border = BorderStroke(1.dp, BedrockBorderOutline)
                    ) {
                        Text(
                            text = label,
                            fontFamily = ConsoleFontFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = DiamondBlueSecondary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // 6. Barra de Histórico Recente de Comandos Digitados
            if (uiState.commandHistory.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "Histórico",
                        tint = TextSecondaryMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    uiState.commandHistory.take(8).forEach { histCmd ->
                        Surface(
                            onClick = { onCommandInputChanged(histCmd) },
                            shape = RoundedCornerShape(6.dp),
                            color = BedrockSurface,
                            border = BorderStroke(1.dp, BedrockBorderOutline.copy(alpha = 0.6f))
                        ) {
                            Text(
                                text = histCmd,
                                fontFamily = ConsoleFontFamily,
                                fontSize = 10.sp,
                                color = TextSecondaryMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // 7. Campo de Entrada de Comando Arbitrário (/run/bedrock.console via mc cmd)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = uiState.commandInput,
                    onValueChange = onCommandInputChanged,
                    placeholder = {
                        Text(
                            text = "Comando BDS ou mc (ex.: say Olá, gamerule, list)...",
                            color = TextDisabledGrey,
                            fontSize = 12.sp
                        )
                    },
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = ConsoleFontFamily,
                        color = TextPrimaryWhite,
                        fontSize = 13.sp
                    ),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = BedrockSurface,
                        unfocusedContainerColor = BedrockSurface,
                        focusedBorderColor = GrassGreenPrimary,
                        unfocusedBorderColor = BedrockBorderOutline
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (uiState.commandInput.isNotBlank() && !uiState.isSendingCommand) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSendCommand()
                            }
                        }
                    )
                )

                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSendCommand()
                    },
                    enabled = uiState.commandInput.isNotBlank() && !uiState.isSendingCommand,
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (uiState.commandInput.isNotBlank() && !uiState.isSendingCommand) {
                                GrassGreenDark
                            } else {
                                BedrockSurfaceElevated
                            }
                        )
                ) {
                    if (uiState.isSendingCommand) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = GrassGreenPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Enviar comando",
                            tint = if (uiState.commandInput.isNotBlank()) TextPrimaryWhite else TextDisabledGrey
                        )
                    }
                }
            }
        }

        // Snackbar Host
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 72.dp, start = 16.dp, end = 16.dp)
        )

        // Diálogo Modal para Broadcast Público no Chat (`mc say`)
        if (uiState.showBroadcastDialog) {
            BroadcastMessageDialog(
                messageInput = uiState.broadcastInput,
                onMessageChanged = onBroadcastInputChanged,
                onConfirm = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onConfirmBroadcast()
                },
                onDismiss = onDismissBroadcastDialog
            )
        }
    }
}

/**
 * Barra superior compacta de controles do Console Interativo.
 */
@Composable
private fun ConsoleControlBar(
    isLiveTailEnabled: Boolean,
    isAutoScrollEnabled: Boolean,
    isLoading: Boolean,
    onToggleLiveTail: () -> Unit,
    onToggleAutoScroll: () -> Unit,
    onOpenBroadcast: () -> Unit,
    onToggleSearch: () -> Unit,
    onRefresh: () -> Unit,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pílula Live Tail
            Surface(
                onClick = onToggleLiveTail,
                shape = RoundedCornerShape(8.dp),
                color = if (isLiveTailEnabled) GrassGreenDark.copy(alpha = 0.35f) else BedrockSurfaceElevated,
                border = BorderStroke(1.dp, if (isLiveTailEnabled) GrassGreenPrimary else BedrockBorderOutline)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (isLiveTailEnabled) GrassGreenPrimary else TextDisabledGrey)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (isLiveTailEnabled) "Ao Vivo" else "Pausado",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isLiveTailEnabled) GrassGreenPrimary else TextSecondaryMuted
                    )
                }
            }

            // Pílula Auto-Scroll
            Surface(
                onClick = onToggleAutoScroll,
                shape = RoundedCornerShape(8.dp),
                color = if (isAutoScrollEnabled) DiamondBlueDark.copy(alpha = 0.3f) else BedrockSurfaceElevated,
                border = BorderStroke(1.dp, if (isAutoScrollEnabled) DiamondBlueSecondary else BedrockBorderOutline)
            ) {
                Text(
                    text = "Auto-scroll",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isAutoScrollEnabled) DiamondBlueLight else TextSecondaryMuted,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                )
            }

            // Botão de Broadcast no Chat (`say`)
            Surface(
                onClick = onOpenBroadcast,
                shape = RoundedCornerShape(8.dp),
                color = GoldYellowDark.copy(alpha = 0.3f),
                border = BorderStroke(1.dp, GoldYellowTertiary)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Campaign,
                        contentDescription = "Anunciar no Chat",
                        tint = GoldYellowTertiary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Aviso Chat",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = GoldYellowTertiary
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onToggleSearch, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Buscar no log",
                    tint = TextPrimaryWhite,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = onRefresh, enabled = !isLoading, modifier = Modifier.size(32.dp)) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(15.dp),
                        strokeWidth = 2.dp,
                        color = DiamondBlueSecondary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Recarregar logs",
                        tint = DiamondBlueSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.ClearAll,
                    contentDescription = "Limpar console",
                    tint = TextSecondaryMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Renderiza uma linha individual do log/console com destaque sintático de cores
 * e suporte a cópia rápida ao tocar.
 */
@Composable
private fun ConsoleLogLineItem(
    entry: ConsoleEntry,
    onCopyClick: () -> Unit
) {
    val (badgeColor, textColor) = when (entry.type) {
        ConsoleLineType.COMMAND_SENT -> GrassGreenPrimary to GrassGreenPrimary
        ConsoleLineType.COMMAND_OUTPUT -> DiamondBlueSecondary to DiamondBlueLight
        ConsoleLineType.CHAT -> GoldYellowTertiary to GoldYellowTertiary
        ConsoleLineType.PLAYER_EVENT -> DiamondBlueSecondary to TextPrimaryWhite
        ConsoleLineType.WARN -> GoldYellowTertiary to GoldYellowTertiary
        ConsoleLineType.ERROR -> RedstoneRedError to RedstoneRedError
        ConsoleLineType.SYSTEM -> TextSecondaryMuted to TextSecondaryMuted
        ConsoleLineType.INFO -> DiamondBlueSecondary.copy(alpha = 0.7f) to TextPrimaryWhite.copy(alpha = 0.92f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable { onCopyClick() }
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Horário compacto
        Text(
            text = entry.timestamp,
            fontFamily = ConsoleFontFamily,
            fontSize = 10.sp,
            color = TextDisabledGrey,
            modifier = Modifier.width(54.dp)
        )

        // Badge semântico da linha
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = badgeColor.copy(alpha = 0.16f),
            modifier = Modifier.padding(end = 6.dp, top = 1.dp)
        ) {
            Text(
                text = entry.type.badgeLabel,
                fontFamily = ConsoleFontFamily,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = badgeColor,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }

        // Conteúdo textual da linha
        Text(
            text = entry.content,
            fontFamily = ConsoleFontFamily,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = textColor,
            modifier = Modifier.weight(1f)
        )

        Icon(
            imageVector = Icons.Default.ContentCopy,
            contentDescription = "Copiar linha",
            tint = TextDisabledGrey.copy(alpha = 0.4f),
            modifier = Modifier
                .padding(start = 4.dp, top = 2.dp)
                .size(11.dp)
        )
    }
}

/**
 * Diálogo modal para envio de avisos globais no chat (`mc say`) com botões de mensagens prontas.
 */
@Composable
private fun BroadcastMessageDialog(
    messageInput: String,
    onMessageChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val presets = listOf(
        "§e[AVISO] Manutenção programada em 5 minutos!",
        "§a[Never Land] Backup automático em execução.",
        "§b[Admin] Bem-vindos ao servidor Never Land!"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Campaign,
                contentDescription = null,
                tint = GoldYellowTertiary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Transmitir Aviso no Chat (say)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimaryWhite
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "A mensagem será exibida instantaneamente para todos os jogadores conectados no mundo Never Land:",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondaryMuted
                )

                OutlinedTextField(
                    value = messageInput,
                    onValueChange = onMessageChanged,
                    placeholder = {
                        Text("Ex.: Reinício rápido em 2 minutos...", fontSize = 12.sp)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldYellowTertiary,
                        unfocusedBorderColor = BedrockBorderOutline
                    )
                )

                Text(
                    text = "Mensagens Rápidas (toque para preencher):",
                    style = MaterialTheme.typography.labelSmall,
                    color = DiamondBlueSecondary
                )

                presets.forEach { preset ->
                    Surface(
                        onClick = { onMessageChanged(preset) },
                        shape = RoundedCornerShape(8.dp),
                        color = BedrockSurfaceElevated,
                        border = BorderStroke(1.dp, BedrockBorderOutline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = preset,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimaryWhite,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = messageInput.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GrassGreenDark,
                    contentColor = TextPrimaryWhite
                )
            ) {
                Text("Transmitir Agora", fontWeight = FontWeight.Bold)
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

