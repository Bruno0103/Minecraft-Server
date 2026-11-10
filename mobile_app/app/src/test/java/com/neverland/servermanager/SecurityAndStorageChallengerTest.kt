package com.neverland.servermanager

import com.neverland.servermanager.data.security.InMemorySecureStorage
import com.neverland.servermanager.data.security.SecurePreferences
import com.neverland.servermanager.data.ssh.SshManager
import com.neverland.servermanager.domain.model.AuthType
import com.neverland.servermanager.domain.model.ConnectionState
import com.neverland.servermanager.domain.model.SshConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * ==============================================================================
 * Never Land Server Manager - Suíte de Desafio Adversarial de Segurança & Armazenamento
 * ==============================================================================
 *
 * Autor: Challenger M2.2 (Security & Storage Challenger)
 * Marco: Marco 2 — SSH Networking & Secure Storage
 *
 * Esta suíte de testes atua como oráculo empírico e estresse adversarial sobre os
 * componentes de segurança e comunicação desenvolvidos no Marco 2:
 * 1. Data Leak Prevention (DLP):
 *    - Tenta extrair credenciais (senhas em texto claro, passphrases e chaves privadas)
 *      através de toString(), interpolação de strings, reflexão e mensagens de exceções.
 * 2. Fuzzing & Análise de Limites (Boundary Value Fuzzing):
 *    - Submete portas aos limites extremos (0, -1, 65536, Int.MIN_VALUE, Int.MAX_VALUE).
 *    - Submete hosts a valores vazios, compostos por espaços, tabs e quebras de linha.
 *    - Submete chaves privadas PEM a fragmentos corrompidos, incompletos, invertidos e lixo binário.
 *    - Garante que SshConfig.validate() e SshManager tratem todas as entradas graciosamente,
 *      sem exceções não tratadas e sem causar crash da aplicação móvel.
 * 3. Resiliência do Armazenamento Seguro (SecurePreferences):
 *    - Testa o ciclo de vida completo de gravação, leitura, sobrescrita e limpeza de credenciais.
 *    - Valida o isolamento entre métodos de autenticação PASSWORD e PRIVATE_KEY.
 *    - Valida a preservação de dados de rede (host e porta) após a limpeza estrita de credenciais.
 *    - Executa estresse de concorrência massiva simulando múltiplas corrotinas simultâneas.
 * ==============================================================================
 */
class SecurityAndStorageChallengerTest {

    private lateinit var securePrefs: SecurePreferences
    private lateinit var inMemoryStorage: InMemorySecureStorage
    private lateinit var sshManager: SshManager

    // Amostras de chaves criptográficas para testes adversariais
    private val validRsaPemSample = """
        -----BEGIN RSA PRIVATE KEY-----
        MIIEowIBAAKCAQEA0Y4Z1xX5j8NeverLandSuperSecretKeySampleBody1234567890
        AbCdEfGhIjKlMnOpQrStUvWxYz0123456789+/==
        -----END RSA PRIVATE KEY-----
    """.trimIndent()

    private val validOpenSshPemSample = """
        -----BEGIN OPENSSH PRIVATE KEY-----
        b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAA...NeverLandOpenSshSecretSample...
        -----END OPENSSH PRIVATE KEY-----
    """.trimIndent()

    private val validPkcs8PemSample = """
        -----BEGIN PRIVATE KEY-----
        MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgw...NeverLandPkcs8SecretSample...
        -----END PRIVATE KEY-----
    """.trimIndent()

    @Before
    fun setUp() {
        SecurePreferences.resetInstanceForTesting()
        inMemoryStorage = InMemorySecureStorage()
        securePrefs = SecurePreferences.createForTesting(inMemoryStorage)
        sshManager = SshManager()
    }

    @After
    fun tearDown() {
        SecurePreferences.resetInstanceForTesting()
    }

    // ==============================================================================
    // SEÇÃO 1: ATAQUES DE VAZAMENTO DE CREDENCIAIS (DATA LEAK PREVENTION - DLP)
    // ==============================================================================

