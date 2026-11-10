package com.neverland.servermanager.ui.settings

import com.neverland.servermanager.domain.model.AuthType
import com.neverland.servermanager.domain.model.ConnectionState

/**
 * ==============================================================================
 * Never Land Server Manager - Estado de UI de Ajustes & Diagnóstico (SettingsUiState.kt)
 * ==============================================================================
 *
 * Estrutura imutável que representa todo o estado visual e interativo da tela
 * de Configurações de Conexão SSH, Cofre Criptográfico (Android KeyStore) e
 * Diagnósticos Avançados do Sistema Operacional Oracle Linux 9.7 UEK.
 * ==============================================================================
 */

/**
 * Tipos de relatórios diagnósticos da máquina virtual disponíveis na aba de Ajustes.
 */
enum class DiagnosticReportType(
    val title: String,
    val subtitle: String,
    val commandLabel: String
) {
    RESOURCES(
        title = "Recursos de Memória, Swap e Disco",
        subtitle = "Inspeciona RAM física, Swap NVMe, partição raiz e Cgroups v2",
        commandLabel = "mc recursos"
    ),
    NETWORK(
        title = "Rede, Firewalld e Portas 19132",
        subtitle = "Portas abertas no firewalld e sockets UDP/TCP em escuta no kernel",
        commandLabel = "mc rede"
    ),
    CONFIG(
        title = "Propriedades do BDS (server.properties)",
        subtitle = "Exibe todas as diretivas ativas configuradas no motor Bedrock",
        commandLabel = "mc config"
    ),
    BOOT_AUDIT(
        title = "Auditoria de Autostart no Boot da VM",
        subtitle = "Valida systemd, NetworkManager-wait-online, firewalld e /etc/fstab",
        commandLabel = "mc boot"
    )
}

/**
 * Estado consolidado da tela [SettingsScreen].
 */
data class SettingsUiState(
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val host: String = "157.151.2.91",
    val port: String = "22",
    val username: String = "opc",
    val authType: AuthType = AuthType.PRIVATE_KEY,
    val privateKeyPem: String = "",
    val passphrase: String = "",
    val password: String = "",
    val isKeyVisible: Boolean = false,
    val isPasswordVisible: Boolean = false,
    val hasSavedCredentials: Boolean = false,
    val isConnecting: Boolean = false,
    val isTestingConnection: Boolean = false,
    val testResultBanner: String? = null,
    val isTestSuccess: Boolean? = null,
    // Estado dos Modais de Diagnóstico da VM
    val activeDiagnosticType: DiagnosticReportType? = null,
    val isLoadingDiagnostic: Boolean = false,
    val diagnosticOutput: String = "",
    val snackbarMessage: String? = null
) {
    /**
     * Indica se a sessão SSH está atualmente conectada e autenticada.
     */
    val isConnected: Boolean
        get() = connectionState is ConnectionState.Connected
}

