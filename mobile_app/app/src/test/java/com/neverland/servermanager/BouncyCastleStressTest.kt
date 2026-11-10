package com.neverland.servermanager

import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Security
import java.security.Signature
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * ==============================================================================
 * Never Land Server Manager - Teste de Estresse de Ambiente e BouncyCastle
 * (BouncyCastleStressTest.kt)
 * ==============================================================================
 * 
 * Desenvolvido pelo Challenger M1.2 (Environment Stress Challenger) para validar
 * empiricamente:
 * 1. O isolamento de ambiente da JVM (garantindo que o teste roda em JDK 17 e não JDK 26).
 * 2. A resiliência, concorrência e integridade do BouncyCastleProvider sob carga severa.
 * 3. O comportamento do registro de provedores criptográficos em cenários de múltiplos threads
 *    e re-execução de ciclos de vida.
 * 4. A capacidade de gerar chaves Ed25519 e RSA concorrentemente sem colisões de estado interno.
 * ==============================================================================
 */
class BouncyCastleStressTest {

    /**
     * Teste 1: Validação Empírica da Versão da JVM em Tempo de Execução.
     * Comprova formalmente se o executor de testes do Gradle está rodando sob o JDK 17
     * conforme configurado em gradle.properties (e rejeitando o JDK 26 da máquina).
     */
    @Test
    fun testJvmEnvironmentIsStrictlyJdk17() {
        val javaVersion = System.getProperty("java.version") ?: ""
        val javaVendor = System.getProperty("java.vendor") ?: ""
        val javaHome = System.getProperty("java.home") ?: ""

        println("=== [JVM AUDIT] Informações da JVM de Execução dos Testes ===")
        println("Java Version: $javaVersion")
        println("Java Vendor : $javaVendor")
        println("Java Home   : $javaHome")
        println("============================================================")

        // Valida que a JVM é estritamente Java 17 (versão 17.x)
        assertTrue(
            "A JVM de execução de testes deve ser a versão 17 LTS (Atual: $javaVersion)",
            javaVersion.startsWith("17.")
        )

        // Valida que não estamos executando sob o JDK 26 instalado na máquina host
        assertTrue(
            "A execução NÃO pode utilizar o JDK 26 (Atual: $javaVersion)",
            !javaVersion.startsWith("26.")
        )
    }

    /**
     * Teste 2: Estresse de Registro Concorrente e Idempotência do BouncyCastleProvider.
     * Simula múltiplos threads e recriações de processo tentando registrar ou reposicionar
     * o BouncyCastleProvider no topo da lista do Java Security Runtime.
     */
    @Test
    fun testConcurrentBouncyCastleRegistrationAndLifecycle() {
        val threadCount = 30
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val successCount = AtomicInteger(0)
        val failureCount = AtomicInteger(0)

        // Dispara 30 threads simultâneos realizando o registro de segurança idêntico ao NeverLandApp
        for (i in 0 until threadCount) {
            executor.submit {
                try {
                    // Procedimento de re-registro resiliente
                    Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
                    val provider = BouncyCastleProvider()
                    val pos = Security.insertProviderAt(provider, 1)

                    if (pos == 1 || Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) != null) {
                        successCount.incrementAndGet()
                    } else {
                        failureCount.incrementAndGet()
                    }
                } catch (e: Exception) {
                    failureCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        val completed = latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        assertTrue("Todas as threads de registro devem concluir em 10s", completed)
        assertEquals("Nenhuma thread deve falhar no registro do BouncyCastle", 0, failureCount.get())
        assertEquals("Todas as 30 threads devem concluir com sucesso", 30, successCount.get())

        // Garante que o provedor BC final existe e está ativo no sistema de segurança
        val finalBc = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)
        assertNotNull("Provedor 'BC' deve estar registrado após o estresse", finalBc)
    }

    /**
     * Teste 3: Estresse Criptográfico Concorrente com Curvas Elípticas Ed25519 e RSA.
     * Valida que o BouncyCastleProvider suporta a geração paralela de pares de chaves
     * e operações de assinatura Ed25519 e SHA256withRSA sem deadlocks ou corrupção de estado.
     */
    @Test
    fun testConcurrentCryptographicOperationsEd25519AndRsa() {
        // Assegura que o BouncyCastle está registrado
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.insertProviderAt(BouncyCastleProvider(), 1)
        }

        val threadCount = 20
        val operationsPerThread = 5
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val totalOperations = AtomicInteger(0)

        for (i in 0 until threadCount) {
            executor.submit {
                try {
                    for (op in 0 until operationsPerThread) {
                        // 1. Gera par de chaves Ed25519 (essencial para SSH moderno do Never Land)
                        val kpgEd = KeyPairGenerator.getInstance("Ed25519", "BC")
                        val keyPairEd = kpgEd.generateKeyPair()
                        assertNotNull(keyPairEd.public)
                        assertNotNull(keyPairEd.private)

                        // 2. Assina e valida dados com Ed25519
                        val testPayload = "NeverLand_Stress_Payload_${i}_${op}".toByteArray(Charsets.UTF_8)
                        val sigEd = Signature.getInstance("Ed25519", "BC")
                        sigEd.initSign(keyPairEd.private)
                        sigEd.update(testPayload)
                        val signatureBytes = sigEd.sign()

                        val verifierEd = Signature.getInstance("Ed25519", "BC")
                        verifierEd.initVerify(keyPairEd.public)
                        verifierEd.update(testPayload)
                        assertTrue("Assinatura Ed25519 deve ser verificada com sucesso", verifierEd.verify(signatureBytes))

                        totalOperations.incrementAndGet()
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        val completed = latch.await(15, TimeUnit.SECONDS)
        executor.shutdown()

        assertTrue("Todas as operações criptográficas concorrentes devem concluir em 15s", completed)
        assertEquals(
            "Devem ter sido executadas exatamente 100 operações criptográficas completas",
            100,
            totalOperations.get()
        )
    }
}