    /**
     * Ataque DLP 1: Tentativa de extração de senha via toString() e interpolação.
     * Verifica se qualquer senha arbitrária (mesmo contendo caracteres especiais) é
     * rigorosamente mascarada e não vaza em saídas textuais formatadas.
     */
    @Test
    fun testDlpPasswordLeakageInToStringAndInterpolation() {
        val sensitivePasswords = listOf(
            "SenhaUltraSecreta123!",
            "Admin@NeverLand#2026$",
            "P@ssw0rd_Com_Espacos_E_Aspas'\"<>&",
            "123456"
        )

        for (password in sensitivePasswords) {
            val config = SshConfig(
                host = "157.151.2.91",
                port = 22,
                username = "opc",
                authType = AuthType.PASSWORD,
                password = password
            )

            val stringOutput = config.toString()
            val interpolatedOutput = "Log: $config"

            // A senha em texto claro JAMAIS pode estar presente
            assertFalse(
                "A senha em texto claro '$password' vazou em toString()!",
                stringOutput.contains(password)
            )
            assertFalse(
                "A senha em texto claro '$password' vazou na interpolação de strings!",
                interpolatedOutput.contains(password)
            )

            // Deve conter a máscara determinística de segurança
            assertTrue(
                "toString() deve exibir a senha devidamente mascarada como ********",
                stringOutput.contains("password=********")
            )
        }
    }

    /**
     * Ataque DLP 2: Tentativa de extração de Passphrase via toString().
     * Verifica que a passphrase utilizada para descriptografar chaves PEM protegidas
     * é estritamente mascarada com '********'.
     */
    @Test
    fun testDlpPassphraseLeakageInToString() {
        val sensitivePassphrases = listOf(
            "MinhaPassphraseUltraSecreta987",
            "ChaveiroOracleCloudNeverLand"
        )

        for (passphrase in sensitivePassphrases) {
            val config = SshConfig(
                host = "157.151.2.91",
                port = 22,
                username = "opc",
                authType = AuthType.PRIVATE_KEY,
                privateKeyPem = validRsaPemSample,
                passphrase = passphrase
            )

            val output = config.toString()

            assertFalse(
                "A passphrase em texto claro '$passphrase' vazou em toString()!",
                output.contains(passphrase)
            )
            assertTrue(
                "A passphrase deve constar como mascarada em toString()",
                output.contains("passphrase=********")
            )
        }
    }

    /**
     * Ataque DLP 3: Tentativa de extração do corpo da Chave Privada PEM.
     * Verifica que o corpo criptográfico bruto das chaves RSA, OpenSSH e PKCS#8
     * não é despejado em logs do sistema, exibindo apenas o marcador com a contagem de caracteres.
     */
    @Test
    fun testDlpPrivateKeyContentLeakageInToString() {
        val testKeys = listOf(
            validRsaPemSample to "RSA",
            validOpenSshPemSample to "OPENSSH",
            validPkcs8PemSample to "PKCS8"
        )

        for ((keyPem, formatName) in testKeys) {
            val config = SshConfig(
                host = "157.151.2.91",
                port = 22,
                username = "opc",
                authType = AuthType.PRIVATE_KEY,
                privateKeyPem = keyPem
            )

            val output = config.toString()

            // Trechos internos da chave não devem vazar
            assertFalse(
                "O corpo sensível da chave $formatName vazou em toString()!",
                output.contains("NeverLandSuperSecretKeySampleBody1234567890") ||
                        output.contains("NeverLandOpenSshSecretSample") ||
                        output.contains("NeverLandPkcs8SecretSample")
            )

            // Cabeçalhos brutos também não devem ser impressos no log geral do config
            assertFalse(
                "O cabeçalho PEM da chave $formatName não deve ser exibido em toString()",
                output.contains("-----BEGIN")
            )

            // Deve conter o indicador de comprimento para diagnóstico seguro
            val expectedMarker = "[CHAVE_PRIVADA_PRESENTE_${keyPem.length}_CHARS]"
            assertTrue(
                "toString() deve conter o marcador de presença e tamanho da chave: $expectedMarker",
                output.contains(expectedMarker)
            )
        }
    }

