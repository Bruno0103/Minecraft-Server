"""
===============================================================================
SUÍTE DE TESTES AUTOMATIZADOS DE INTEGRIDADE DO MARCO 2 (M2)
Arquivo: tests/test_m2_quick_craft_integrity.py
===============================================================================
Objetivo:
Validar de forma automatizada, sintática e semântica a implementação do
Quick Crafting (Fabricação Rápida de Lote com 1 Clique) nos contêineres:
1. Bancada de Trabalho 3x3: vdx/ui_data/container_mixed/crafting_table.uidx
2. Inventário do Jogador 2x2: vdx/ui_data/container_mixed/inventory.uidx

Aspectos Verificados:
- Existência e conformidade JSONC/UTF-8 dos arquivos no disco.
- Presença do evento 'button.crafting_output_tertiary' nos botões das setas.
- Presença do tooltip explicativo com 'tooltip_dx.tooltip_panel' ou
  'tooltip_dx.hover_text_panel' e texto localizável 'controller.buttonTip.craftAll'.
- Presença da variável '$auto_place_mode_button_name' configurada para
  'button.crafting_output_tertiary' nos slots de saída (work_bench_output)
  tanto na bancada 3x3 quanto no inventário 2x2.
===============================================================================
"""

import json
import re
import unittest
from pathlib import Path
from typing import Dict, Any, Optional

# Diretório base do Resource Pack Colourful Containers
RESOURCE_PACK_DIR = Path(
    r"C:\Users\Bruno\AppData\Roaming\Minecraft Bedrock\Users\Shared\games\com.mojang\resource_packs\ColourfulC"
)

# Caminhos absolutos dos arquivos inspecionados no Marco 2
CRAFTING_TABLE_UIDX = (
    RESOURCE_PACK_DIR / "vdx" / "ui_data" / "container_mixed" / "crafting_table.uidx"
)
INVENTORY_UIDX = (
    RESOURCE_PACK_DIR / "vdx" / "ui_data" / "container_mixed" / "inventory.uidx"
)


def strip_jsonc_comments_and_trailing_commas(text: str) -> str:
    """
    Remove comentários C-style (// e /* ... */) e vírgulas sobressalentes
    (trailing commas) de strings JSONC / UIDX, preservando rigorosamente
    o conteúdo interno de strings literais e escapes.
    """
    result = []
    in_string = False
    escape = False
    i = 0
    length = len(text)

    while i < length:
        char = text[i]

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
    # Remove trailing commas antes de fechamento de objeto ou array
    cleaned = re.sub(r",\s*([\]}])", r"\1", cleaned)
    return cleaned


def parse_uidx(file_path: Path) -> Dict[str, Any]:
    """Lê um arquivo UIDX/JSONC do disco e retorna o dicionário decodificado."""
    raw_content = file_path.read_text(encoding="utf-8")
    clean_content = strip_jsonc_comments_and_trailing_commas(raw_content)
    return json.loads(clean_content)


def find_control_recursive(data: Any, target_name: str) -> Optional[Dict[str, Any]]:
    """Busca recursivamente em árvores de dicionários/listas por uma chave contendo target_name."""
    if isinstance(data, dict):
        for k, v in data.items():
            if target_name in k:
                return v if isinstance(v, dict) else {k: v}
            res = find_control_recursive(v, target_name)
            if res is not None:
                return res
    elif isinstance(data, list):
        for item in data:
            res = find_control_recursive(item, target_name)
            if res is not None:
                return res
    return None


