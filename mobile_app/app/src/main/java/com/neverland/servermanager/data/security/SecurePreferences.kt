package com.neverland.servermanager.data.security

import android.content.Context
import com.neverland.servermanager.domain.model.SshConfig

/**
 * ==============================================================================
 * Never Land Server Manager - Ponto Central de Armazenamento Seguro (SecurePreferences.kt)
 * ==============================================================================
 *
 * Fachada (Facade) e ponto de acesso centralizado para o armazenamento seguro de
 * credenciais e parâmetros de conexão SSH.
 *
 * Utiliza o padrão de Delegação (Class Delegation via `by storage`), repassando
 * todas as operações da interface [SecureStorage] para a implementação subjacente.
 *
 * Estratégia Híbrida:
 * - Em execução no Android: O método [getInstance] constrói o Singleton com
 *   [EncryptedSecureStorage], cifrando dados com AES-256 no Android KeyStore.
 * - Em testes unitários na JVM: O método [createForTesting] permite injetar
 *   [InMemorySecureStorage] diretamente, viabilizando execução de testes locais
 *   com velocidade máxima e sem emuladores.
 * ==============================================================================
 */
class SecurePreferences(private val storage: SecureStorage) : SecureStorage by storage {

    companion object {
        @Volatile
        private var instance: SecurePreferences? = null

        /**
         * Obtém a instância Singleton do gerenciador seguro de credenciais para produção.
         * Utiliza o padrão thread-safe com double-checked locking e lazy instantiation.
         *
         * @param context Contexto Android para inicialização do EncryptedSharedPreferences.
         * @return Instância única de [SecurePreferences].
         */
        fun getInstance(context: Context): SecurePreferences {
            return instance ?: synchronized(this) {
                instance ?: SecurePreferences(EncryptedSecureStorage(context.applicationContext)).also {
                    instance = it
                }
            }
        }

        /**
         * Fábrica para instanciação em testes unitários locais JUnit na JVM.
         * Permite injetar um armazenamento em memória ou falso sem necessitar de Context Android.
         *
         * @param storage Instância customizada de [SecureStorage] (padrão: [InMemorySecureStorage]).
         * @return Nova instância de [SecurePreferences] para escopo de teste.
         */
        fun createForTesting(storage: SecureStorage = InMemorySecureStorage()): SecurePreferences {
            return SecurePreferences(storage)
        }

        /**
         * Reinicializa a instância Singleton para assegurar isolamento entre baterias de testes.
         */
        fun resetInstanceForTesting() {
            synchronized(this) {
                instance = null
            }
        }
    }
}
