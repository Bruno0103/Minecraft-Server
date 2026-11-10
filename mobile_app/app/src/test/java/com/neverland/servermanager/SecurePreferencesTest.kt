package com.neverland.servermanager

import com.neverland.servermanager.data.security.InMemorySecureStorage
import com.neverland.servermanager.data.security.SecurePreferences
import com.neverland.servermanager.domain.model.AuthType
import com.neverland.servermanager.domain.model.SshConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * ==============================================================================
 * Never Land Server Manager - Testes Unitários de Armazenamento Seguro (JVM)
 * ==============================================================================
 *
 * Valida de forma determinística na JVM (Desktop JUnit) as operações de persistência,
 * carregamento, limpeza e verificação de credenciais através do [SecurePreferences]
 * utilizando o double de teste [InMemorySecureStorage].
 * ==============================================================================
 */
class SecurePreferencesTest {

    private lateinit var securePrefs: SecurePreferences

    @Before
    fun setUp() {
        SecurePreferences.resetInstanceForTesting()
        // Cria a instância de teste acoplada ao storage em memória puro
        securePrefs = SecurePreferences.createForTesting(InMemorySecureStorage())
    }

    @After
    fun tearDown() {
        SecurePreferences.resetInstanceForTesting()
    }

    @Test
    fun testInitialDefaultsWhenEmpty() {
        // Ao inicializar sem dados prévios, deve retornar os padrões seguros do Never Land
        assertFalse("Não deve haver credenciais salvas inicialmente", securePrefs.hasSavedCredentials())

        val config = securePrefs.loadSshConfig()
        assertEquals("157.151.2.91", config.host)
        assertEquals(22, config.port)
        assertEquals("opc", config.username)
        assertEquals(AuthType.PRIVATE_KEY, config.authType)
        assertNull(config.privateKeyPem)
        assertNull(config.password)
        assertNull(config.passphrase)
    }

    @Test
    fun testSaveAndLoadConfigurationWithPassword() {
        // Salva e recupera uma configuração com autenticação por senha
        val original = SshConfig(
            host = "never-land.rocks",
            port = 2222,
            username = "admin_opc",
            authType = AuthType.PASSWORD,
            password = "SuperPassword2026!"
        )

        securePrefs.saveSshConfig(original)

        assertTrue("Deve registrar presença de credenciais salvas", securePrefs.hasSavedCredentials())

        val loaded = securePrefs.loadSshConfig()
        assertEquals("never-land.rocks", loaded.host)
        assertEquals(2222, loaded.port)
        assertEquals("admin_opc", loaded.username)
        assertEquals(AuthType.PASSWORD, loaded.authType)
        assertEquals("SuperPassword2026!", loaded.password)
        assertNull(loaded.privateKeyPem)
        assertNull(loaded.passphrase)
    }

    @Test
    fun testSaveAndLoadConfigurationWithPrivateKeyAndPassphrase() {
        // Salva e recupera configuração com par de chaves e passphrase
        val pemKey = """
            -----BEGIN OPENSSH PRIVATE KEY-----
            b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAA...sample...
            -----END OPENSSH PRIVATE KEY-----
        """.trimIndent()

        val original = SshConfig(
            host = "157.151.2.91",
            port = 22,
            username = "opc",
            authType = AuthType.PRIVATE_KEY,
            privateKeyPem = pemKey,
            passphrase = "MinhaPassphraseSegura"
        )

        securePrefs.saveSshConfig(original)

        assertTrue(securePrefs.hasSavedCredentials())

        val loaded = securePrefs.loadSshConfig()
        assertEquals("157.151.2.91", loaded.host)
        assertEquals(22, loaded.port)
        assertEquals("opc", loaded.username)
        assertEquals(AuthType.PRIVATE_KEY, loaded.authType)
        assertEquals(pemKey, loaded.privateKeyPem)
        assertEquals("MinhaPassphraseSegura", loaded.passphrase)
        assertNull(loaded.password)
    }

    @Test
    fun testClearCredentialsPreservesConnectionSettings() {
        // Limpar credenciais deve remover senha e chave privada, preservando host e porta
        val original = SshConfig(
            host = "custom.neverland.net",
            port = 2200,
            username = "operator",
            authType = AuthType.PASSWORD,
            password = "TemporaryPassword123"
        )

        securePrefs.saveSshConfig(original)
        assertTrue(securePrefs.hasSavedCredentials())

        // Executa a limpeza segura
        securePrefs.clearCredentials()

        assertFalse("Credenciais devem ter sido removidas", securePrefs.hasSavedCredentials())

        val loaded = securePrefs.loadSshConfig()
        assertEquals("custom.neverland.net", loaded.host)
        assertEquals(2200, loaded.port)
        assertEquals("operator", loaded.username)
        assertNull("A senha deve ser null após clearCredentials", loaded.password)
        assertNull("A chave deve ser null após clearCredentials", loaded.privateKeyPem)
    }

    @Test
    fun testOverwriteCredentialsFromPasswordToPrivateKey() {
        // Transição de método de autenticação
        val configWithPass = SshConfig(authType = AuthType.PASSWORD, password = "Senha1")
        securePrefs.saveSshConfig(configWithPass)
        assertEquals("Senha1", securePrefs.loadSshConfig().password)

        val configWithKey = SshConfig(
            authType = AuthType.PRIVATE_KEY,
            privateKeyPem = "-----BEGIN RSA PRIVATE KEY-----\nteste\n-----END RSA PRIVATE KEY-----"
        )
        securePrefs.saveSshConfig(configWithKey)

        val loaded = securePrefs.loadSshConfig()
        assertEquals(AuthType.PRIVATE_KEY, loaded.authType)
        assertNull("A senha anterior deve ter sido removida", loaded.password)
        assertTrue(loaded.privateKeyPem!!.contains("BEGIN RSA PRIVATE KEY"))
    }
}