    /**
     * Ataque DLP 4: Verificação de que mensagens de erro de validação (validate())
     * não refletem credenciais inseridas pelo usuário caso inválidas.
     */
    @Test
    fun testDlpValidationExceptionsDoNotEchoCredentials() {
        val secretPassword = "SecretPasswordShouldNotBeEchoed123"
        val configWithPass = SshConfig(
            host = "", // Provoca falha de validação propositalmente
            port = 22,
            username = "opc",
            authType = AuthType.PASSWORD,
            password = secretPassword
        )

        val resultPass = configWithPass.validate()
        assertTrue(resultPass.isFailure)
        val errorMessagePass = resultPass.exceptionOrNull()?.message ?: ""

        assertFalse(
            "A mensagem de exceção de validação não deve ecoar a senha do usuário!",
            errorMessagePass.contains(secretPassword)
        )

        // Teste com chave malformada contendo trecho secreto
        val malformedSecretPem = "-----BEGIN INVALID KEY-----\nSuperSecretBytes12345\n-----END INVALID KEY-----"
        val configWithKey = SshConfig(
            host = "157.151.2.91",
            port = 22,
            username = "opc",
            authType = AuthType.PRIVATE_KEY,
            privateKeyPem = malformedSecretPem
        )

        val resultKey = configWithKey.validate()
        assertTrue(resultKey.isFailure)
        val errorMessageKey = resultKey.exceptionOrNull()?.message ?: ""

        assertFalse(
            "A mensagem de exceção de validação não deve conter o conteúdo da chave!",
            errorMessageKey.contains("SuperSecretBytes12345")
        )
    }

    /**
     * Ataque DLP 5: Validação de cópia rasa ou imutável (copy()) mantendo sanitização de toString().
     */
    @Test
    fun testDlpCopyPreservesSanitization() {
        val original = SshConfig(
            host = "157.151.2.91",
            password = "SecretPasswordOriginal",
            authType = AuthType.PASSWORD
        )

        val copied = original.copy(port = 2222)
        val stringCopied = copied.toString()

        assertFalse(
            "A cópia de SshConfig expôs a senha original em toString()!",
            stringCopied.contains("SecretPasswordOriginal")
        )
        assertTrue(stringCopied.contains("password=********"))
    }

    // ==============================================================================
    // SEÇÃO 2: FUZZING E VALORES DE FRONTEIRA (BOUNDARY VALUES & MALFORMED INPUTS)
    // ==============================================================================

    /**
     * Fuzzing 1: Portas TCP fora do intervalo de 16 bits (1..65535).
     * Testa valores limites inferiores, superiores e negativos extremos.
     */
    @Test
    fun testFuzzingInvalidPortBoundaries() {
        val boundaryInvalidPorts = listOf(
            0,                     // Limite inferior exato inválido
            -1,                    // Negativo imediato
            -22,                   // Porta padrão negativa
            -65535,                // Menos limite superior
            65536,                 // Limite superior exato inválido (2^16)
            65537,                 // Acima do limite
            70000,                 // Faixa alta
            99999,                 // Porta de 5 dígitos alta
            Int.MAX_VALUE,         // Extremo positivo da JVM (2147483647)
            Int.MIN_VALUE          // Extremo negativo da JVM (-2147483648)
        )

        for (port in boundaryInvalidPorts) {
            val config = SshConfig(
                port = port,
                authType = AuthType.PASSWORD,
                password = "validPassword"
            )
            val result = config.validate()
            assertTrue(
                "A porta inválida $port deveria ser expressamente rejeitada por validate()",
                result.isFailure
            )
            val msg = result.exceptionOrNull()?.message ?: ""
            assertTrue(
                "A mensagem deve indicar que a porta $port está fora de 1..65535",
                msg.contains("Porta inválida") && msg.contains(port.toString())
            )
        }
    }

    /**
     * Fuzzing 2: Portas TCP válidas nos limites de fronteira (1 e 65535).
     */
    @Test
    fun testValidPortBoundariesAcceptance() {
        val boundaryValidPorts = listOf(1, 22, 80, 443, 2222, 19132, 65534, 65535)

        for (port in boundaryValidPorts) {
            val config = SshConfig(
                port = port,
                authType = AuthType.PASSWORD,
                password = "validPassword"
            )
            val result = config.validate()
            assertTrue("A porta válida de fronteira $port deveria ser aceita", result.isSuccess)
        }
    }

