package com.neverland.servermanager.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.neverland.servermanager.data.security.SecureStorage
import com.neverland.servermanager.domain.model.AuthType
import com.neverland.servermanager.domain.model.ConnectionState
import com.neverland.servermanager.domain.model.SshConfig
import com.neverland.servermanager.domain.repository.IServerRepository
import com.neverland.servermanager.domain.repository.ISshManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ==============================================================================
 * Never Land Server Manager - ViewModel de Ajustes & Diagnóstico (SettingsViewModel.kt)
 * ==============================================================================
 *
 * Responsável por:
 * 1. Carregar e persistir credenciais SSH de forma segura via [SecureStorage]
 *    (`EncryptedSharedPreferences` + Android KeyStore).
 * 2. Estabelecer, testar e encerrar conexões SSH diretas via [ISshManager].
 * 3. Permitir importação de arquivos `.key` / `.pem` do armazenamento do celular,
 *    colagem da área de transferência ou restauração do perfil oficial Never Land OCI.
 * 4. Executar diagnósticos da máquina virtual (`mc recursos`, `mc rede`, `mc config`, `mc boot`).
 * ==============================================================================
 */
class SettingsViewModel(
    private val sshManager: ISshManager,
    private val serverRepository: IServerRepository,
    private val secureStorage: SecureStorage,
    private val bundledOciKeyProvider: () -> String? = { null }
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSavedConfiguration()
        observeConnectionState()
    }

    /**
     * Carrega a configuração salva no cofre seguro (ou pré-carrega a chave oficial da VM
     * caso ainda não existam credenciais salvas no dispositivo).
     */
    private fun loadSavedConfiguration() {
        var config = secureStorage.loadSshConfig()
        var hasCreds = secureStorage.hasSavedCredentials()

        if (!hasCreds) {
            val bundledKey = bundledOciKeyProvider()?.trim()
            if (!bundledKey.isNullOrEmpty()) {
                config = config.copy(
                    host = "157.151.2.91",
                    port = 22,
                    username = "opc",
                    authType = AuthType.PRIVATE_KEY,
                    privateKeyPem = bundledKey
                )
                secureStorage.saveSshConfig(config)
                hasCreds = true
            }
        }

        _uiState.update {
            it.copy(
                host = config.host,
                port = config.port.toString(),
                username = config.username,
                authType = config.authType,
                privateKeyPem = config.privateKeyPem ?: "",
                passphrase = config.passphrase ?: "",
                password = config.password ?: "",
                hasSavedCredentials = hasCreds
            )
        }
    }

    /**
     * Observa mudanças na máquina de estados do [ISshManager].
     */
    private fun observeConnectionState() {
        viewModelScope.launch {
            sshManager.connectionState.collect { state ->
                _uiState.update {
                    it.copy(
                        connectionState = state,
                        isConnecting = state is ConnectionState.Connecting
                    )
                }
            }
        }
    }

    // ==========================================================================
    // Atualização de Campos do Formulário SSH
    // ==========================================================================

    fun updateHost(host: String) {
        _uiState.update { it.copy(host = host, testResultBanner = null) }
    }

    fun updatePort(port: String) {
        val digitsOnly = port.filter { it.isDigit() }.take(5)
        _uiState.update { it.copy(port = digitsOnly, testResultBanner = null) }
    }

    fun updateUsername(username: String) {
        _uiState.update { it.copy(username = username, testResultBanner = null) }
    }

    fun updateAuthType(authType: AuthType) {
        _uiState.update { it.copy(authType = authType, testResultBanner = null) }
    }

    fun updatePrivateKeyPem(pem: String) {
        _uiState.update { it.copy(privateKeyPem = pem, testResultBanner = null) }
    }

    fun updatePassphrase(passphrase: String) {
        _uiState.update { it.copy(passphrase = passphrase, testResultBanner = null) }
    }

    fun updatePassword(password: String) {
        _uiState.update { it.copy(password = password, testResultBanner = null) }
    }

    fun toggleKeyVisibility() {
        _uiState.update { it.copy(isKeyVisible = !it.isKeyVisible) }
    }

    fun togglePasswordVisibility() {
        _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    /**
     * Carrega o perfil oficial da VM Never Land (OCI Always Free: 157.151.2.91, porta 22, opc e chave RSA).
     */
    fun loadDefaultNeverLandProfile() {
        val bundledKey = bundledOciKeyProvider()?.trim()
        _uiState.update {
            it.copy(
                host = "157.151.2.91",
                port = "22",
                username = "opc",
                authType = AuthType.PRIVATE_KEY,
                privateKeyPem = bundledKey ?: it.privateKeyPem,
                snackbarMessage = if (!bundledKey.isNullOrEmpty()) {
                    "Perfil e chave oficial Never Land (OCI) carregados!"
                } else {
                    "Parâmetros padrão do servidor Never Land preenchidos."
                }
            )
        }
    }

    /**
     * Importa o conteúdo textual de um arquivo `.key` ou `.pem` selecionado no celular.
     */
    fun onKeyFileImported(content: String) {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) {
            _uiState.update { it.copy(snackbarMessage = "O arquivo selecionado está vazio.") }
            return
        }

        _uiState.update {
            it.copy(
                authType = AuthType.PRIVATE_KEY,
                privateKeyPem = trimmed,
                snackbarMessage = "Chave privada importada (${trimmed.length} caracteres)!"
            )
        }
    }

    // ==========================================================================
    // Operações de Conexão, Teste e Cofre KeyStore
    // ==========================================================================

    private fun buildCurrentSshConfig(): SshConfig {
        val s = _uiState.value
        val parsedPort = s.port.toIntOrNull() ?: 22
        return SshConfig(
            host = s.host.trim(),
            port = parsedPort,
            username = s.username.trim(),
            authType = s.authType,
            privateKeyPem = s.privateKeyPem.trim().takeIf { it.isNotEmpty() },
            passphrase = s.passphrase.takeIf { it.isNotEmpty() },
            password = s.password.takeIf { it.isNotEmpty() }
        )
    }

    /**
     * Valida, salva no cofre criptografado (`EncryptedSharedPreferences`) e conecta ao servidor.
     */
    fun saveAndConnect() {
        val config = buildCurrentSshConfig()
        val validation = config.validate()
        if (validation.isFailure) {
            val err = validation.exceptionOrNull()?.message ?: "Configuração inválida."
            _uiState.update {
                it.copy(
                    testResultBanner = err,
                    isTestSuccess = false,
                    snackbarMessage = err
                )
            }
            return
        }

        secureStorage.saveSshConfig(config)
        _uiState.update {
            it.copy(
                hasSavedCredentials = secureStorage.hasSavedCredentials(),
                isConnecting = true,
                testResultBanner = null
            )
        }

        viewModelScope.launch {
            val result = sshManager.connect(config)
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isConnecting = false,
                            isTestSuccess = true,
                            testResultBanner = "Conectado e autenticado com sucesso em ${config.username}@${config.host}:${config.port}!",
                            snackbarMessage = "Conexão SSH estabelecida com sucesso!"
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isConnecting = false,
                            isTestSuccess = false,
                            testResultBanner = "Falha na conexão: ${err.message}",
                            snackbarMessage = "Erro ao conectar: ${err.message}"
                        )
                    }
                }
            )
        }
    }

    /**
     * Executa um teste completo de conectividade e mede a latência de ida e volta (`testConnection`).
     */
    fun testSshConnectionNow() {
        val config = buildCurrentSshConfig()
        val validation = config.validate()
        if (validation.isFailure) {
            val err = validation.exceptionOrNull()?.message ?: "Parâmetros inválidos."
            _uiState.update {
                it.copy(
                    testResultBanner = err,
                    isTestSuccess = false,
                    snackbarMessage = err
                )
            }
            return
        }

        secureStorage.saveSshConfig(config)
        _uiState.update {
            it.copy(
                hasSavedCredentials = secureStorage.hasSavedCredentials(),
                isTestingConnection = true,
                testResultBanner = "Testando handshake SSH e eco NEVERLAND_OK..."
            )
        }

        viewModelScope.launch {
            val result = sshManager.testConnection(config)
            result.fold(
                onSuccess = { latencyMs ->
                    _uiState.update {
                        it.copy(
                            isTestingConnection = false,
                            isTestSuccess = true,
                            testResultBanner = "Teste SSH Aprovado! Handshake + Eco verificados em ${latencyMs}ms.",
                            snackbarMessage = "Conexão verificada (${latencyMs}ms)!"
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isTestingConnection = false,
                            isTestSuccess = false,
                            testResultBanner = "Falha no teste SSH: ${err.message}",
                            snackbarMessage = "Falha no teste: ${err.message}"
                        )
                    }
                }
            )
        }
    }

    /**
     * Encerra a sessão SSH ativa.
     */
    fun disconnectSsh() {
        viewModelScope.launch {
            sshManager.disconnect()
            _uiState.update {
                it.copy(
                    testResultBanner = "Sessão SSH encerrada pelo operador.",
                    isTestSuccess = null,
                    snackbarMessage = "Desconectado do servidor."
                )
            }
        }
    }

    /**
     * Remove as credenciais salvas no Android KeyStore.
     */
    fun clearSavedCredentials() {
        viewModelScope.launch {
            sshManager.disconnect()
            secureStorage.clearCredentials()
            _uiState.update {
                it.copy(
                    privateKeyPem = "",
                    passphrase = "",
                    password = "",
                    hasSavedCredentials = false,
                    testResultBanner = "Credenciais removidas do Android KeyStore.",
                    isTestSuccess = null,
                    snackbarMessage = "Credenciais apagadas com segurança."
                )
            }
        }
    }

    // ==========================================================================
    // Diagnósticos Avançados da Máquina Virtual Oracle Linux (`mc recursos`, `rede`, `config`, `boot`)
    // ==========================================================================

    fun runDiagnostic(type: DiagnosticReportType) {
        if (!sshManager.isConnected()) {
            _uiState.update {
                it.copy(snackbarMessage = "Conecte-se ao SSH primeiro para executar '${type.commandLabel}'.")
            }
            return
        }

        _uiState.update {
            it.copy(
                activeDiagnosticType = type,
                isLoadingDiagnostic = true,
                diagnosticOutput = "Executando '${type.commandLabel}' na VM Oracle Linux..."
            )
        }

        viewModelScope.launch {
            val result = when (type) {
                DiagnosticReportType.RESOURCES -> serverRepository.fetchResourcesDiagnostic()
                DiagnosticReportType.NETWORK -> serverRepository.fetchNetworkDiagnostic()
                DiagnosticReportType.CONFIG -> serverRepository.fetchServerConfigDiagnostic()
                DiagnosticReportType.BOOT_AUDIT -> serverRepository.fetchBootAuditDiagnostic()
            }

            result.fold(
                onSuccess = { output ->
                    _uiState.update {
                        it.copy(
                            isLoadingDiagnostic = false,
                            diagnosticOutput = output.ifEmpty { "(Nenhuma saída retornada pelo comando)" }
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoadingDiagnostic = false,
                            diagnosticOutput = "[ERRO] Falha ao executar ${type.commandLabel}:\n${err.message}"
                        )
                    }
                }
            )
        }
    }

    fun dismissDiagnosticDialog() {
        _uiState.update {
            it.copy(
                activeDiagnosticType = null,
                isLoadingDiagnostic = false,
                diagnosticOutput = ""
            )
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    /**
     * Factory para criação da [SettingsViewModel].
     */
    class Factory(
        private val sshManager: ISshManager,
        private val serverRepository: IServerRepository,
        private val secureStorage: SecureStorage,
        private val bundledOciKeyProvider: () -> String? = { null }
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                return SettingsViewModel(
                    sshManager = sshManager,
                    serverRepository = serverRepository,
                    secureStorage = secureStorage,
                    bundledOciKeyProvider = bundledOciKeyProvider
                ) as T
            }
            throw IllegalArgumentException("Classe ViewModel desconhecida: ${modelClass.name}")
        }
    }
}

