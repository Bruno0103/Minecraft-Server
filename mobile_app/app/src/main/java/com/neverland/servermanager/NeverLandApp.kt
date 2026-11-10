package com.neverland.servermanager

import android.app.Application
import android.util.Log
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

/**
 * ==============================================================================
 * Never Land Server Manager - Classe Application Global (NeverLandApp.kt)
 * ==============================================================================
 * 
 * Esta classe estende [Application] e é instanciada pelo sistema operacional
 * Android antes de qualquer Activity, Service ou componente de segundo plano.
 * 
 * Responsabilidade Primária:
 * Configurar e registrar os provedores criptográficos de segurança (Security Providers)
 * necessários para a comunicação SSHv2 segura com o servidor remoto Never Land BDS
 * (Oracle Linux 9.7 na nuvem Oracle Cloud Infrastructure).
 * 
 * Contexto Criptográfico (Bouncy Castle & Android):
 * O sistema operacional Android inclui historicamente uma versão interna, antiga e
 * reduzida do provedor "BC" (Bouncy Castle) em seu classpath de sistema. Essa versão
 * interna carece de suporte nativo a algoritmos modernos de curva elíptica essenciais,
 * como Ed25519 (EdDSA) e novas especificações de chaves privadas OpenSSH (PKCS#8 v1/v2).
 * 
 * Sem o registro prioritário da biblioteca Bouncy Castle moderna (`bcprov-jdk18on`),
 * tentativas de decodificar chaves SSH Ed25519 ou estabelecer conexões SSH com
 * servidores Linux atualizados resultam em exceções fatais como [java.security.NoSuchAlgorithmException].
 * 
 * ==============================================================================
 */
class NeverLandApp : Application() {

    companion object {
        /**
         * Identificador de tag para mensagens de diagnóstico no Android Logcat.
         */
        private const val TAG = "NeverLandApp"
    }

    /**
     * Ponto de entrada do ciclo de vida da aplicação.
     * Executado uma única vez na inicialização do processo antes de qualquer UI ser inflada.
     */
    override fun onCreate() {
        super.onCreate()
        
        Log.i(TAG, "Inicializando Never Land Server Manager Application...")
        
        // Configura o provedor de segurança Bouncy Castle com alta prioridade
        setupSecurityProviders()
    }

    /**
     * Registra o [BouncyCastleProvider] moderno como o provedor criptográfico primário.
     * 
     * Procedimento Técnico:
     * 1. Removemos o provedor "BC" legado embutido no Android (se presente),
     *    evitando sombreamento ou conflitos de resolução de algoritmos criptográficos.
     * 2. Inserimos uma nova instância de [BouncyCastleProvider] na posição 1 da lista
     *    global de provedores do Java Runtime [Security.insertProviderAt].
     * 3. Registramos no Logcat o índice em que o provedor foi alocado para facilitar
     *    a auditoria de segurança durante a execução de testes.
     */
    private fun setupSecurityProviders() {
        try {
            // Remove qualquer instância legada do provedor "BC" registrada pelo sistema Android
            Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
            Log.d(TAG, "Provedor Bouncy Castle legado desvinculado com sucesso.")

            // Instancia o provedor moderno fornecido pela dependência bcprov-jdk18on
            val bouncyCastleProvider = BouncyCastleProvider()

            // Insere o provedor na posição 1 (maior prioridade na busca de algoritmos de chave/cifra)
            val insertPosition = Security.insertProviderAt(bouncyCastleProvider, 1)

            if (insertPosition != -1) {
                Log.i(
                    TAG,
                    "BouncyCastleProvider v${bouncyCastleProvider.version} registrado com sucesso na posição de segurança $insertPosition."
                )
            } else {
                // Caso retorne -1, o provedor já se encontrava na lista; forçamos o registro
                Log.w(
                    TAG,
                    "BouncyCastleProvider já constava na lista de segurança. Reposicionando no topo..."
                )
                Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
                Security.insertProviderAt(bouncyCastleProvider, 1)
            }

            Log.i(TAG, "Infraestrutura de criptografia SSH (Ed25519 / OpenSSH / RSA) pronta.")
        } catch (e: Exception) {
            // Captura qualquer erro de permissão ou runtime para que a aplicação não crashe silenciosamente
            Log.e(
                TAG,
                "Falha ao configurar provedores de segurança Bouncy Castle: ${e.message}",
                e
            )
        }
    }
}
