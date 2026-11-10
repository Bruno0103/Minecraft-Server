package com.neverland.servermanager

import com.neverland.servermanager.domain.model.AuthType
import com.neverland.servermanager.domain.model.SshConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ==============================================================================
 * Never Land Server Manager - Testes Unitários de Domínio de SshConfig
 * ==============================================================================
 *
 * Valida de forma abrangente e determinística as regras de negócio, limites
 * operacionais, validação de campos e proteção contra vazamento de credenciais
 * (Data Leak Prevention) do modelo [SshConfig].
 * ==============================================================================
 */
class SshConfigDomainTest {

    private val sampleRsaKey = """
        -----BEGIN RSA PRIVATE KEY-----
        MIIEowIBAAKCAQEA0Y4Z1xX5j8...sample...
        -----END RSA PRIVATE KEY-----
    """.trimIndent()

    private val sampleOpenSshKey = """
        -----BEGIN OPENSSH PRIVATE KEY-----
        b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAA...sample...
        -----END OPENSSH PRIVATE KEY-----
    """.trimIndent()

    private val samplePkcs8Key = """
        -----BEGIN PRIVATE KEY-----
        MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgw...sample...
        -----END PRIVATE KEY-----
    """.trimIndent()

    @Test
    fun testDefaultValuesMatchNeverLandStandard() {
        // Valida que os valores padrão correspondem à arquitetura canônica da VM Never Land
        val config = SshConfig()

        assertEquals("157.151.2.91", config.host)
        assertEquals(22, config.port)
        assertEquals("opc", config.username)
        assertEquals(AuthType.PRIVATE_KEY, config.authType)
        assertEquals(15, config.connectionTimeoutSeconds)
        assertEquals(15, config.keepAliveIntervalSeconds)
        assertFalse(config.hasValidCredentials())
    }

    @Test
    fun testValidateSuccessWithPassword() {
        // Validação com tipo de autenticação PASSWORD e senha válida
        val config = SshConfig(
            host = "never-land.rocks",
            port = 22,
            username = "opc",
            authType = AuthType.PASSWORD,
            password = "MinhaSenhaSegura123!"
        )

        val result = config.validate()
        assertTrue("A validação com senha válida deveria passar", result.isSuccess)
        assertTrue(config.hasValidCredentials())
    }

    @Test
    fun testValidateSuccessWithRsaPemKey() {
        // Validação com chave RSA clássica em formato PEM
        val config = SshConfig(
            host = "157.151.2.91",
            port = 22,
            username = "opc",
            authType = AuthType.PRIVATE_KEY,
            privateKeyPem = sampleRsaKey,
            passphrase = "senha-da-chave"
        )

        val result = config.validate()
        assertTrue("A validação com chave RSA PEM válida deveria passar", result.isSuccess)
        assertTrue(config.hasValidCredentials())
    }

    @Test
    fun testValidateSuccessWithOpenSshKey() {
        // Validação com chave moderna OpenSSH v1 (Ed25519)
        val config = SshConfig(
            host = "157.151.2.91",
            port = 2222,
            username = "opc",
            authType = AuthType.PRIVATE_KEY,
            privateKeyPem = sampleOpenSshKey
        )

        val result = config.validate()
        assertTrue("A validação com chave OpenSSH v1 válida deveria passar", result.isSuccess)
        assertTrue(config.hasValidCredentials())
    }

    @Test
    fun testValidateSuccessWithPkcs8Key() {
        // Validação com chave PKCS#8
        val config = SshConfig(
            host = "157.151.2.91",
            port = 22,
            username = "opc",
            authType = AuthType.PRIVATE_KEY,
            privateKeyPem = samplePkcs8Key
        )

        val result = config.validate()
        assertTrue("A validação com chave PKCS#8 válida deveria passar", result.isSuccess)
        assertTrue(config.hasValidCredentials())
    }

    @Test
    fun testValidateRejectsEmptyOrWhitespaceHost() {
        // Rejeita host vazio ou composto apenas de espaços
        val configEmpty = SshConfig(host = "", password = "pass", authType = AuthType.PASSWORD)
        val configSpaces = SshConfig(host = "   ", password = "pass", authType = AuthType.PASSWORD)

        assertTrue(configEmpty.validate().isFailure)
        assertTrue(configSpaces.validate().isFailure)
    }