    /**
     * Fuzzing 3: Hosts malformados, vazios, com caracteres de escape ou injeções.
     */
    @Test
    fun testFuzzingMalformedHosts() {
        val malformedHosts = listOf(
            "",                    // Vazio puro
            " ",                   // Espaço simples
            "    ",                // Múltiplos espaços
            "\t",                  // Tabulação
            "\n",                  // Quebra de linha Unix
            "\r\n",                // Quebra de linha Windows
            "   \t\r\n   "         // Mistura de whitespace
        )

        for (host in malformedHosts) {
            val config = SshConfig(
                host = host,
                authType = AuthType.PASSWORD,
                password = "validPassword"
            )
            val result = config.validate()
            assertTrue("O host malformado/vazio '$host' deveria ser rejeitado", result.isFailure)
            val msg = result.exceptionOrNull()?.message ?: ""
            assertTrue(
                "A mensagem deve indicar host vazio",
                msg.contains("host do servidor não pode estar vazio")
            )
        }
    }

    /**
     * Fuzzing 4: Nomes de usuário inválidos (vazios ou compostos apenas por whitespace).
     */
    @Test
    fun testFuzzingMalformedUsernames() {
        val malformedUsers = listOf("", " ", "   ", "\t", "\n\r")

        for (user in malformedUsers) {
            val config = SshConfig(
                username = user,
                authType = AuthType.PASSWORD,
                password = "validPassword"
            )
            val result = config.validate()
            assertTrue("O usuário inválido '$user' deveria ser rejeitado", result.isFailure)
            val msg = result.exceptionOrNull()?.message ?: ""
            assertTrue(
                "A mensagem deve indicar usuário vazio",
                msg.contains("nome de usuário SSH não pode estar vazio")
            )
        }
    }

    /**
     * Fuzzing 5: Timeouts e intervalos de Heartbeat inválidos.
     */
    @Test
    fun testFuzzingTimeoutsAndKeepAlive() {
        // connectionTimeoutSeconds <= 0
        val invalidTimeouts = listOf(0, -1, -15, -999, Int.MIN_VALUE)
        for (timeout in invalidTimeouts) {
            val config = SshConfig(
                connectionTimeoutSeconds = timeout,
                authType = AuthType.PASSWORD,
                password = "validPassword"
            )
            val result = config.validate()
            assertTrue("Timeout de conexão $timeout deveria ser rejeitado", result.isFailure)
        }

        // keepAliveIntervalSeconds < 0
        val invalidKeepAlives = listOf(-1, -15, -100, Int.MIN_VALUE)
        for (keepAlive in invalidKeepAlives) {
            val config = SshConfig(
                keepAliveIntervalSeconds = keepAlive,
                authType = AuthType.PASSWORD,
                password = "validPassword"
            )
            val result = config.validate()
            assertTrue("Intervalo de keep-alive $keepAlive deveria ser rejeitado", result.isFailure)
        }
    }

