package com.neverland.servermanager

import com.neverland.servermanager.ui.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ==============================================================================
 * Never Land Server Manager - Testes Unitários de Arquitetura e Rotas (ArchitectureTest.kt)
 * ==============================================================================
 * 
 * Valida a consistência das rotas e destinos do grafo de navegação definidos no Marco 1.
 * Garante que cada tela possua rota não vazia, título legível e ícone associado.
 * ==============================================================================
 */
class ArchitectureTest {

    @Test
    fun testNavigationScreensCompleteness() {
        // Valida que existem exatamente 4 destinos fundamentais no aplicativo
        val items = Screen.items
        assertEquals("O Scaffold principal deve conter 4 seções fundamentais", 4, items.size)

        // Valida rotas específicas
        val routes = items.map { it.route }.toSet()
        assertTrue("Deve conter a rota 'dashboard'", routes.contains("dashboard"))
        assertTrue("Deve conter a rota 'console'", routes.contains("console"))
        assertTrue("Deve conter a rota 'players'", routes.contains("players"))
        assertTrue("Deve conter a rota 'settings'", routes.contains("settings"))

        // Valida propriedades de cada Screen
        for (screen in items) {
            assertTrue("Rota não pode ser vazia", screen.route.isNotBlank())
            assertTrue("Título não pode ser vazio", screen.title.isNotBlank())
            assertNotNull("Ícone deve estar definido", screen.icon)
        }
    }
}
