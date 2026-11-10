package com.neverland.servermanager.data.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.neverland.servermanager.domain.model.AuthType
import com.neverland.servermanager.domain.model.SshConfig

/**
 * ==============================================================================
 * Never Land Server Manager - Armazenamento Criptografado Nativo (EncryptedSecureStorage.kt)
 * ==============================================================================
 *
 * Implementação de [SecureStorage] para o ambiente de produção do Android baseada
 * em [EncryptedSharedPreferences] e gerenciada pelo subsistema do Android KeyStore (TEE).
 *
 * Arquitetura Criptográfica de Duas Camadas:
 * 1. Chaves de Identificação (Keys): Cifradas com AES-256 no modo SIV (deterministico),
 *    permitindo buscas sem expor o identificador da credencial em texto claro.
 * 2. Valores Sensíveis (Values): Cifrados com AES-256 no modo GCM (autenticado),
 *    garantindo confidencialidade e integridade à prova de adulteração do arquivo XML.
 *
 * Mecanismo de Auto-Cura (Self-Healing):
 * Caso ocorra corrupção do arquivo XML ou o aparelho passe por alterações drásticas
 * no bloqueio de tela que invalidem a chave mestre no KeyStore, a exceção é capturada,
 * o arquivo corrompido é removido com segurança (`deleteSharedPreferences`) e uma nova
 * chave mestre é gerada, prevenindo travamentos cíclicos (crash loops) na inicialização do app.
 * ==============================================================================
 */
class EncryptedSecureStorage(private val context: Context) : SecureStorage {

    companion object {
        private const val TAG = "EncryptedSecureStorage"
        private const val PREFS_FILE_NAME = "neverland_secure_prefs"

        // Identificadores de chaves no dicionário encriptado
        private const val KEY_HOST = "ssh_host"
        private const val KEY_PORT = "ssh_port"
        private const val KEY_USERNAME = "ssh_username"
        private const val KEY_AUTH_TYPE = "ssh_auth_type"
        private const val KEY_PRIVATE_KEY_PEM = "ssh_private_key_pem"
        private const val KEY_PASSPHRASE = "ssh_passphrase"
        private const val KEY_PASSWORD = "ssh_password"
        private const val KEY_TIMEOUT_SECONDS = "ssh_timeout_seconds"
        private const val KEY_KEEPALIVE_SECONDS = "ssh_keepalive_seconds"

        // Valores canônicos padrão do servidor Never Land
        const val DEFAULT_HOST = "157.151.2.91"
        const val DEFAULT_PORT = 22
        const val DEFAULT_USERNAME = "opc"
    }

    private val sharedPreferences: SharedPreferences by lazy {
        initSharedPreferences(context)
    }

    /**
     * Inicializa a instância de EncryptedSharedPreferences com mecanismo de auto-cura.
     */
    private fun initSharedPreferences(context: Context): SharedPreferences {
        return try {
            createEncryptedPrefs(context)
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao abrir EncryptedSharedPreferences (KeyStore inválido ou corrupção). Executando auto-cura...", e)
            try {
                // Remove o arquivo de preferências corrompido para permitir que a nova chave seja criada
                context.deleteSharedPreferences(PREFS_FILE_NAME)
                createEncryptedPrefs(context)
            } catch (fatal: Exception) {
                Log.e(TAG, "Impossível recuperar EncryptedSharedPreferences com auto-cura.", fatal)
                throw fatal
            }
        }
    }

    /**
     * Cria a MasterKey e a instância de EncryptedSharedPreferences via AndroidX Security.
     */
    private fun createEncryptedPrefs(ctx: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(ctx, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .setRequestStrongBoxBacked(false) // Garante compatibilidade total com 100% dos aparelhos Android 8.0+
            .build()

        return EncryptedSharedPreferences.create(
            ctx,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override fun saveSshConfig(config: SshConfig) {
        sharedPreferences.edit().apply {
            putString(KEY_HOST, config.host)
            putInt(KEY_PORT, config.port)
            putString(KEY_USERNAME, config.username)
            putString(KEY_AUTH_TYPE, config.authType.name)
            putInt(KEY_TIMEOUT_SECONDS, config.connectionTimeoutSeconds)
            putInt(KEY_KEEPALIVE_SECONDS, config.keepAliveIntervalSeconds)

            if (config.privateKeyPem != null) {
                putString(KEY_PRIVATE_KEY_PEM, config.privateKeyPem)
            } else {
                remove(KEY_PRIVATE_KEY_PEM)
            }

            if (config.passphrase != null) {
                putString(KEY_PASSPHRASE, config.passphrase)
            } else {
                remove(KEY_PASSPHRASE)
            }

            if (config.password != null) {
                putString(KEY_PASSWORD, config.password)
            } else {
                remove(KEY_PASSWORD)
            }

            apply()
        }
    }

    override fun loadSshConfig(): SshConfig {
        val host = sharedPreferences.getString(KEY_HOST, DEFAULT_HOST) ?: DEFAULT_HOST
        val port = sharedPreferences.getInt(KEY_PORT, DEFAULT_PORT)
        val username = sharedPreferences.getString(KEY_USERNAME, DEFAULT_USERNAME) ?: DEFAULT_USERNAME
        val authTypeName = sharedPreferences.getString(KEY_AUTH_TYPE, AuthType.PRIVATE_KEY.name)
        val authType = try {
            AuthType.valueOf(authTypeName ?: AuthType.PRIVATE_KEY.name)
        } catch (_: Exception) {
            AuthType.PRIVATE_KEY
        }

        val privateKeyPem = sharedPreferences.getString(KEY_PRIVATE_KEY_PEM, null)
        val passphrase = sharedPreferences.getString(KEY_PASSPHRASE, null)
        val password = sharedPreferences.getString(KEY_PASSWORD, null)
        val timeoutSeconds = sharedPreferences.getInt(KEY_TIMEOUT_SECONDS, 15)
        val keepAliveSeconds = sharedPreferences.getInt(KEY_KEEPALIVE_SECONDS, 15)

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

    override fun clearCredentials() {
        sharedPreferences.edit().apply {
            remove(KEY_PRIVATE_KEY_PEM)
            remove(KEY_PASSPHRASE)
            remove(KEY_PASSWORD)
            apply()
        }
    }

    override fun hasSavedCredentials(): Boolean {
        val hasPem = !sharedPreferences.getString(KEY_PRIVATE_KEY_PEM, null).isNullOrBlank()
        val hasPass = !sharedPreferences.getString(KEY_PASSWORD, null).isNullOrBlank()
        return hasPem || hasPass
    }
}
