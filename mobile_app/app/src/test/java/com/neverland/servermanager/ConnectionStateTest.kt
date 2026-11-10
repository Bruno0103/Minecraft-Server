package com.neverland.servermanager

import com.neverland.servermanager.domain.model.ConnectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * ==============================================================================
 * Never Land Server Manager - Testes Unitários da Máquina de Estados (ConnectionState)
 * ==============================================================================
 *
 * Valida a hierarquia de tipos selados de [ConnectionState], suas propriedades
 * utilitárias de conveniência e transições de estado para a interface gráfica.
 * ==============================================================================
 */
class ConnectionStateTest {

    @Test
    fun testDisconnectedStateProperties() {
        val state: ConnectionState = ConnectionState.Disconnected

        assertTrue(state.isDisconnected)
        assertFalse(state.isConnected)
        assertFalse(state.isConnecting)
        assertFalse(state.isError)
    }

    @Test
    fun testConnectingStateProperties() {
        val state: ConnectionState = ConnectionState.Connecting

        assertTrue(state.isConnecting)
        assertFalse(state.isDisconnected)
        assertFalse(state.isConnected)
        assertFalse(state.isError)
    }

    @Test
    fun testConnectedStateProperties() {
        val beforeMillis = System.currentTimeMillis()
        val state: ConnectionState = ConnectionState.Connected(
            host = "157.151.2.91",
            port = 22,
            latencyMs = 45L
        )
        val afterMillis = System.currentTimeMillis()

        assertTrue(state.isConnected)
        assertFalse(state.isDisconnected)
        assertFalse(state.isConnecting)
        assertFalse(state.isError)

        val connected = state as ConnectionState.Connected
        assertEquals("157.151.2.91", connected.host)
        assertEquals(22, connected.port)
        assertEquals(45L, connected.latencyMs)
        assertTrue(connected.connectedAtMillis in beforeMillis..afterMillis)
    }

    @Test
    fun testErrorStateProperties() {
        val originalCause = IOException("Conexão recusada pelo host remoto")
        val beforeMillis = System.currentTimeMillis()
        val state: ConnectionState = ConnectionState.Error(
            message = "Falha ao conectar na porta 22",
            cause = originalCause
        )
        val afterMillis = System.currentTimeMillis()

        assertTrue(state.isError)
        assertFalse(state.isConnected)
        assertFalse(state.isDisconnected)
        assertFalse(state.isConnecting)

        val error = state as ConnectionState.Error
        assertEquals("Falha ao conectar na porta 22", error.message)
        assertEquals(originalCause, error.cause)
        assertTrue(error.timestampMillis in beforeMillis..afterMillis)
    }

    @Test
    fun testErrorStateWithoutCause() {
        val state: ConnectionState = ConnectionState.Error(message = "Timeout de rede")
        val error = state as ConnectionState.Error

        assertEquals("Timeout de rede", error.message)
        assertNull(error.cause)
        assertNotNull(error.timestampMillis)
    }

    @Test
    fun testStateTransitionFlow() {
        // Simulação do ciclo de vida típico da conexão
        var currentState: ConnectionState = ConnectionState.Disconnected
        assertTrue(currentState.isDisconnected)

        // Usuário aciona o botão 'Conectar'
        currentState = ConnectionState.Connecting
        assertTrue(currentState.isConnecting)

        // Conexão e handshake concluídos
        currentState = ConnectionState.Connected(host = "157.151.2.91", port = 22, latencyMs = 38L)
        assertTrue(currentState.isConnected)

        // Falha transitória de rede
        currentState = ConnectionState.Error("Timeout de socket após 15s")
        assertTrue(currentState.isError)

        // Reset manual pelo usuário
        currentState = ConnectionState.Disconnected
        assertTrue(currentState.isDisconnected)
    }
}