class TestM2QuickCraftIntegrity(unittest.TestCase):
    """Suíte de testes automatizados para Quick Crafting na Bancada 3x3 e Inventário 2x2."""

    def setUp(self):
        self.crafting_table_path = CRAFTING_TABLE_UIDX
        self.inventory_path = INVENTORY_UIDX

    def test_01_files_exist_and_utf8_validity(self):
        """Verifica a existência física e a leitura UTF-8 dos dois arquivos do Marco 2."""
        self.assertTrue(
            self.crafting_table_path.exists(),
            f"Arquivo de bancada não encontrado: {self.crafting_table_path}",
        )
        self.assertTrue(
            self.inventory_path.exists(),
            f"Arquivo de inventário não encontrado: {self.inventory_path}",
        )

        # Testa leitura crua em UTF-8 sem erros de decodificação
        try:
            self.crafting_table_path.read_text(encoding="utf-8")
            self.inventory_path.read_text(encoding="utf-8")
        except UnicodeDecodeError as exc:
            self.fail(f"Erro de decodificação UTF-8 em arquivos do M2: {exc}")

    def test_02_jsonc_syntax_integrity(self):
        """Verifica se ambos os arquivos são JSONC sintaticamente válidos."""
        try:
            crafting_data = parse_uidx(self.crafting_table_path)
            self.assertIsInstance(crafting_data, dict)
            self.assertEqual(crafting_data.get("namespace"), "crafting_table_mixed_dx")
        except Exception as exc:
            self.fail(f"Falha de sintaxe JSONC em crafting_table.uidx: {exc}")

        try:
            inv_data = parse_uidx(self.inventory_path)
            self.assertIsInstance(inv_data, dict)
            self.assertEqual(inv_data.get("namespace"), "inventory_mixed_dx")
        except Exception as exc:
            self.fail(f"Falha de sintaxe JSONC em inventory.uidx: {exc}")

    def test_03_crafting_table_arrow_action(self):
        """
        Verifica se a seta de crafting na bancada 3x3 contém o botão ativo
        com mapeamento para 'button.crafting_output_tertiary' em menu_select e menu_ok.
        """
        data = parse_uidx(self.crafting_table_path)
        raw_text = self.crafting_table_path.read_text(encoding="utf-8")

        # 1. Verificação léxica direta no arquivo
        self.assertIn(
            "button.crafting_output_tertiary",
            raw_text,
            "Evento 'button.crafting_output_tertiary' ausente em crafting_table.uidx",
        )

        # 2. Localização estrutural da seta
        arrow_panel = find_control_recursive(data, "arrow")
        self.assertIsNotNone(arrow_panel, "Controle 'arrow' não encontrado em crafting_table.uidx")

        # Verifica dimensões canônicas da seta 3x3
        self.assertEqual(arrow_panel.get("size"), [28, 21])
        self.assertEqual(arrow_panel.get("offset"), [87, 32])

        # 3. Mapeamentos de botão da seta
        btn_control = find_control_recursive(arrow_panel, "button")
        self.assertIsNotNone(btn_control, "Botão interativo não encontrado dentro de 'arrow'")

        # Validação do property bag para coletar do slot de saída
        prop_bag = btn_control.get("property_bag", {})
        self.assertEqual(prop_bag.get("#collection_name"), "crafting_output_items")
        self.assertEqual(prop_bag.get("#collection_index"), 0)

        # Validação dos mapeamentos de botão
        mappings = btn_control.get("button_mappings", [])
        mapping_dict = {m.get("from_button_id"): m.get("to_button_id") for m in mappings}
        self.assertEqual(
            mapping_dict.get("button.menu_select"),
            "button.crafting_output_tertiary",
            "Mapeamento de menu_select não direcionado para button.crafting_output_tertiary",
        )
        self.assertEqual(
            mapping_dict.get("button.menu_ok"),
            "button.crafting_output_tertiary",
            "Mapeamento de menu_ok não direcionado para button.crafting_output_tertiary",
        )

    def test_04_crafting_table_arrow_tooltip(self):
        """
        Verifica se a seta de crafting na bancada 3x3 possui tooltip configurado
        com texto localizável 'controller.buttonTip.craftAll' e painel tooltip_dx.
        """
        raw_text = self.crafting_table_path.read_text(encoding="utf-8")
        data = parse_uidx(self.crafting_table_path)

        # 1. Verifica presença da chave de localização
        self.assertIn(
            "controller.buttonTip.craftAll",
            raw_text,
            "Chave 'controller.buttonTip.craftAll' não encontrada em crafting_table.uidx",
        )

        # 2. Verifica presença de componente de tooltip (tooltip_panel ou hover_text_panel)
        has_tooltip_component = (
            "tooltip_dx.tooltip_panel" in raw_text
            or "tooltip_dx.hover_text_panel" in raw_text
            or "hover_text_panel" in raw_text
        )
        self.assertTrue(
            has_tooltip_component,
            "Componente de tooltip do namespace tooltip_dx ausente na seta de crafting_table.uidx",
        )

        # 3. Verifica se a seta possui controles de hover/tooltip
        arrow_panel = find_control_recursive(data, "arrow")
        arrow_str = json.dumps(arrow_panel)
        self.assertIn(
            "controller.buttonTip.craftAll",
            arrow_str,
            "Tooltip 'controller.buttonTip.craftAll' não associado à seta de crafting 3x3",
        )

    def test_05_crafting_table_output_slot_auto_place(self):
        """
        Verifica se o slot de saída 3x3 (work_bench_output) possui a variável
        $auto_place_mode_button_name configurada como 'button.crafting_output_tertiary'.
        """
        data = parse_uidx(self.crafting_table_path)
        output_slot = find_control_recursive(data, "work_bench_output")
        self.assertIsNotNone(
            output_slot, "Controle 'work_bench_output' não encontrado em crafting_table.uidx"
        )
        self.assertEqual(
            output_slot.get("$auto_place_mode_button_name"),
            "button.crafting_output_tertiary",
            "work_bench_output na bancada 3x3 deve ter $auto_place_mode_button_name: button.crafting_output_tertiary",
        )

    def test_06_inventory_arrow_action(self):
        """
        Verifica se a seta de crafting no inventário 2x2 contém o botão ativo
        com mapeamento para 'button.crafting_output_tertiary' em menu_select e menu_ok.
        """
        data = parse_uidx(self.inventory_path)
        raw_text = self.inventory_path.read_text(encoding="utf-8")

        # 1. Verificação léxica direta no arquivo
        self.assertIn(
            "button.crafting_output_tertiary",
            raw_text,
            "Evento 'button.crafting_output_tertiary' ausente em inventory.uidx",
        )

        # 2. Localização estrutural da seta 2x2
        arrow_panel = find_control_recursive(data, "arrow")
        self.assertIsNotNone(arrow_panel, "Controle 'arrow' não encontrado em inventory.uidx")

        # Verifica dimensões canônicas da seta 2x2
        self.assertEqual(arrow_panel.get("size"), [18, 18])
        self.assertEqual(arrow_panel.get("offset"), [134, 27])

        # 3. Mapeamentos de botão da seta
        btn_control = find_control_recursive(arrow_panel, "button")
        self.assertIsNotNone(btn_control, "Botão interativo não encontrado dentro de 'arrow' 2x2")

        # Validação do property bag para coletar do slot de saída
        prop_bag = btn_control.get("property_bag", {})
        self.assertEqual(prop_bag.get("#collection_name"), "crafting_output_items")
        self.assertEqual(prop_bag.get("#collection_index"), 0)

        # Validação dos mapeamentos de botão
        mappings = btn_control.get("button_mappings", [])
        mapping_dict = {m.get("from_button_id"): m.get("to_button_id") for m in mappings}
        self.assertEqual(
            mapping_dict.get("button.menu_select"),
            "button.crafting_output_tertiary",
            "Mapeamento de menu_select não direcionado para button.crafting_output_tertiary em inventory.uidx",
        )
        self.assertEqual(
            mapping_dict.get("button.menu_ok"),
            "button.crafting_output_tertiary",
            "Mapeamento de menu_ok não direcionado para button.crafting_output_tertiary em inventory.uidx",
        )

    def test_07_inventory_arrow_tooltip(self):
        """
        Verifica se a seta de crafting no inventário 2x2 possui tooltip configurado
        com texto localizável 'controller.buttonTip.craftAll' e painel tooltip_dx.
        """
        raw_text = self.inventory_path.read_text(encoding="utf-8")
        data = parse_uidx(self.inventory_path)

        # 1. Verifica presença da chave de localização
        self.assertIn(
            "controller.buttonTip.craftAll",
            raw_text,
            "Chave 'controller.buttonTip.craftAll' não encontrada em inventory.uidx",
        )

        # 2. Verifica presença de componente de tooltip (tooltip_panel ou hover_text_panel)
        has_tooltip_component = (
            "tooltip_dx.tooltip_panel" in raw_text
            or "tooltip_dx.hover_text_panel" in raw_text
            or "hover_text_panel" in raw_text
        )
        self.assertTrue(
            has_tooltip_component,
            "Componente de tooltip do namespace tooltip_dx ausente na seta de inventory.uidx",
        )

        # 3. Verifica se a seta possui controles de hover/tooltip
        arrow_panel = find_control_recursive(data, "arrow")
        arrow_str = json.dumps(arrow_panel)
        self.assertIn(
            "controller.buttonTip.craftAll",
            arrow_str,
            "Tooltip 'controller.buttonTip.craftAll' não associado à seta de crafting 2x2",
        )

    def test_08_inventory_output_slot_auto_place(self):
        """
        Verifica se o slot de saída 2x2 (work_bench_output) possui obrigatoriamente
        a variável $auto_place_mode_button_name configurada como 'button.crafting_output_tertiary'.
        Garante a correção da anomalia de paridade detectada na Fase 0 (Survey).
        """
        data = parse_uidx(self.inventory_path)
        output_slot = find_control_recursive(data, "work_bench_output")
        self.assertIsNotNone(
            output_slot, "Controle 'work_bench_output' não encontrado em inventory.uidx"
        )
        self.assertEqual(
            output_slot.get("$auto_place_mode_button_name"),
            "button.crafting_output_tertiary",
            "work_bench_output no inventário 2x2 DEVE ter $auto_place_mode_button_name: button.crafting_output_tertiary",
        )


if __name__ == "__main__":
    unittest.main()
