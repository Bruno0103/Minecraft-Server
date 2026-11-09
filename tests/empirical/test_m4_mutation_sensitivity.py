"""
===============================================================================
TESTE FORENSE DE SENSIBILIDADE DO ORÁCULO E ANÁLISE DE MUTAÇÃO ARTIFICIAL (M4)
Arquivo: tests/empirical/test_m4_mutation_sensitivity.py
===============================================================================
Objetivo do Auditor Forense (auditor_m4):
Comprovar empiricamente que a suíte de testes 'tests/test_m4_villager_trade_integrity.py'
NÃO é auto-certificadora, NÃO possui resultados fixos (hardcoded) e reage com falha
imediata (mata o mutante) quando qualquer uma das propriedades críticas da implementação
da Troca Rápida de Aldeão em 'villager.uidx', 'pt_BR.lang' ou 'en_US.lang' é alterada.

Metodologia de Mutação Sintética:
Injetamos 12 mutantes artificiais representativos cobrindo:
- M1: Injeção de Byte Order Mark (UTF-8 BOM).
- M2: Corrupção do namespace 'villager_desktop_dx'.
- M3: Injeção de chave duplicada no AST JSON.
- M4: Corrupção do identificador de tradução do hover ('trade.fake').
- M5: Deslocamento geométrico da seta de troca (offset corrompido).
- M6: Desvio da ação do botão ('button.fake_action' em vez de auto_place).
- M7: Supressão do binding nativo de validação '#trade_button_enabled'.
- M8: Quebra de transparência em repouso (inserção de controle visual opaco).
- M9: Violação da distância de segurança espacial (folga < 80px contra botões externos).
- M10: Inversão da hierarquia de camadas (quick_trade sobrepondo cruz de esgotado).
- M11: Supressão de uma das 14 variantes temáticas de profissão de aldeão.
- M12: Remoção fraudulenta dos créditos de CrisXolt e licença.
===============================================================================
"""

import copy
import unittest
from pathlib import Path
import tests.test_m4_villager_trade_integrity as tm4