    /**
     * Fuzzing 6: Chaves PEM corrompidas, truncadas, com cabeçalhos incompatíveis ou falsos.
     */
    @Test
    fun testFuzzingMalformedPemKeys() {
        val corruptedKeys = listOf(
            // Chave sem cabeçalho nem rodapé
            "MIIEowIBAAKCAQEA0Y4Z1xX5j8NeverLandSampleBody...",
            // Cabeçalho presente mas sem rodapé
            "-----BEGIN RSA PRIVATE KEY-----\nMIIEowIBAAKCAQEA0Y4Z1xX5j8...",
            // Rodapé presente mas sem cabeçalho
            "MIIEowIBAAKCAQEA0Y4Z1xX5j8...\n-----END RSA PRIVATE KEY-----",
            // Cabeçalho RSA com rodapé OPENSSH (incompatível)
            "-----BEGIN RSA PRIVATE KEY-----\nMIIEowIBAAKCAQEA0Y4Z1xX5j8...\n-----END OPENSSH PRIVATE KEY-----",
            // Cabeçalho OPENSSH com rodapé RSA (incompatível)
            "-----BEGIN OPENSSH PRIVATE KEY-----\nb3BlbnNzaC...\n-----END RSA PRIVATE KEY-----",
            // Tipo de chave não suportado (DSA)
            "-----BEGIN DSA PRIVATE KEY-----\nMIIBvAIBAAKCAQEA...\n-----END DSA PRIVATE KEY-----",
            // Chave com certificado ao invés de chave privada
            "-----BEGIN CERTIFICATE-----\nMIIDXTCCAkWgAwIBAg...\n-----END CERTIFICATE-----"
        )

        for (corruptedKey in corruptedKeys) {
            val config = SshConfig(
                host = "157.151.2.91",
                port = 22,
                username = "opc",
                authType = AuthType.PRIVATE_KEY,
                privateKeyPem = corruptedKey
            )

            val result = config.validate()
            assertTrue(
                "A chave corrompida deveria ser rejeitada por validate():\n$corruptedKey",
                result.isFailure
            )
            val msg = result.exceptionOrNull()?.message ?: ""
            assertTrue(
                "A mensagem deve indicar formato de chave privada inválido",
                msg.contains("Formato de chave privada inválido")
            )
        }
    }

    /**
     * Fuzzing 7: Senha vazia, em branco ou nula para tipo de autenticação PASSWORD.
     */
    @Test
    fun testFuzzingBlankPasswordRejection() {
        val blankPasswords = listOf(null, "", " ", "   ", "\t", "\n\r")

        for (pass in blankPasswords) {
            val config = SshConfig(
                authType = AuthType.PASSWORD,
                password = pass
            )

            val result = config.validate()
            assertTrue(
                "Senha vazia/em branco '$pass' deveria ser rejeitada para AuthType.PASSWORD",
                result.isFailure
            )
            assertFalse(config.hasValidCredentials())
        }
    }

    /**
     * Fuzzing 8: Submeter SshManager.connect() a configurações malformadas.
     * Garante que SshManager rejeita antes de abrir sockets, transiciona para ConnectionState.Error
     * e retorna Result.failure sem disparar exceções não tratadas na corrotina.
     */
    @Test
    fun testSshManagerResilienceAgainstMalformedConfigs() = runBlocking {
        val invalidConfigs = listOf(
            // Porta acima do limite
            SshConfig(port = 70000, authType = AuthType.PASSWORD, password = "pwd"),
            // Host em branco
            SshConfig(host = "   ", authType = AuthType.PASSWORD, password = "pwd"),
            // Usuário em branco
            SshConfig(username = "", authType = AuthType.PASSWORD, password = "pwd"),
            // Senha ausente
            SshConfig(authType = AuthType.PASSWORD, password = null),
            // Chave privada truncada
            SshConfig(authType = AuthType.PRIVATE_KEY, privateKeyPem = "-----BEGIN RSA PRIVATE KEY-----"),
            // Timeout zero
            SshConfig(connectionTimeoutSeconds = 0, authType = AuthType.PASSWORD, password = "pwd")
        )

        for (config in invalidConfigs) {
            val result = sshManager.connect(config)

            // Deve falhar graciosamente
            assertTrue("SshManager.connect deve retornar Result.failure para config inválida", result.isFailure)
            assertFalse("SshManager não deve reportar como conectado", sshManager.isConnected())

            // Estado reativo deve ser ConnectionState.Error
            val state = sshManager.connectionState.value
            assertTrue("O estado deve ser ConnectionState.Error", state.isError)
            val errorState = state as ConnectionState.Error
            assertNotNull("A causa da exceção deve estar presente", errorState.cause)
            assertTrue("A mensagem de erro deve ser descritiva e não-vazia", errorState.message.isNotBlank())
        }
    }

    // ==============================================================================
    // SEÇÃO 3: RESILIÊNCIA DO ARMAZENAMENTO SEGURO (SECUREPREFERENCES)
    // ==============================================================================

