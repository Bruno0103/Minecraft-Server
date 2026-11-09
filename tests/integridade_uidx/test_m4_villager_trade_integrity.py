"""
===============================================================================
SUÍTE DE TESTES AUTOMATIZADOS DE INTEGRIDADE DO MARCO 4 (M4)
Arquivo: tests/test_m4_villager_trade_integrity.py
===============================================================================
Objetivo:
Validar de forma automatizada, sintática, semântica e geométrica a implementação
da Troca Expressa Contínua de Aldeões no Resource Pack 'Colourful Containers BE: Mixed'
(ColourfulC), assegurando:
1. Existência física do arquivo e ausência de BOM (UTF-8 puro).
2. Conformidade sintática estrita JSONC e namespace 'villager_desktop_dx'.
3. Ausência de chaves duplicadas no AST (Abstract Syntax Tree).
4. Definição correta do painel de hover flutuante 'quick_trade_hover_panel' e
   existência das strings de localização 'trade.tradeAll' em pt_BR e en_US.
5. Geometria, posicionamento e hierarquia do painel da seta 'arrow_panel'.
6. Mapeamento de ações do botão de troca rápida para 'button.container_auto_place'
   no slot 'trade2_result_item[0]'.
7. Injeção obrigatória do binding de validação nativo '#trade_button_enabled'.
8. Estados de repouso e bloqueio 100% transparentes ('miscellaneous_dx.empty_panel')
   para preservar a arte temática de madeira rústica de CrisXolt.
9. Coexistência espacial harmoniosa e distância segura contra colisão com
   'trade_info_button' e a Aba Lateral Compacta externa.
10. Hierarquia de camadas (layer 4 do quick trade < layer 14 de out_of_stock <
    layer 15 de hover_button do aviso de esgotamento).
11. Preservação integral dos slots de ingredientes/resultado e das 14 variantes
    de profissão de aldeões no painel de fundo.
12. Preservação do cabeçalho de licença de CrisXolt e documentação explicativa
    extensa em Português do Brasil.
===============================================================================
"""

import json
import re
import unittest
from pathlib import Path
from typing import Dict, Any, List, Optional


# Diretório base do Resource Pack Colourful Containers BE: Mixed
RESOURCE_PACK_DIR = Path(
    r"C:\Users\Bruno\AppData\Roaming\Minecraft Bedrock\Users\Shared\games\com.mojang\resource_packs\ColourfulC"
)

# Caminho do arquivo UIDX do contêiner de aldeão
VILLAGER_UIDX_PATH = (
    RESOURCE_PACK_DIR / "vdx" / "ui_data" / "container" / "villager.uidx"
)

# Caminhos dos arquivos de linguagem do pacote
LANG_PT_BR_PATH = RESOURCE_PACK_DIR / "texts" / "pt_BR.lang"
LANG_EN_US_PATH = RESOURCE_PACK_DIR / "texts" / "en_US.lang"


def strip_jsonc_comments_and_trailing_commas(text: str) -> str:
    """
    Remove comentários C-style (// linha e /* bloco */) e vírgulas sobressalentes
    (trailing commas) de strings JSONC / UIDX, preservando com absoluta precisão
    o conteúdo interno de strings literais e escapes de aspas.
    """
    result = []
    in_string = False
    escape = False
    i = 0
    length = len(text)

    while i < length:
        char = text[i]

        # Se estamos dentro de uma string literal delimitada por aspas duplas
        if in_string:
            result.append(char)
            if escape:
                escape = False
            elif char == "\\":
                escape = True
            elif char == '"':
                in_string = False
            i += 1
            continue

        # Início de uma nova string literal
        if char == '"':
            in_string = True
            result.append(char)
            i += 1
            continue

        # Comentário de bloco /* ... */
        if char == "/" and i + 1 < length and text[i + 1] == "*":
            i += 2
            while i + 1 < length and not (text[i] == "*" and text[i + 1] == "/"):
                if text[i] == "\n":
                    result.append("\n")
                i += 1
            i += 2
            continue

        # Comentário de linha // ...
        if char == "/" and i + 1 < length and text[i + 1] == "/":
            i += 2
            while i < length and text[i] not in ("\r", "\n"):
                i += 1
            continue

        result.append(char)
        i += 1

    cleaned = "".join(result)
    # Remove trailing commas antes do fechamento de arrays ou objetos
    cleaned = re.sub(r",\s*([\]}])", r"\1", cleaned)
    return cleaned