    @Test
    fun testValidateRejectsInvalidPorts() {
        // Rejeita portas fora do intervalo TCP 1..65535
        val invalidPorts = listOf(0, -1, -22, 65536, 70000, 100000)

        for (port in invalidPorts) {
            val config = SshConfig(port = port, password = "pass", authType = AuthType.PASSWORD)
            assertTrue("A porta $port deveria ser rejeitada", config.validate().isFailure)
        }
    }

    @Test
    fun testValidateRejectsEmptyUsername() {
        // Rejeita usuário em branco
        val config = SshConfig(username = "   ", password = "pass", authType = AuthType.PASSWORD)
        assertTrue(config.validate().isFailure)
    }

    @Test
    fun testValidateRejectsMissingPasswordWhenAuthTypePassword() {
        // Rejeita autenticação por senha quando a senha não foi informada
        val configNull = SshConfig(authType = AuthType.PASSWORD, password = null)
        val configBlank = SshConfig(authType = AuthType.PASSWORD, password = "   ")

        assertTrue(configNull.validate().isFailure)
        assertTrue(configBlank.validate().isFailure)
    }

    @Test
    fun testValidateRejectsMissingOrInvalidPemKey() {
        // Rejeita chave ausente ou com cabeçalho corrompido/inválido
        val configNull = SshConfig(authType = AuthType.PRIVATE_KEY, privateKeyPem = null)
        val configInvalidHeader = SshConfig(
            authType = AuthType.PRIVATE_KEY,
            privateKeyPem = "CHAVE_SEM_CABECALHO_VALIDO_12345"
        )

        assertTrue(configNull.validate().isFailure)
        assertTrue(configInvalidHeader.validate().isFailure)
    }

    @Test
    fun testValidateRejectsNonPositiveTimeout() {
        // Rejeita timeout menor ou igual a zero
        val configZero = SshConfig(connectionTimeoutSeconds = 0, password = "pass", authType = AuthType.PASSWORD)
        val configNegative = SshConfig(connectionTimeoutSeconds = -5, password = "pass", authType = AuthType.PASSWORD)

        assertTrue(configZero.validate().isFailure)
        assertTrue(configNegative.validate().isFailure)
    }

    @Test
    fun testToStringNeverLeaksPasswordOrPrivateKey() {
        // Teste de Integridade de Segurança (Data Leak Prevention):
        // Garante que senhas, passphrases e o conteúdo bruto da chave privada NUNCA
        // vazem no retorno de toString() para evitar exposição no Logcat.
        val plainPassword = "SuperSecretPassword123!"
        val plainPassphrase = "MinhaPassphraseSuperSegura"

        val config = SshConfig(
            host = "157.151.2.91",
            port = 22,
            username = "opc",
            authType = AuthType.PRIVATE_KEY,
            privateKeyPem = sampleRsaKey,
            passphrase = plainPassphrase,
            password = plainPassword
        )

        val output = config.toString()

        assertFalse("A senha em texto claro JAMAIS pode aparecer em toString()", output.contains(plainPassword))
        assertFalse("A passphrase em texto claro JAMAIS pode aparecer em toString()", output.contains(plainPassphrase))
        assertFalse("O conteúdo bruto da chave PEM JAMAIS pode aparecer em toString()", output.contains("sample"))
        assertTrue("Deve indicar que a senha está mascarada", output.contains("password=********"))
        assertTrue("Deve indicar que a passphrase está mascarada", output.contains("passphrase=********"))
        assertTrue("Deve indicar a presença da chave com contagem de caracteres", output.contains("CHAVE_PRIVADA_PRESENTE_"))
    }

    @Test
    fun testImmutableCopyPreservesProperties() {
        // Garante que mutações parciais preservem a integridade dos demais campos
        val original = SshConfig(host = "157.151.2.91", port = 22, username = "opc")
        val modified = original.copy(port = 2222, username = "admin")

        assertEquals("157.151.2.91", modified.host)
        assertEquals(2222, modified.port)
        assertEquals("admin", modified.username)
        assertNotEquals(original, modified)
    }
}