    /**
     * Resiliência 1: Ciclo completo de gravação e carregamento de todos os 9 atributos.
     */
    @Test
    fun testSecurePreferencesCompleteLifecycleAllFields() {
        val fullConfig = SshConfig(
            host = "neverland.cloud.oracle.com",
            port = 2222,
            username = "minecraft_admin",
            authType = AuthType.PRIVATE_KEY,
            privateKeyPem = validOpenSshPemSample,
            passphrase = "UltraSecurePassphrase123",
            password = null,
            connectionTimeoutSeconds = 30,
            keepAliveIntervalSeconds = 45
        )

        // Grava no armazenamento seguro
        securePrefs.saveSshConfig(fullConfig)

        // Verifica status de credencial presente
        assertTrue(securePrefs.hasSavedCredentials())

        // Carrega e valida equivalência campo a campo
        val loaded = securePrefs.loadSshConfig()
        assertEquals("neverland.cloud.oracle.com", loaded.host)
        assertEquals(2222, loaded.port)
        assertEquals("minecraft_admin", loaded.username)
        assertEquals(AuthType.PRIVATE_KEY, loaded.authType)
        assertEquals(validOpenSshPemSample, loaded.privateKeyPem)
        assertEquals("UltraSecurePassphrase123", loaded.passphrase)
        assertNull(loaded.password)
        assertEquals(30, loaded.connectionTimeoutSeconds)
        assertEquals(45, loaded.keepAliveIntervalSeconds)
    }

    /**
     * Resiliência 2: Sobrescrita de credenciais com alternância de AuthType.
     * Garante que credenciais de métodos anteriores sejam limpas e não deixem resíduos no storage.
     */
    @Test
    fun testSecurePreferencesAuthTypeSwitchOverwritesCleanly() {
        // Fase 1: Grava configuração com senha
        val configPass = SshConfig(
            host = "157.151.2.91",
            port = 22,
            username = "opc",
            authType = AuthType.PASSWORD,
            password = "PrimeiraSenhaFase1"
        )
        securePrefs.saveSshConfig(configPass)

        var loaded = securePrefs.loadSshConfig()
        assertEquals(AuthType.PASSWORD, loaded.authType)
        assertEquals("PrimeiraSenhaFase1", loaded.password)
        assertNull(loaded.privateKeyPem)

        // Fase 2: Sobrescreve com chave privada (senha deve ser removida)
        val configKey = SshConfig(
            host = "157.151.2.91",
            port = 22,
            username = "opc",
            authType = AuthType.PRIVATE_KEY,
            privateKeyPem = validRsaPemSample,
            passphrase = "PassFase2"
        )
        securePrefs.saveSshConfig(configKey)

        loaded = securePrefs.loadSshConfig()
        assertEquals(AuthType.PRIVATE_KEY, loaded.authType)
        assertEquals(validRsaPemSample, loaded.privateKeyPem)
        assertEquals("PassFase2", loaded.passphrase)
        assertNull("A senha anterior deveria ter sido removida do storage ao salvar com PEM", loaded.password)

        // Fase 3: Sobrescreve de volta com senha (chave PEM e passphrase devem ser removidas)
        val configPassAgain = SshConfig(
            host = "157.151.2.91",
            port = 22,
            username = "opc",
            authType = AuthType.PASSWORD,
            password = "SegundaSenhaFase3"
        )
        securePrefs.saveSshConfig(configPassAgain)

        loaded = securePrefs.loadSshConfig()
        assertEquals(AuthType.PASSWORD, loaded.authType)
        assertEquals("SegundaSenhaFase3", loaded.password)
        assertNull("A chave privada deveria ter sido removida do storage ao salvar com senha", loaded.privateKeyPem)
        assertNull("A passphrase deveria ter sido removida do storage ao salvar com senha", loaded.passphrase)
    }

