package com.neverland.servermanager.e2e

import org.junit.runner.RunWith
import org.junit.runners.Suite

// ==============================================================================
// Never Land Server Manager - Suíte Completa de Testes E2E (Dual Track)
// ==============================================================================
// Esta suíte unificada agrupa os 4 Tiers metodológicos de testes opacos
// orientados a requisitos da aplicação Never Land Server Manager:
//
// - Tier 1: Feature Coverage (26 casos cobrindo os Happy Paths das 5 funções principais)
// - Tier 2: Boundary & Corner Cases (25 casos cobrindo limites, exceções e adversidades)
// - Tier 3: Cross-Feature Combinations (10 casos de concorrência e interações cruzadas)
// - Tier 4: Real-World Scenarios (4 cenários completos de administração ponta a ponta)
//
// Total: 65 casos de teste automatizados independentes e determinísticos.
// ==============================================================================
@RunWith(Suite::class)
@Suite.SuiteClasses(
    Tier1FeatureCoverageTest::class,
    Tier2BoundaryCornerCasesTest::class,
    Tier3CrossFeatureCombinationsTest::class,
    Tier4RealWorldScenariosTest::class
)
class E2ETestSuite