class TestVillagerTradeIntegrity(unittest.TestCase):
    """
    Suíte completa de 12 testes unitários de integridade para a Troca Rápida de Aldeão (M4).
    """

    @classmethod
    def setUpClass(cls):
        """Carrega os dados em disco antes da execução dos testes."""
        cls.raw_bytes = VILLAGER_UIDX_PATH.read_bytes()
        cls.raw_text = cls.raw_bytes.decode("utf-8")
        cls.cleaned_json = strip_jsonc_comments_and_trailing_commas(cls.raw_text)
        cls.data = json.loads(cls.cleaned_json)

    def test_01_villager_uidx_exists_and_utf8_without_bom(self):
        """1. Valida existência física do arquivo villager.uidx e ausência de Byte Order Mark (BOM)."""
        self.assertTrue(
            VILLAGER_UIDX_PATH.is_file(),
            f"O arquivo {VILLAGER_UIDX_PATH} não foi encontrado no disco!",
        )
        self.assertFalse(
            self.raw_bytes.startswith(b"\xef\xbb\xbf"),
            "O arquivo villager.uidx contém BOM UTF-8 (\\xef\\xbb\\xbf), violando o padrão Mojang!",
        )
        self.assertGreater(
            len(self.raw_bytes),
            5000,
            "O arquivo villager.uidx possui tamanho menor do que o esperado.",
        )

    def test_02_villager_uidx_jsonc_syntax(self):
        """2. Valida decodificação JSON após higienização JSONC e confirma namespace correto."""
        self.assertIsInstance(
            self.data,
            dict,
            "O conteúdo decodificado de villager.uidx não é um dicionário JSON válido!",
        )
        self.assertEqual(
            self.data.get("namespace"),
            "villager_desktop_dx",
            "O namespace raiz de villager.uidx deve ser 'villager_desktop_dx'!",
        )

    def test_03_no_duplicate_keys_ast(self):
        """3. Garante via object_pairs_hook que nenhuma chave duplicada exista no AST de villager.uidx."""
        duplicate_keys = []

        def check_duplicates_hook(pairs):
            seen = set()
            d = {}
            for key, val in pairs:
                if key in seen:
                    duplicate_keys.append(key)
                seen.add(key)
                d[key] = val
            return d

        json.loads(self.cleaned_json, object_pairs_hook=check_duplicates_hook)
        self.assertEqual(
            duplicate_keys,
            [],
            f"Chaves duplicadas detectadas no AST de villager.uidx: {duplicate_keys}",
        )

    def test_04_quick_trade_hover_panel_definition(self):
        """4. Valida a definição de quick_trade_hover_panel, o tooltip e as chaves nos arquivos .lang."""
        self.assertIn(
            "quick_trade_hover_panel",
            self.data,
            "O controle 'quick_trade_hover_panel' não foi encontrado na raiz de villager.uidx!",
        )
        panel = self.data["quick_trade_hover_panel"]
        self.assertEqual(panel.get("type"), "panel")
        self.assertEqual(panel.get("size"), [28, 21])

        # Validação do componente tooltip interno
        controls = panel.get("controls", [])
        self.assertTrue(
            any("hover_text_panel@tooltip_dx.tooltip_panel" in c for c in controls),
            "quick_trade_hover_panel deve instanciar 'hover_text_panel@tooltip_dx.tooltip_panel'!",
        )

        tooltip_config = None
        for c in controls:
            if "hover_text_panel@tooltip_dx.tooltip_panel" in c:
                tooltip_config = c["hover_text_panel@tooltip_dx.tooltip_panel"]
                break

        self.assertIsNotNone(tooltip_config)
        self.assertEqual(tooltip_config.get("$hover_panel_text"), "trade.tradeAll")
        self.assertEqual(tooltip_config.get("$hover_anchor_from"), "top_middle")
        self.assertEqual(tooltip_config.get("$hover_anchor_to"), "bottom_middle")
        self.assertEqual(tooltip_config.get("$hover_text_offset"), [0, -4])

        # Validação das chaves de tradução nos arquivos .lang
        self.assertTrue(LANG_PT_BR_PATH.is_file(), f"{LANG_PT_BR_PATH} não encontrado!")
        pt_content = LANG_PT_BR_PATH.read_text(encoding="utf-8-sig")
        self.assertIn(
            "trade.tradeAll=Trocar Tudo",
            pt_content,
            "A chave 'trade.tradeAll=Trocar Tudo' não foi encontrada em texts/pt_BR.lang!",
        )

        self.assertTrue(LANG_EN_US_PATH.is_file(), f"{LANG_EN_US_PATH} não encontrado!")
        en_content = LANG_EN_US_PATH.read_text(encoding="utf-8")
        self.assertIn(
            "trade.tradeAll=Trade All",
            en_content,
            "A chave 'trade.tradeAll=Trade All' não foi encontrada em texts/en_US.lang!",
        )

    def test_05_arrow_panel_geometry_and_hierarchy(self):
        """5. Valida geometria, offset [82, 35], tamanho [28, 21] e hierarquia de arrow_panel em right_panel."""
        right_panel = self.data.get("right_panel", {})
        rp_controls = right_panel.get("controls", [])

        arrow_panel = None
        for c in rp_controls:
            if "arrow_panel" in c:
                arrow_panel = c["arrow_panel"]
                break

        self.assertIsNotNone(arrow_panel, "arrow_panel não encontrado dentro de right_panel!")
        self.assertEqual(arrow_panel.get("type"), "panel")
        self.assertEqual(arrow_panel.get("size"), [28, 21])
        self.assertEqual(arrow_panel.get("offset"), [82, 35])
        self.assertEqual(arrow_panel.get("anchor_from"), "top_left")
        self.assertEqual(arrow_panel.get("anchor_to"), "top_left")

        # Verifica se contém quick_trade_button e arrow_panel@trade2.trade_selector_stack_panel
        ap_controls = arrow_panel.get("controls", [])
        has_quick_trade = any("quick_trade_button" in c for c in ap_controls)
        has_selector = any("arrow_panel@trade2.trade_selector_stack_panel" in c for c in ap_controls)

        self.assertTrue(has_quick_trade, "quick_trade_button deve estar contido em arrow_panel!")
        self.assertTrue(has_selector, "arrow_panel@trade2.trade_selector_stack_panel deve estar em arrow_panel!")

    def test_06_quick_trade_button_action_mappings(self):
        """6. Valida property_bag para trade2_result_item[0] e button_mappings para button.container_auto_place."""
        qtb = self._get_quick_trade_button()
        self.assertEqual(qtb.get("type"), "button")
        self.assertEqual(qtb.get("size"), [28, 21])

        prop_bag = qtb.get("property_bag", {})
        self.assertEqual(prop_bag.get("#collection_name"), "trade2_result_item")
        self.assertEqual(prop_bag.get("#collection_index"), 0)

        mappings = qtb.get("button_mappings", [])
        select_map = next((m for m in mappings if m.get("from_button_id") == "button.menu_select"), None)
        ok_map = next((m for m in mappings if m.get("from_button_id") == "button.menu_ok"), None)

        self.assertIsNotNone(select_map, "Mapeamento para 'button.menu_select' não encontrado!")
        self.assertEqual(select_map.get("to_button_id"), "button.container_auto_place")
        self.assertEqual(select_map.get("mapping_type"), "pressed")

        self.assertIsNotNone(ok_map, "Mapeamento para 'button.menu_ok' não encontrado!")
        self.assertEqual(ok_map.get("to_button_id"), "button.container_auto_place")
        self.assertEqual(ok_map.get("mapping_type"), "pressed")

    def test_07_quick_trade_button_validation_binding(self):
        """7. Valida binding '#trade_button_enabled' com override '#enabled' em quick_trade_button."""
        qtb = self._get_quick_trade_button()
        bindings = qtb.get("bindings", [])

        trade_binding = next(
            (b for b in bindings if b.get("binding_name") == "#trade_button_enabled"),
            None,
        )
        self.assertIsNotNone(
            trade_binding,
            "Binding de validação '#trade_button_enabled' ausente no quick_trade_button!",
        )
        self.assertEqual(
            trade_binding.get("binding_name_override"),
            "#enabled",
            "O binding_name_override deve ser '#enabled' para desabilitar o botão sem estoque!",
        )

    def test_08_quick_trade_button_transparent_rest_and_locked_state(self):
        """8. Valida transparência de repouso e bloqueio (empty_panel) e instâncias de hover/pressed."""
        qtb = self._get_quick_trade_button()

        self.assertEqual(qtb.get("default_control"), "default")
        self.assertEqual(qtb.get("hover_control"), "hover")
        self.assertEqual(qtb.get("pressed_control"), "pressed")
        self.assertEqual(qtb.get("locked_control"), "locked")

        controls = qtb.get("controls", [])
        has_default_empty = any("default@miscellaneous_dx.empty_panel" in c for c in controls)
        has_locked_empty = any("locked@miscellaneous_dx.empty_panel" in c for c in controls)
        has_hover_panel = any("hover@villager_desktop_dx.quick_trade_hover_panel" in c for c in controls)
        has_pressed_panel = any("pressed@villager_desktop_dx.quick_trade_hover_panel" in c for c in controls)

        self.assertTrue(
            has_default_empty,
            "Estado 'default' deve instanciar 'miscellaneous_dx.empty_panel' para preservar pixel art!",
        )
        self.assertTrue(
            has_locked_empty,
            "Estado 'locked' deve instanciar 'miscellaneous_dx.empty_panel' quando desabilitado!",
        )
        self.assertTrue(
            has_hover_panel,
            "Estado 'hover' deve instanciar 'villager_desktop_dx.quick_trade_hover_panel'!",
        )
        self.assertTrue(
            has_pressed_panel,
            "Estado 'pressed' deve instanciar 'villager_desktop_dx.quick_trade_hover_panel'!",
        )

    def test_09_spatial_coexistence_with_side_tools_tab_and_trade_info(self):
        """9. Valida que a seta de troca [82, 35] não colide com trade_info_button [24, 92] e a aba lateral."""
        # 1. Geometria da seta interna
        arrow_panel = self._get_arrow_panel()
        arrow_offset = arrow_panel.get("offset", [0, 0])
        arrow_size = arrow_panel.get("size", [0, 0])
        self.assertEqual(arrow_offset, [82, 35])
        self.assertEqual(arrow_size, [28, 21])

        # 2. Localização do trade_info_button
        right_panel = self.data.get("right_panel", {})
        teb_panel = next((c["trade_extra_buttons"] for c in right_panel.get("controls", []) if "trade_extra_buttons" in c), None)
        self.assertIsNotNone(teb_panel, "Painel 'trade_extra_buttons' não encontrado!")

        info_btn = next((c["trade_info_button@button_dx.main_black_button"] for c in teb_panel.get("controls", []) if "trade_info_button@button_dx.main_black_button" in c), None)
        self.assertIsNotNone(info_btn, "trade_info_button não encontrado em trade_extra_buttons!")

        self.assertEqual(info_btn.get("offset"), [24, 92])
        self.assertEqual(info_btn.get("size"), [20, 20])
        self.assertEqual(info_btn.get("anchor_from"), "top_right")

        # 3. Cálculo de folga horizontal absoluta:
        # right_panel width = 176.
        # Seta termina em X = 82 + 28 = 110 (relativo a right_panel).
        # Coluna externa direita está em 176 + 24 = 200.
        # Folga = 200 - 110 = 90 pixels livres!
        right_panel_width = right_panel.get("size", [176, 166])[0]
        arrow_right_edge = arrow_offset[0] + arrow_size[0]
        external_column_x = right_panel_width + info_btn.get("offset")[0]
        gap_x = external_column_x - arrow_right_edge

        self.assertGreaterEqual(
            gap_x,
            80,
            f"A folga horizontal entre a seta e a coluna externa deve ser >= 80px (medido: {gap_x}px)!",
        )

    def test_10_layer_hierarchy_with_out_of_stock_elements(self):
        """10. Valida hierarquia de camadas: quick_trade_button (4) < out_of_stock (14) < hover_button (15)."""
        qtb = self._get_quick_trade_button()
        qtb_layer = qtb.get("layer")
        self.assertEqual(qtb_layer, 4, "quick_trade_button deve estar na layer 4!")

        apc = self.data.get("arrow_panel_content", {})
        apc_controls = apc.get("controls", [])

        out_of_stock = next((c["out_of_stock@villager_textures_dx.out_of_stock"] for c in apc_controls if "out_of_stock@villager_textures_dx.out_of_stock" in c), None)
        self.assertIsNotNone(out_of_stock, "out_of_stock não encontrado em arrow_panel_content!")
        oos_layer = out_of_stock.get("layer")
        self.assertEqual(oos_layer, 14, "out_of_stock deve estar na layer 14!")

        hover_btn = next((c["hover_button"] for c in apc_controls if "hover_button" in c), None)
        self.assertIsNotNone(hover_btn, "hover_button não encontrado em arrow_panel_content!")
        hover_layer = hover_btn.get("layer")
        self.assertEqual(hover_layer, 15, "hover_button deve estar na layer 15!")

        # Validação estrita da precedência
        self.assertLess(
            qtb_layer,
            oos_layer,
            "quick_trade_button deve ter layer menor que out_of_stock para ser coberto pela cruz!",
        )
        self.assertLess(
            oos_layer,
            hover_layer,
            "out_of_stock deve ter layer menor que hover_button!",
        )

    def test_11_slots_and_background_variants_integrity(self):
        """11. Valida coleções de slots (ingredientes 1, 2 e resultado) e as 14 variantes de profissão."""
        right_panel = self.data.get("right_panel", {})
        rp_controls = right_panel.get("controls", [])

        # 1. Ingrediente 1
        ing1 = next((c["ingredient_1_item_slot@villager_desktop_dx.item_slot"] for c in rp_controls if "ingredient_1_item_slot@villager_desktop_dx.item_slot" in c), None)
        self.assertIsNotNone(ing1)
        self.assertEqual(ing1.get("$item_collection_name"), "trade2_ingredient1_item")

        # 2. Ingrediente 2
        ing2 = next((c["ingredient_2_item_slot@villager_desktop_dx.item_slot"] for c in rp_controls if "ingredient_2_item_slot@villager_desktop_dx.item_slot" in c), None)
        self.assertIsNotNone(ing2)
        self.assertEqual(ing2.get("$item_collection_name"), "trade2_ingredient2_item")

        # 3. Resultado
        res_panel = next((c["result_panel"] for c in rp_controls if "result_panel" in c), None)
        self.assertIsNotNone(res_panel)
        res_slot = next((c["result_item_slot@villager_desktop_dx.item_slot"] for c in res_panel.get("controls", []) if "result_item_slot@villager_desktop_dx.item_slot" in c), None)
        self.assertIsNotNone(res_slot)
        self.assertEqual(res_slot.get("$item_collection_name"), "trade2_result_item")

        # 4. Variantes de profissão de aldeão em villager_background_panel
        vbp = self.data.get("villager_background_panel", {})
        vbp_controls = vbp.get("controls", [])
        expected_variants = [
            "default@villager_textures_dx.villager",
            "armorer@villager_textures_dx.villager_armorer",
            "butcher@villager_textures_dx.villager_butcher",
            "cartographer@villager_textures_dx.villager_cartographer",
            "cleric@villager_textures_dx.villager_cleric",
            "farmer@villager_textures_dx.villager_farmer",
            "fisherman@villager_textures_dx.villager_fisherman",
            "fletcher@villager_textures_dx.villager_fletcher",
            "leatherworker@villager_textures_dx.villager_leatherworker",
            "librarian@villager_textures_dx.villager_librarian",
            "shepherd@villager_textures_dx.villager_shepherd",
            "tool_smith@villager_textures_dx.villager_tool_smith",
            "weapon_smith@villager_textures_dx.villager_weapon_smith",
            "stone_mason@villager_textures_dx.villager_stone_mason",
            "wandering_trader@villager_textures_dx.villager_wandering_trader",
        ]

        found_variants = []
        for c in vbp_controls:
            found_variants.extend(list(c.keys()))

        for variant in expected_variants:
            self.assertIn(
                variant,
                found_variants,
                f"A variante de profissão '{variant}' está ausente em villager_background_panel!",
            )

    def test_12_portuguese_comments_and_crisxolt_license(self):
        """12. Valida preservação dos créditos de CrisXolt e presença de comentários em Português do Brasil."""
        # Créditos e cabeçalho original de CrisXolt
        self.assertIn(
            "CrisXolt",
            self.raw_text,
            "Os créditos do autor original (@CrisXolt) devem ser preservados no cabeçalho!",
        )
        self.assertIn(
            "Don't use any UI data design without permission!",
            self.raw_text,
            "A declaração de licença original do cabeçalho deve ser preservada!",
        )

        # Comentários em Português do Brasil
        self.assertIn(
            "Quick Trade Tooltip & Hover Panel (Marco 4 - Aldeão)",
            self.raw_text,
            "Comentário em Português do Brasil de introdução do Quick Trade ausente!",
        )
        self.assertIn(
            "Estados padrão e bloqueado 100% transparentes para preservar a textura de madeira",
            self.raw_text,
            "Comentário explicativo sobre transparência dos controles de estado ausente!",
        )
        self.assertIn(
            "Validação nativa da Mojang: desabilita o botão se a troca não for viável",
            self.raw_text,
            "Comentário explicativo sobre binding de validação ausente!",
        )

    # Métodos auxiliares de navegação
    def _get_arrow_panel(self) -> Dict[str, Any]:
        rp = self.data.get("right_panel", {})
        for c in rp.get("controls", []):
            if "arrow_panel" in c:
                return c["arrow_panel"]
        return {}

    def _get_quick_trade_button(self) -> Dict[str, Any]:
        arrow = self._get_arrow_panel()
        for c in arrow.get("controls", []):
            if "quick_trade_button" in c:
                return c["quick_trade_button"]
        return {}


if __name__ == "__main__":
    unittest.main()
