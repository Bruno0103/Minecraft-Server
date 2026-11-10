package com.neverland.servermanager.domain.model

/**
 * ==============================================================================
 * Never Land Server Manager - Modelo de Configuração SSH (SshConfig.kt)
 * ==============================================================================
 *
 * Estrutura imutável de dados que encapsula todos os parâmetros de rede,
 * identificadores de usuário, credenciais criptográficas e limites operacionais
 * de tempo para a comunicação SSHv2 com a máquina virtual Never Land BDS
 * na Oracle Cloud Infrastructure (OCI).
 *
 * Princípios de Segurança e Boas Práticas:
 * 1. Sanitização de Saída (Data Leak Prevention - DLP): O método [toString] padrão
 *    do Kotlin é expressamente sobrescrito para que senhas, passphrases e chaves
 *    privadas jamais sejam impressas em logs do sistema, Logcat ou traces de depuração.
 * 2. Validação Rigorosa: O método [validate] verifica limites de portas (1..65535),
 *    não-vacuidade de campos essenciais e consistência de cabeçalhos PEM/OpenSSH.
 * 3. Imutabilidade: Todos os campos são propriedades imutáveis (val), permitindo
 *    criação de novas variantes seguras via cópia seletiva ([copy]).
 *
 * @property host Endereço IP ou domínio FQDN da VM (padrão Never Land: "157.151.2.91")
 * @property port Porta TCP do serviço sshd na VM (padrão: 22)
 * @property username Nome de usuário no sistema operacional Linux (padrão: "opc")
 * @property authType Método de autenticação selecionado (PRIVATE_KEY ou PASSWORD)
 * @property privateKeyPem Conteúdo da chave privada em formato PEM ou OpenSSH (informação sensível)
 * @property passphrase Senha opcional para decodificar chave privada protegida (informação sensível)
 * @property password Senha de login caso authType seja PASSWORD (informação sensível)
 * @property connectionTimeoutSeconds Tempo limite em segundos para conexão TCP/SSH (padrão: 15s)
 * @property keepAliveIntervalSeconds Intervalo do pacote de Heartbeat em segundos (padrão: 15s)
 * ==============================================================================
 */
data class SshConfig(
    val host: String = "157.151.2.91",
    val port: Int = 22,
    val username: String = "opc",
    val authType: AuthType = AuthType.PRIVATE_KEY,
    val privateKeyPem: String? = null,
    val passphrase: String? = null,
    val password: String? = null,
    val connectionTimeoutSeconds: Int = 15,
    val keepAliveIntervalSeconds: Int = 15
) {
    /**
     * Valida os parâmetros de conexão de acordo com os limites operacionais e de segurança.
     *
     * @return [Result.success] com a própria instância caso válida;
     *         [Result.failure] contendo [IllegalArgumentException] descritiva em caso de inconsistência.
     */
    fun validate(): Result<SshConfig> {
        val trimmedHost = host.trim()
        if (trimmedHost.isEmpty()) {
            return Result.failure(IllegalArgumentException("O host do servidor não pode estar vazio."))
        }

        if (port !in 1..65535) {
            return Result.failure(IllegalArgumentException("Porta inválida: $port. A porta deve estar entre 1 e 65535."))
        }

        val trimmedUser = username.trim()
        if (trimmedUser.isEmpty()) {
            return Result.failure(IllegalArgumentException("O nome de usuário SSH não pode estar vazio."))
        }

        if (connectionTimeoutSeconds <= 0) {
            return Result.failure(IllegalArgumentException("O timeout de conexão deve ser maior que zero."))
        }

        if (keepAliveIntervalSeconds < 0) {
            return Result.failure(IllegalArgumentException("O intervalo de keep-alive não pode ser negativo."))
        }

        when (authType) {
            AuthType.PASSWORD -> {
                if (password.isNullOrBlank()) {
                    return Result.failure(IllegalArgumentException("A senha deve ser preenchida para o tipo de autenticação PASSWORD."))
                }
            }
            AuthType.PRIVATE_KEY -> {
                if (privateKeyPem.isNullOrBlank()) {
                    return Result.failure(IllegalArgumentException("O conteúdo da chave privada PEM deve ser fornecido para o tipo PRIVATE_KEY."))
                }
                val trimmedKey = privateKeyPem.trim()
                val hasValidHeader = (trimmedKey.contains("-----BEGIN RSA PRIVATE KEY-----") && trimmedKey.contains("-----END RSA PRIVATE KEY-----")) ||
                        (trimmedKey.contains("-----BEGIN OPENSSH PRIVATE KEY-----") && trimmedKey.contains("-----END OPENSSH PRIVATE KEY-----")) ||
                        (trimmedKey.contains("-----BEGIN PRIVATE KEY-----") && trimmedKey.contains("-----END PRIVATE KEY-----"))

                if (!hasValidHeader) {
                    return Result.failure(IllegalArgumentException("Formato de chave privada inválido. Esperado formato PEM (PKCS#1/PKCS#8) ou OpenSSH."))
                }
            }
        }

        return Result.success(this)
    }

    /**
     * Verifica de forma simplificada se a configuração atual possui as credenciais mínimas
     * preenchidas para o tipo de autenticação selecionado.
     *
     * @return true se houver credencial presente e não-vazia; false caso contrário.
     */
    fun hasValidCredentials(): Boolean {
        return when (authType) {
            AuthType.PASSWORD -> !password.isNullOrBlank()
            AuthType.PRIVATE_KEY -> !privateKeyPem.isNullOrBlank()
        }
    }

    /**
     * Representação textual segura e higienizada para logs de sistema e relatórios de depuração.
     * Mascara rigorosamente dados sensíveis como senhas e chaves privadas para evitar vazamento
     * acidental no Logcat do Android.
     */
    override fun toString(): String {
        val maskedPassword = if (password.isNullOrEmpty()) "null" else "********"
        val maskedPassphrase = if (passphrase.isNullOrEmpty()) "null" else "********"
        val maskedKey = if (privateKeyPem.isNullOrEmpty()) {
            "null"
        } else {
            "[CHAVE_PRIVADA_PRESENTE_${privateKeyPem.length}_CHARS]"
        }

        return "SshConfig(host='$host', port=$port, username='$username', authType=$authType, " +
                "password=$maskedPassword, passphrase=$maskedPassphrase, privateKeyPem=$maskedKey, " +
                "connectionTimeoutSeconds=$connectionTimeoutSeconds, keepAliveIntervalSeconds=$keepAliveIntervalSeconds)"
    }
}
