package com.neverland.servermanager.data.security

import com.neverland.servermanager.domain.model.AuthType
import com.neverland.servermanager.domain.model.SshConfig
import java.util.concurrent.ConcurrentHashMap

/**
 * ==============================================================================
 * Never Land Server Manager - Armazenamento Seguro em Memória (InMemorySecureStorage.kt)
 * ==============================================================================
 *
 * Implementação puramente em memória do contrato [SecureStorage], projetada para
 * testes unitários executados diretamente na JVM (Desktop JUnit).
 *
 * Vantagens Arquiteturais:
 * 1. Independência de Plataforma: Não referencia `android.content.Context` nem
 *    `AndroidKeyStore`, permitindo que os testes rodem em milissegundos sem emulador.
 * 2. Thread-Safety: Utiliza [ConcurrentHashMap] com sincronização atômica para suportar
 *    testes concorrentes de corrotinas.
 * 3. Fidelidade Funcional: Reproduz exatamente o mesmo comportamento de persistência,
 *    leitura e limpeza de credenciais do [EncryptedSecureStorage] de produção.
 * ==============================================================================
 */
class InMemorySecureStorage : SecureStorage {

    companion object {
        const val DEFAULT_HOST = "157.151.2.91"
        const val DEFAULT_PORT = 22
        const val DEFAULT_USERNAME = "opc"
    }

    // Mapa thread-safe em memória simulando o dicionário de preferências encriptadas
    private val storage = ConcurrentHashMap<String, Any>()

    @Synchronized
    override fun saveSshConfig(config: SshConfig) {
        storage["host"] = config.host
        storage["port"] = config.port
        storage["username"] = config.username
        storage["authType"] = config.authType.name
        storage["connectionTimeoutSeconds"] = config.connectionTimeoutSeconds
        storage["keepAliveIntervalSeconds"] = config.keepAliveIntervalSeconds

        if (config.privateKeyPem != null) {
            storage["privateKeyPem"] = config.privateKeyPem
        } else {
            storage.remove("privateKeyPem")
        }

        if (config.passphrase != null) {
            storage["passphrase"] = config.passphrase
        } else {
            storage.remove("passphrase")
        }

        if (config.password != null) {
            storage["password"] = config.password
        } else {
            storage.remove("password")
        }
    }

    @Synchronized
    override fun loadSshConfig(): SshConfig {
        val host = storage["host"] as? String ?: DEFAULT_HOST
        val port = storage["port"] as? Int ?: DEFAULT_PORT
        val username = storage["username"] as? String ?: DEFAULT_USERNAME
        val authTypeName = storage["authType"] as? String ?: AuthType.PRIVATE_KEY.name
        val authType = try {
            AuthType.valueOf(authTypeName)
        } catch (_: Exception) {
            AuthType.PRIVATE_KEY
        }

        val privateKeyPem = storage["privateKeyPem"] as? String
        val passphrase = storage["passphrase"] as? String
        val password = storage["password"] as? String
        val timeoutSeconds = storage["connectionTimeoutSeconds"] as? Int ?: 15
        val keepAliveSeconds = storage["keepAliveIntervalSeconds"] as? Int ?: 15

        return SshConfig(
            host = host,
            port = port,
            username = username,
            authType = authType,
            privateKeyPem = privateKeyPem,
            passphrase = passphrase,
            password = password,
            connectionTimeoutSeconds = timeoutSeconds,
            keepAliveIntervalSeconds = keepAliveSeconds
        )
    }

    @Synchronized
    override fun clearCredentials() {
        storage.remove("privateKeyPem")
        storage.remove("passphrase")
        storage.remove("password")
    }

    @Synchronized
    override fun hasSavedCredentials(): Boolean {
        val pem = storage["privateKeyPem"] as? String
        val pass = storage["password"] as? String
        return !pem.isNullOrBlank() || !pass.isNullOrBlank()
    }
}