class TestVillagerMutationSensitivity(unittest.TestCase):
    """
    Suíte do auditor forense para execução e validação da morte de mutantes artificiais.
    """

    @classmethod
    def setUpClass(cls):
        """Inicializa a suíte original de testes M4."""
        tm4.TestVillagerTradeIntegrity.setUpClass()

    def run_m4_test(self, method_name: str) -> bool:
        """
        Executa diretamente um método de teste na instância de TestVillagerTradeIntegrity.
        Retorna True se o teste lançou AssertionError ou exceção (mutante morto).
        Retorna False se o teste passou (mutante sobreviveu - falha grave do oráculo).
        """
        test_instance = tm4.TestVillagerTradeIntegrity(method_name)
        try:
            method = getattr(test_instance, method_name)
            method()
            return False  # Sobreviveu (não levantou erro)
        except (AssertionError, Exception):
            return True  # Morto com sucesso

    def test_mutant_01_utf8_bom_injection(self):
        """M1: Injeção de BOM no arquivo deve matar test_01."""
        orig_bytes = tm4.TestVillagerTradeIntegrity.raw_bytes
        try:
            tm4.TestVillagerTradeIntegrity.raw_bytes = b"\xef\xbb\xbf" + orig_bytes
            killed = self.run_m4_test("test_01_villager_uidx_exists_and_utf8_without_bom")
            self.assertTrue(killed, "Oráculo falhou: Não detectou injeção de BOM UTF-8!")
        finally:
            tm4.TestVillagerTradeIntegrity.raw_bytes = orig_bytes

    def test_mutant_02_namespace_corruption(self):
        """M2: Corrupção do namespace deve matar test_02."""
        orig_data = copy.deepcopy(tm4.TestVillagerTradeIntegrity.data)
        try:
            tm4.TestVillagerTradeIntegrity.data["namespace"] = "villager_corrompido_dx"
            killed = self.run_m4_test("test_02_villager_uidx_jsonc_syntax")
            self.assertTrue(killed, "Oráculo falhou: Não detectou namespace corrompido!")
        finally:
            tm4.TestVillagerTradeIntegrity.data = copy.deepcopy(orig_data)

    def test_mutant_03_ast_duplicate_key(self):
        """M3: Chave duplicada no JSON AST deve matar test_03."""
        orig_cleaned = tm4.TestVillagerTradeIntegrity.cleaned_json
        try:
            target_str = '"namespace": "villager_desktop_dx",'
            replacement_str = '"namespace": "villager_desktop_dx",\n"namespace": "villager_desktop_dx",'
            tm4.TestVillagerTradeIntegrity.cleaned_json = orig_cleaned.replace(target_str, replacement_str)
            killed = self.run_m4_test("test_03_no_duplicate_keys_ast")
            self.assertTrue(killed, "Oráculo falhou: Não detectou chave duplicada no AST!")
        finally:
            tm4.TestVillagerTradeIntegrity.cleaned_json = orig_cleaned

    def test_mutant_04_tooltip_text_corruption(self):
        """M4: Chave de hover panel incorreta deve matar test_04."""
        orig_data = copy.deepcopy(tm4.TestVillagerTradeIntegrity.data)
        try:
            panel = tm4.TestVillagerTradeIntegrity.data["quick_trade_hover_panel"]
            panel["controls"][0]["hover_text_panel@tooltip_dx.tooltip_panel"]["$hover_panel_text"] = "trade.fake"
            killed = self.run_m4_test("test_04_quick_trade_hover_panel_definition")
            self.assertTrue(killed, "Oráculo falhou: Não detectou chave de tradução inexistente!")
        finally:
            tm4.TestVillagerTradeIntegrity.data = copy.deepcopy(orig_data)

    def test_mutant_05_arrow_offset_displacement(self):
        """M5: Deslocamento incorreto da seta de troca deve matar test_05."""
        orig_data = copy.deepcopy(tm4.TestVillagerTradeIntegrity.data)
        try:
            for c in tm4.TestVillagerTradeIntegrity.data["right_panel"]["controls"]:
                if "arrow_panel" in c:
                    c["arrow_panel"]["offset"] = [90, 40]
            killed = self.run_m4_test("test_05_arrow_panel_geometry_and_hierarchy")
            self.assertTrue(killed, "Oráculo falhou: Não detectou offset adulterado em arrow_panel!")
        finally:
            tm4.TestVillagerTradeIntegrity.data = copy.deepcopy(orig_data)

    def test_mutant_06_button_action_hijack(self):
        """M6: Modificação da ação para button.fake deve matar test_06."""
        orig_data = copy.deepcopy(tm4.TestVillagerTradeIntegrity.data)
        try:
            for c in tm4.TestVillagerTradeIntegrity.data["right_panel"]["controls"]:
                if "arrow_panel" in c:
                    for ac in c["arrow_panel"]["controls"]:
                        if "quick_trade_button" in ac:
                            ac["quick_trade_button"]["button_mappings"][0]["to_button_id"] = "button.fake"
            killed = self.run_m4_test("test_06_quick_trade_button_action_mappings")
            self.assertTrue(killed, "Oráculo falhou: Não detectou ação de botão fraudulenta!")
        finally:
            tm4.TestVillagerTradeIntegrity.data = copy.deepcopy(orig_data)

    def test_mutant_07_validation_binding_removal(self):
        """M7: Supressão do binding nativo #trade_button_enabled deve matar test_07."""
        orig_data = copy.deepcopy(tm4.TestVillagerTradeIntegrity.data)
        try:
            for c in tm4.TestVillagerTradeIntegrity.data["right_panel"]["controls"]:
                if "arrow_panel" in c:
                    for ac in c["arrow_panel"]["controls"]:
                        if "quick_trade_button" in ac:
                            ac["quick_trade_button"]["bindings"] = []
            killed = self.run_m4_test("test_07_quick_trade_button_validation_binding")
            self.assertTrue(killed, "Oráculo falhou: Não detectou ausência de binding #trade_button_enabled!")
        finally:
            tm4.TestVillagerTradeIntegrity.data = copy.deepcopy(orig_data)

    def test_mutant_08_opaque_rest_state(self):
        """M8: Quebra de transparência em repouso deve matar test_08."""
        orig_data = copy.deepcopy(tm4.TestVillagerTradeIntegrity.data)
        try:
            for c in tm4.TestVillagerTradeIntegrity.data["right_panel"]["controls"]:
                if "arrow_panel" in c:
                    for ac in c["arrow_panel"]["controls"]:
                        if "quick_trade_button" in ac:
                            ac["quick_trade_button"]["controls"] = [{"default@common.button": {}}]
            killed = self.run_m4_test("test_08_quick_trade_button_transparent_rest_and_locked_state")
            self.assertTrue(killed, "Oráculo falhou: Não detectou opacidade indevida no botão de repouso!")
        finally:
            tm4.TestVillagerTradeIntegrity.data = copy.deepcopy(orig_data)

    def test_mutant_09_spatial_collision_induction(self):
        """M9: Redução da folga de segurança para < 80px deve matar test_09."""
        orig_data = copy.deepcopy(tm4.TestVillagerTradeIntegrity.data)
        try:
            for c in tm4.TestVillagerTradeIntegrity.data["right_panel"]["controls"]:
                if "arrow_panel" in c:
                    c["arrow_panel"]["offset"] = [150, 35]
            killed = self.run_m4_test("test_09_spatial_coexistence_with_side_tools_tab_and_trade_info")
            self.assertTrue(killed, "Oráculo falhou: Não detectou violação de folga mínima de 80px!")
        finally:
            tm4.TestVillagerTradeIntegrity.data = copy.deepcopy(orig_data)

    def test_mutant_10_layer_inversion(self):
        """M10: Inversão de camada (layer 16 > 14) deve matar test_10."""
        orig_data = copy.deepcopy(tm4.TestVillagerTradeIntegrity.data)
        try:
            for c in tm4.TestVillagerTradeIntegrity.data["right_panel"]["controls"]:
                if "arrow_panel" in c:
                    for ac in c["arrow_panel"]["controls"]:
                        if "quick_trade_button" in ac:
                            ac["quick_trade_button"]["layer"] = 16
            killed = self.run_m4_test("test_10_layer_hierarchy_with_out_of_stock_elements")
            self.assertTrue(killed, "Oráculo falhou: Não detectou violação na hierarquia de camadas!")
        finally:
            tm4.TestVillagerTradeIntegrity.data = copy.deepcopy(orig_data)

    def test_mutant_11_villager_profession_variant_removal(self):
        """M11: Remoção de profissão de aldeão deve matar test_11."""
        orig_data = copy.deepcopy(tm4.TestVillagerTradeIntegrity.data)
        try:
            vbp = tm4.TestVillagerTradeIntegrity.data["villager_background_panel"]
            vbp["controls"] = [c for c in vbp["controls"] if "librarian@villager_textures_dx.villager_librarian" not in c]
            killed = self.run_m4_test("test_11_slots_and_background_variants_integrity")
            self.assertTrue(killed, "Oráculo falhou: Não detectou supressão de variante temática!")
        finally:
            tm4.TestVillagerTradeIntegrity.data = copy.deepcopy(orig_data)

    def test_mutant_12_crisxolt_credit_tampering(self):
        """M12: Adulteração dos créditos de CrisXolt deve matar test_12."""
        orig_raw = tm4.TestVillagerTradeIntegrity.raw_text
        try:
            tm4.TestVillagerTradeIntegrity.raw_text = orig_raw.replace("CrisXolt", "FakeAuthor")
            killed = self.run_m4_test("test_12_portuguese_comments_and_crisxolt_license")
            self.assertTrue(killed, "Oráculo falhou: Não detectou violação dos créditos de CrisXolt!")
        finally:
            tm4.TestVillagerTradeIntegrity.raw_text = orig_raw


if __name__ == "__main__":
    unittest.main()
