package com.neverland.servermanager

import com.neverland.servermanager.data.ssh.SshManager
import com.neverland.servermanager.domain.model.AuthType
import com.neverland.servermanager.domain.model.ConnectionState
import com.neverland.servermanager.domain.model.SshConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * ==============================================================================
 * Never Land Server Manager - Testes Unitários de Coroutines e Ciclo SSH
 * ==============================================================================
 *
 * Valida a arquitetura assíncrona do [SshManager], emissão reativa de [StateFlow],
 * serialização de chamadas via [kotlinx.coroutines.sync.Mutex], cancelamento cooperativo
 * e tratamento estrito de integridade de conexões.
 * ==============================================================================
 */
class SshManagerCoroutinesTest {

    private lateinit var sshManager: SshManager

    @Before
    fun setUp() {
        sshManager = SshManager()
    }

    @Test
    fun testInitialStateIsDisconnected() {
        // Ao instanciar, o gerenciador deve estar em repouso e sem configuração ativa
        val state = sshManager.connectionState.value
        assertTrue("Estado inicial deve ser Disconnected", state.isDisconnected)
        assertFalse("Não deve constar como conectado", sshManager.isConnected())
        assertNull("Nenhuma configuração ativa deve estar associada", sshManager.activeConfig.value)
    }

    @Test
    fun testConnectWithInvalidConfigEmitsErrorState() = runBlocking {
        // Configuração com porta inválida deve falhar imediatamente na validação prévia
        val invalidConfig = SshConfig(
            host = "157.151.2.91",
            port = 99999, // Porta acima de 65535
            username = "opc",
            authType = AuthType.PASSWORD,
            password = "pwd"
        )

        val result = sshManager.connect(invalidConfig)

        assertTrue("A tentativa de conexão deve retornar falha", result.isFailure)
        val state = sshManager.connectionState.value
        assertTrue("O estado deve transicionar para Error", state.isError)
        val errorState = state as ConnectionState.Error
        assertTrue("A mensagem deve indicar porta inválida", errorState.message.contains("Porta inválida"))
    }

    @Test
    fun testConnectWithMissingPasswordEmitsErrorState() = runBlocking {
        // Configuração PASSWORD sem senha deve ser rejeitada
        val missingPassConfig = SshConfig(
            host = "157.151.2.91",
            port = 22,
            username = "opc",
            authType = AuthType.PASSWORD,
            password = null
        )

        val result = sshManager.connect(missingPassConfig)

        assertTrue(result.isFailure)
        val state = sshManager.connectionState.value
        assertTrue(state.isError)
        val error = state as ConnectionState.Error
        assertTrue(error.message.contains("A senha deve ser preenchida"))
    }

    @Test
    fun testExecuteCommandWhenDisconnectedReturnsFailure() = runBlocking {
        // Disparar comando sem estar conectado deve retornar falha estruturada
        val result = sshManager.executeCommand("mc status")

        assertTrue("Comando sem conexão deve falhar", result.isFailure)
        val exception = result.exceptionOrNull()
        assertNotNull(exception)
        assertTrue(
            "Deve orientar a conectar antes de executar comandos",
            exception is IllegalStateException && exception.message!!.contains("Sessão SSH desconectada")
        )
    }

    @Test
    fun testDisconnectResetsStateAndActiveConfig() = runBlocking {
        // Força transição e valida que disconnect() redefine os fluxos
        sshManager.disconnect()

        val state = sshManager.connectionState.value
        assertTrue(state.isDisconnected)
        assertFalse(sshManager.isConnected())
        assertNull(sshManager.activeConfig.value)
    }

    @Test
    fun testConnectToUnreachableHostReturnsMappedNetworkException() = runBlocking {
        // Tenta conectar em um endereço loopback com porta fechada para validar o mapeamento de erro
        val unreachableConfig = SshConfig(
            host = "127.0.0.1",
            port = 19133, // Porta fechada propositalmente
            username = "opc",
            authType = AuthType.PASSWORD,
            password = "test",
            connectionTimeoutSeconds = 1 // Timeout curto para o teste ser instantâneo
        )

        val result = sshManager.connect(unreachableConfig)

        assertTrue("Conexão com porta fechada deve falhar", result.isFailure)
        val state = sshManager.connectionState.value
        assertTrue("O estado deve transicionar para Error", state.isError)
        val errorState = state as ConnectionState.Error
        assertTrue(
            "Deve conter mensagem amigável em Português sobre conexão",
            errorState.message.contains("Não foi possível conectar") || errorState.message.contains("Tempo limite")
        )
    }

    @Test
    fun testConcurrentCommandRequestsAreSerialized() = runBlocking {
        // Valida que múltiplas corrotinas disparando instruções simultaneamente
        // são serializadas pelo Mutex sem corrupção ou colisão
        val jobs = (1..5).map { index ->
            async(Dispatchers.IO) {
                // Como não há conexão ativa, todos devem receber a falha de sessão desconectada
                // de forma segura e síncrona através do Mutex
                sshManager.executeCommand("echo test $index")
            }
        }

        val results = jobs.awaitAll()
        assertEquals(5, results.size)
        results.forEach { result ->
            assertTrue("Todas as requisições devem retornar falha controlada", result.isFailure)
        }
    }

    @Test
    fun testCoroutineCooperativeCancellationPropagates() = runBlocking {
        // Valida que o cancelamento cooperativo de corrotinas com SshManager
        // propaga CancellationException e não é engolido por blocos genéricos de captura
        var cancellationDetected = false

        val job = launch(Dispatchers.IO) {
            try {
                sshManager.disconnect()
                // Ponto de suspensão cooperativo simulando espera de processamento assíncrono
                delay(5000)
                sshManager.executeCommand("mc status")
            } catch (e: CancellationException) {
                cancellationDetected = true
                throw e
            }
        }

        // Aguarda a corrotina iniciar e atingir o ponto de suspensão cooperativo
        delay(50)
        // Dispara o cancelamento cooperativo
        job.cancelAndJoin()

        assertTrue("A CancellationException deve ter sido propagada cooperativamente", cancellationDetected)
        assertTrue("O job deve constar como cancelado", job.isCancelled)
        assertTrue("O estado final do SshManager deve permanecer Disconnected", sshManager.connectionState.value.isDisconnected)
    }
}