    /**
     * Resiliência 3: Limpeza total de credenciais com clearCredentials().
     * Garante que privateKeyPem, passphrase e password são completamente expurgados,
     * enquanto os parâmetros operacionais de rede (host, port, username, timeout)
     * são preservados intactos para conveniência e reconexão rápida.
     */
    @Test
    fun testSecurePreferencesClearCredentialsTotalWipePreservesNetworkMetadata() {
        val original = SshConfig(
            host = "never-land.rocks",
            port = 2200,
            username = "custom_operator",
            authType = AuthType.PRIVATE_KEY,
            privateKeyPem = validPkcs8PemSample,
            passphrase = "MinhaPassphrase",
            password = "SenhaResidual",
            connectionTimeoutSeconds = 25,
            keepAliveIntervalSeconds = 20
        )

        securePrefs.saveSshConfig(original)
        assertTrue(securePrefs.hasSavedCredentials())

        // Executa a purga de credenciais
        securePrefs.clearCredentials()

        // 1. Confirma que o status de credenciais salvas é falso
        assertFalse("Não deve haver credenciais salvas após clearCredentials()", securePrefs.hasSavedCredentials())

        // 2. Confirma que os campos de credenciais são estritamente nulos
        val loaded = securePrefs.loadSshConfig()
        assertNull("privateKeyPem deve ser null após limpeza", loaded.privateKeyPem)
        assertNull("passphrase deve ser null após limpeza", loaded.passphrase)
        assertNull("password deve ser null após limpeza", loaded.password)

        // 3. Confirma que os parâmetros de rede foram preservados
        assertEquals("never-land.rocks", loaded.host)
        assertEquals(2200, loaded.port)
        assertEquals("custom_operator", loaded.username)
        assertEquals(25, loaded.connectionTimeoutSeconds)
        assertEquals(20, loaded.keepAliveIntervalSeconds)
    }

    /**
     * Resiliência 4: Armazenamento vazio ou ausência de chaves prévias.
     * Deve retornar valores canônicos padrão seguros da infraestrutura Never Land.
     */
    @Test
    fun testSecurePreferencesEmptyStorageDefaults() {
        val emptyStorage = InMemorySecureStorage()
        val prefs = SecurePreferences.createForTesting(emptyStorage)

        assertFalse(prefs.hasSavedCredentials())

        val config = prefs.loadSshConfig()
        assertEquals("157.151.2.91", config.host)
        assertEquals(22, config.port)
        assertEquals("opc", config.username)
        assertEquals(AuthType.PRIVATE_KEY, config.authType)
        assertNull(config.privateKeyPem)
        assertNull(config.password)
        assertNull(config.passphrase)
        assertEquals(15, config.connectionTimeoutSeconds)
        assertEquals(15, config.keepAliveIntervalSeconds)
    }

    /**
     * Resiliência 5: Teste de Concorrência e Thread-Safety.
     * Simula 50 corrotinas concorrentes gravando, lendo e limpando dados simultaneamente
     * no InMemorySecureStorage / SecurePreferences para assegurar que não ocorrem
     * condições de corrida (race conditions) ou falhas de concorrência.
     */
    @Test
    fun testSecurePreferencesMassiveConcurrentStress() = runBlocking {
        val jobs = (1..50).map { index ->
            async(Dispatchers.IO) {
                if (index % 3 == 0) {
                    // Grava configuração com senha
                    securePrefs.saveSshConfig(
                        SshConfig(
                            host = "host-$index.neverland.rocks",
                            port = 2000 + index,
                            username = "user_$index",
                            authType = AuthType.PASSWORD,
                            password = "PasswordConcurrent_$index"
                        )
                    )
                } else if (index % 3 == 1) {
                    // Limpa credenciais
                    securePrefs.clearCredentials()
                } else {
                    // Lê configuração e valida que não lança exceção
                    val loaded = securePrefs.loadSshConfig()
                    assertNotNull(loaded)
                    assertNotNull(loaded.host)
                    assertTrue(loaded.port in 1..65535)
                }
            }
        }

        // Aguarda todas as 50 corrotinas concluírem
        jobs.awaitAll()

        // Após todo o estresse concorrente, o storage deve permanecer totalmente operacional
        val finalConfig = SshConfig(
            host = "final.neverland.rocks",
            port = 22,
            username = "opc",
            authType = AuthType.PASSWORD,
            password = "FinalSecurePassword"
        )
        securePrefs.saveSshConfig(finalConfig)

        val retrieved = securePrefs.loadSshConfig()
        assertEquals("final.neverland.rocks", retrieved.host)
        assertEquals("FinalSecurePassword", retrieved.password)
        assertTrue(securePrefs.hasSavedCredentials())
    }
}
