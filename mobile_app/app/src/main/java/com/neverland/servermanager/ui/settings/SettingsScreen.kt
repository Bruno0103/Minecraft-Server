package com.neverland.servermanager.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsApplications
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neverland.servermanager.domain.model.AuthType
import com.neverland.servermanager.domain.model.ConnectionState
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
import com.neverland.servermanager.ui.theme.RedstoneRedDark
import com.neverland.servermanager.ui.theme.RedstoneRedError
import com.neverland.servermanager.ui.theme.TextDisabledGrey
import com.neverland.servermanager.ui.theme.TextPrimaryWhite
import com.neverland.servermanager.ui.theme.TextSecondaryMuted
import kotlinx.coroutines.launch

/**
 * ==============================================================================
 * Never Land Server Manager - Tela de Ajustes e Diagnóstico (SettingsScreen.kt)
 * ==============================================================================
 *
 * Central de configuração de conectividade SSH, cofre criptografado no Android
 * KeyStore e diagnósticos de baixo nível da máquina virtual Oracle Linux 9.7.
 *
 * Funcionalidades:
 * 1. Seleção rápida de Host (`157.151.2.91` ou `never-land.rocks`), Porta e Usuário (`opc`).
 * 2. Alternância entre Autenticação por Chave Privada (RSA / Ed25519) e Senha.
 * 3. Importação direta de arquivo `.key` / `.pem` do armazenamento do smartphone,
 *    colagem da área de transferência ou restauração em 1 toque da chave oficial Never Land OCI.
 * 4. Botões para Salvar & Conectar, Testar Handshake com medição de latência (`ms`) e Desconectar.
 * 5. Painel de Diagnósticos da VM (`mc recursos`, `mc rede`, `mc config`, `mc boot`)
 *    com visualização em modal monoespaçado.
 * ==============================================================================
 */
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onHostChanged: (String) -> Unit,
    onPortChanged: (String) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onAuthTypeChanged: (AuthType) -> Unit,
    onPrivateKeyChanged: (String) -> Unit,
    onPassphraseChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onToggleKeyVisibility: () -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onLoadDefaultProfile: () -> Unit,
    onKeyFileImported: (String) -> Unit,
    onSaveAndConnect: () -> Unit,
    onTestConnection: () -> Unit,
    onDisconnect: () -> Unit,
    onClearCredentials: () -> Unit,
    onRunDiagnostic: (DiagnosticReportType) -> Unit,
    onDismissDiagnostic: () -> Unit,
    onClearSnackbar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    // Seletor nativo de arquivos do Android para importar chaves .key / .pem do telefone
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val content = context.contentResolver.openInputStream(it)?.bufferedReader()?.use { r -> r.readText() }
                if (!content.isNullOrBlank()) {
                    onKeyFileImported(content)
                }
            } catch (e: Exception) {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Erro ao ler arquivo de chave: ${e.message}")
                }
            }
        }
    }

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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Cabeçalho e Status do Cofre KeyStore
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Conexão SSH & Diagnóstico",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryWhite
                    )
                    Text(
                        text = "Cofre AES-256 Android KeyStore • SSHJ 0.39",
                        style = MaterialTheme.typography.bodySmall,
                        color = DiamondBlueSecondary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (uiState.hasSavedCredentials) GrassGreenDark.copy(alpha = 0.3f) else BedrockSurfaceElevated,
                    border = BorderStroke(1.dp, if (uiState.hasSavedCredentials) GrassGreenPrimary else BedrockBorderOutline)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = if (uiState.hasSavedCredentials) GrassGreenPrimary else TextDisabledGrey,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (uiState.hasSavedCredentials) "KeyStore Ativo" else "Sem Chave",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (uiState.hasSavedCredentials) GrassGreenPrimary else TextSecondaryMuted
                        )
                    }
                }
            }

            // 2. Banner de Resultado de Teste / Estado de Conexão
            uiState.testResultBanner?.let { bannerMsg ->
                val bannerColor = when (uiState.isTestSuccess) {
                    true -> GrassGreenPrimary
                    false -> RedstoneRedError
                    null -> GoldYellowTertiary
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = bannerColor.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, bannerColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (uiState.isTestSuccess == false) Icons.Default.ErrorOutline else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = bannerColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = bannerMsg,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimaryWhite,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // 3. Card de Parâmetros SSH (Host, Porta, Usuário e Presets OCI)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = BedrockSurface),
                border = BorderStroke(1.dp, BedrockBorderOutline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Servidor Oracle Cloud (Never Land)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = DiamondBlueSecondary
                        )

                        // Botão 1-Toque para restaurar perfil oficial Never Land
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onLoadDefaultProfile()
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = DiamondBlueDark.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, DiamondBlueSecondary)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDone,
                                    contentDescription = null,
                                    tint = DiamondBlueLight,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Perfil Oficial OCI",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DiamondBlueLight
                                )
                            }
                        }
                    }

                    // Chips rápidos para alternar entre IP direto e domínio FQDN
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("157.151.2.91" to "IP Direto OCI", "never-land.rocks" to "Domínio DNS").forEach { (hostVal, label) ->
                            val isSelected = uiState.host.trim() == hostVal
                            Surface(
                                onClick = { onHostChanged(hostVal) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) GrassGreenDark.copy(alpha = 0.4f) else BedrockSurfaceElevated,
                                border = BorderStroke(1.dp, if (isSelected) GrassGreenPrimary else BedrockBorderOutline),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "$label ($hostVal)",
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) GrassGreenPrimary else TextSecondaryMuted,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    // Campos de Host, Porta e Usuário
                    OutlinedTextField(
                        value = uiState.host,
                        onValueChange = onHostChanged,
                        label = { Text("Host / Endereço IP da VM") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DiamondBlueSecondary,
                            unfocusedBorderColor = BedrockBorderOutline
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = uiState.username,
                            onValueChange = onUsernameChanged,
                            label = { Text("Usuário Linux") },
                            singleLine = true,
                            modifier = Modifier.weight(1.4f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = DiamondBlueSecondary,
                                unfocusedBorderColor = BedrockBorderOutline
                            )
                        )

                        OutlinedTextField(
                            value = uiState.port,
                            onValueChange = onPortChanged,
                            label = { Text("Porta SSH") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = DiamondBlueSecondary,
                                unfocusedBorderColor = BedrockBorderOutline
                            )
                        )
                    }

                    // Seletor de Tipo de Autenticação (Chave Privada vs Senha)
                    Text(
                        text = "Método de Autenticação SSH:",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondaryMuted
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AuthType.entries.forEach { type ->
                            val isSelected = uiState.authType == type
                            val title = if (type == AuthType.PRIVATE_KEY) "Chave Privada (PEM/RSA)" else "Senha Textual"
                            val icon = if (type == AuthType.PRIVATE_KEY) Icons.Default.Key else Icons.Default.Lock

                            Surface(
                                onClick = { onAuthTypeChanged(type) },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) GrassGreenDark else BedrockSurfaceElevated,
                                border = BorderStroke(1.dp, if (isSelected) GrassGreenPrimary else BedrockBorderOutline),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = if (isSelected) TextPrimaryWhite else TextSecondaryMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = title,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) TextPrimaryWhite else TextSecondaryMuted
                                    )
                                }
                            }
                        }
                    }

                    // Seção de Credenciais conforme o tipo selecionado
                    if (uiState.authType == AuthType.PRIVATE_KEY) {
                        // Botões de Importação Rápida de Chave Privada
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { filePickerLauncher.launch("*/*") },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, DiamondBlueSecondary),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.FileOpen, contentDescription = null, tint = DiamondBlueSecondary, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Importar .key/.pem", fontSize = 11.sp, color = DiamondBlueSecondary)
                            }

                            OutlinedButton(
                                onClick = {
                                    val clipText = clipboard.getText()?.text
                                    if (!clipText.isNullOrBlank()) {
                                        onPrivateKeyChanged(clipText)
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, GrassGreenPrimary),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = null, tint = GrassGreenPrimary, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Colar Chave", fontSize = 11.sp, color = GrassGreenPrimary)
                            }
                        }

                        OutlinedTextField(
                            value = if (uiState.isKeyVisible) {
                                uiState.privateKeyPem
                            } else if (uiState.privateKeyPem.isNotBlank()) {
                                "-----BEGIN RSA PRIVATE KEY-----\n[CHAVE PRIVADA PROTEGIDA NO KEYSTORE - ${uiState.privateKeyPem.length} BYTES]\nToque no ícone de olho ao lado para inspecionar ou editar.\n-----END RSA PRIVATE KEY-----"
                            } else {
                                ""
                            },
                            onValueChange = { if (uiState.isKeyVisible || uiState.privateKeyPem.isBlank()) onPrivateKeyChanged(it) },
                            readOnly = !uiState.isKeyVisible && uiState.privateKeyPem.isNotBlank(),
                            label = { Text("Chave Privada PEM / OpenSSH") },
                            trailingIcon = {
                                IconButton(onClick = onToggleKeyVisibility) {
                                    Icon(
                                        imageVector = if (uiState.isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Alternar visibilidade da chave",
                                        tint = DiamondBlueSecondary
                                    )
                                }
                            },
                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = ConsoleFontFamily,
                                fontSize = 11.sp,
                                color = TextPrimaryWhite
                            ),
                            minLines = 3,
                            maxLines = 6,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GrassGreenPrimary,
                                unfocusedBorderColor = BedrockBorderOutline
                            )
                        )

                        OutlinedTextField(
                            value = uiState.passphrase,
                            onValueChange = onPassphraseChanged,
                            label = { Text("Passphrase da Chave (Opcional)") },
                            singleLine = true,
                            visualTransformation = if (uiState.isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = onTogglePasswordVisibility) {
                                    Icon(
                                        imageVector = if (uiState.isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = DiamondBlueSecondary,
                                unfocusedBorderColor = BedrockBorderOutline
                            )
                        )
                    } else {
                        OutlinedTextField(
                            value = uiState.password,
                            onValueChange = onPasswordChanged,
                            label = { Text("Senha do Usuário SSH") },
                            singleLine = true,
                            visualTransformation = if (uiState.isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = onTogglePasswordVisibility) {
                                    Icon(
                                        imageVector = if (uiState.isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GrassGreenPrimary,
                                unfocusedBorderColor = BedrockBorderOutline
                            )
                        )
                    }

                    // Botões Principais de Conexão e Teste
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSaveAndConnect()
                            },
                            enabled = !uiState.isConnecting && !uiState.isTestingConnection,
                            colors = ButtonDefaults.buttonColors(containerColor = GrassGreenDark),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1.2f)
                        ) {
                            if (uiState.isConnecting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = TextPrimaryWhite
                                )
                            } else {
                                Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (uiState.isConnected) "Reconectar SSH" else "Salvar & Conectar",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onTestConnection()
                            },
                            enabled = !uiState.isConnecting && !uiState.isTestingConnection,
                            colors = ButtonDefaults.buttonColors(containerColor = DiamondBlueDark),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (uiState.isTestingConnection) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = TextPrimaryWhite
                                )
                            } else {
                                Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Testar Ping", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Botões Secundários: Desconectar e Limpar Cofre
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (uiState.isConnected) {
                            OutlinedButton(
                                onClick = onDisconnect,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, GoldYellowTertiary),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.LinkOff, contentDescription = null, tint = GoldYellowTertiary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Desconectar", fontSize = 11.sp, color = GoldYellowTertiary)
                            }
                        }

                        OutlinedButton(
                            onClick = onClearCredentials,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, RedstoneRedError.copy(alpha = 0.7f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null, tint = RedstoneRedError, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Limpar KeyStore", fontSize = 11.sp, color = RedstoneRedError)
                        }
                    }
                }
            }

            // 4. Seção de Diagnósticos Avançados do Sistema Operacional (Oracle Linux 9.7)
            Text(
                text = "Diagnósticos da Máquina Virtual (mc.py)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimaryWhite
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DiagnosticReportType.entries.forEach { diagType ->
                    val diagIcon = when (diagType) {
                        DiagnosticReportType.RESOURCES -> Icons.Default.Memory
                        DiagnosticReportType.NETWORK -> Icons.Default.NetworkCheck
                        DiagnosticReportType.CONFIG -> Icons.Default.SettingsApplications
                        DiagnosticReportType.BOOT_AUDIT -> Icons.Default.Dns
                    }

                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onRunDiagnostic(diagType)
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = BedrockSurface,
                        border = BorderStroke(1.dp, BedrockBorderOutline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(BedrockSurfaceElevated),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = diagIcon,
                                        contentDescription = null,
                                        tint = DiamondBlueSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Text(
                                        text = diagType.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimaryWhite
                                    )
                                    Text(
                                        text = diagType.subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondaryMuted
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = BedrockSurfaceElevated,
                                border = BorderStroke(1.dp, DiamondBlueSecondary.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = diagType.commandLabel,
                                    fontFamily = ConsoleFontFamily,
                                    fontSize = 10.sp,
                                    color = DiamondBlueLight,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Snackbar Host
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )

        // Modal de Relatório Diagnóstico em Terminal Monoespaçado
        uiState.activeDiagnosticType?.let { diagType ->
            AlertDialog(
                onDismissRequest = onDismissDiagnostic,
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = diagType.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimaryWhite
                            )
                            Text(
                                text = "Comando: ${diagType.commandLabel}",
                                fontFamily = ConsoleFontFamily,
                                fontSize = 11.sp,
                                color = DiamondBlueSecondary
                            )
                        }
                        IconButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(uiState.diagnosticOutput))
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Relatório copiado!")
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copiar relatório",
                                tint = DiamondBlueSecondary
                            )
                        }
                    }
                },
                text = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 180.dp, max = 380.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(ConsoleBackgroundDark)
                            .border(1.dp, BedrockBorderOutline, RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        if (uiState.isLoadingDiagnostic) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(color = DiamondBlueSecondary)
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = uiState.diagnosticOutput,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondaryMuted
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = uiState.diagnosticOutput,
                                    fontFamily = ConsoleFontFamily,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                    color = DiamondBlueLight
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { onRunDiagnostic(diagType) },
                        enabled = !uiState.isLoadingDiagnostic,
                        colors = ButtonDefaults.buttonColors(containerColor = DiamondBlueDark)
                    ) {
                        Text("Atualizar")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = onDismissDiagnostic) {
                        Text("Fechar", color = TextSecondaryMuted)
                    }
                },
                containerColor = BedrockSurface,
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}

