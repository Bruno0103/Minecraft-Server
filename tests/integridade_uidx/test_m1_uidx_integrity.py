"""
Teste de Integridade Sintática e Semântica do Marco 1 (M1).
Verifica a Aba Lateral Vertical Compacta e o Desbloqueio Híbrido PC/Mobile
no pacote Colourful Containers BE: Mixed (ColourfulC).
"""

import json
import re
import unittest
from pathlib import Path

RESOURCE_PACK_DIR = Path(
    r"C:\Users\Bruno\AppData\Roaming\Minecraft Bedrock\Users\Shared\games\com.mojang\resource_packs\ColourfulC"
)


def strip_jsonc_comments_and_trailing_commas(text: str) -> str:
    """
    Remove comentários no formato C/C++ (// e /* ... */) e vírgulas sobressalentes
    (trailing commas) de strings JSONC / UIDX, preservando o conteúdo interno de literais string.
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


def parse_uidx(file_path: Path) -> dict:
    raw_content = file_path.read_text(encoding="utf-8")
    clean_content = strip_jsonc_comments_and_trailing_commas(raw_content)
    return json.loads(clean_content)


class TestM1UIDXIntegrity(unittest.TestCase):
    """Testes unitários e comportamentais para os 3 arquivos do Marco 1."""

    def setUp(self):
        self.touch_buttons_path = (
            RESOURCE_PACK_DIR
            / "vdx"
            / "ui_data"
            / "container"
            / "_modules"
            / "container_touch_buttons.uidx"
        )
        self.container_item_path = (
            RESOURCE_PACK_DIR
            / "vdx"
            / "ui_data"
            / "container"
            / "_modules"
            / "container_item.uidx"
        )
        self.villager_path = (
            RESOURCE_PACK_DIR
            / "vdx"
            / "ui_data"
            / "container"
            / "villager.uidx"
        )

    def test_container_touch_buttons_syntax_and_structure(self):
        """Verifica namespace, desbloqueio híbrido, ancoragem e os 3 botões em container_touch_buttons.uidx."""
        self.assertTrue(self.touch_buttons_path.exists(), f"Arquivo não encontrado: {self.touch_buttons_path}")
        data = parse_uidx(self.touch_buttons_path)

        # 1. Namespace
        self.assertEqual(data.get("namespace"), "touch_buttons_dx")

        # 2. inventory_buttons_panel e desbloqueio híbrido
        self.assertIn("inventory_buttons_panel", data)
        inv_panel = data["inventory_buttons_panel"]
        self.assertEqual(inv_panel.get("$touch_ref_buttons|default"), True)

        # Controles dentro do painel
        controls = inv_panel.get("controls", [])
        controls_dict = {}
        for c in controls:
            controls_dict.update(c)

        # 3. close_button mantido com ancoragem e bindings
        self.assertIn("close_button@button_dx.main_black_button", controls_dict)
        close_btn = controls_dict["close_button@button_dx.main_black_button"]
        self.assertEqual(close_btn.get("offset"), [24, 4])
        self.assertEqual(close_btn.get("size"), [20, 20])
        self.assertEqual(close_btn.get("anchor_from"), "top_right")

        # 4. container_side_tools_tab instanciada
        self.assertIn("container_side_tools_tab@touch_buttons_dx.container_side_tools_tab", controls_dict)

        # 5. Definição de container_side_tools_tab
        self.assertIn("container_side_tools_tab", data)
        tab_def = data["container_side_tools_tab"]
        self.assertEqual(tab_def.get("type"), "stack_panel")
        self.assertEqual(tab_def.get("orientation"), "vertical")
        self.assertEqual(tab_def.get("anchor_from"), "top_right")
        self.assertEqual(tab_def.get("anchor_to"), "top_right")
        self.assertEqual(tab_def.get("$container_side_tab_offset|default"), [24, 28])
        self.assertEqual(tab_def.get("layer"), 15)

        tab_controls = tab_def.get("controls", [])
        tab_controls_keys = [list(c.keys())[0] for c in tab_controls]
        self.assertIn("normal_place_tab@touch_buttons_dx.normal_place_tab", tab_controls_keys)
        self.assertIn("auto_place_tab@touch_buttons_dx.auto_place_tab", tab_controls_keys)
        self.assertIn("drop_place_tab@touch_buttons_dx.drop_place_tab", tab_controls_keys)

        # 6. Alias para retrocompatibilidade
        self.assertIn("top_type_toggles@touch_buttons_dx.container_side_tools_tab", data)

        # 7. Definição do toggle base
        self.assertIn("mode_toggle@toggle_dx.toggle_panel", data)
        toggle_base = data["mode_toggle@toggle_dx.toggle_panel"]
        self.assertEqual(toggle_base.get("$toggle_name"), "inventory_mode_toggle")
        self.assertEqual(toggle_base.get("$radio_toggle_group"), True)
        self.assertEqual(toggle_base.get("$checked_control"), "button_dx.black_button_checked_right_default")

        # 8. Índices forçados e estado padrão
        self.assertEqual(data["normal_place_tab@touch_buttons_dx.mode_toggle"].get("$toggle_group_forced_index"), 0)
        self.assertEqual(data["normal_place_tab@touch_buttons_dx.mode_toggle"].get("$toggle_default_state"), True)
        self.assertEqual(data["auto_place_tab@touch_buttons_dx.mode_toggle"].get("$toggle_group_forced_index"), 1)
        self.assertEqual(data["drop_place_tab@touch_buttons_dx.mode_toggle"].get("$toggle_group_forced_index"), 2)

        # 9. Tooltips e Ícones nos painéis de conteúdo
        self.assertIn("normal_place_content", data)
        self.assertIn("auto_place_content", data)
        self.assertIn("drop_place_content", data)

    def test_container_item_syntax_and_event_mappings(self):
        """Verifica a variável global e o mapeamento de mouse, teclado, toque e controle em container_item.uidx."""
        self.assertTrue(self.container_item_path.exists(), f"Arquivo não encontrado: {self.container_item_path}")
        data = parse_uidx(self.container_item_path)

        # 1. Obter container_item
        container_item = data.get("container_item")
        self.assertIsNotNone(container_item, "container_item não definido em container_item.uidx")

        # 2. Desbloqueio híbrido universal
        self.assertEqual(container_item.get("$touch_ref_buttons|default"), True)

        # 3. Localizar item_button_ref nos controles
        controls = container_item.get("controls", [])
        item_button_ref = None
        for c in controls:
            if "item_button_ref" in c:
                item_button_ref = c["item_button_ref"]
                break

        self.assertIsNotNone(item_button_ref, "item_button_ref não encontrado em controls")
        btn_controls = item_button_ref.get("controls", [])
        btn_map = {}
        for bc in btn_controls:
            btn_map.update(bc)

        # 4. Modos presentes
        self.assertIn("main@$button_ref", btn_map)
        self.assertIn("normal_place", btn_map)
        self.assertIn("auto_place", btn_map)
        self.assertIn("drop_place", btn_map)

        # Main condicionado a ($touch_ref_buttons)
        self.assertEqual(btn_map["main@$button_ref"].get("ignored"), "($touch_ref_buttons)")

        # Modos condicionados a (not $touch_ref_buttons)
        self.assertEqual(btn_map["normal_place"].get("ignored"), "(not $touch_ref_buttons)")
        self.assertEqual(btn_map["auto_place"].get("ignored"), "(not $touch_ref_buttons)")
        self.assertEqual(btn_map["drop_place"].get("ignored"), "(not $touch_ref_buttons)")

        # 5. auto_place button mappings
        auto_btn = btn_map["auto_place"]["controls"][0]["button@$button_ref"]
        auto_mappings = auto_btn.get("button_mappings", [])
        auto_events = {m["from_button_id"]: m["to_button_id"] for m in auto_mappings}
        self.assertEqual(auto_events.get("button.menu_select"), "$auto_place_mode_button_name")
        self.assertEqual(auto_events.get("button.menu_ok"), "$auto_place_mode_button_name")
        self.assertEqual(auto_events.get("button.controller_select"), "$auto_place_mode_button_name")
        self.assertEqual(auto_events.get("button.controller_secondary_select"), "$auto_place_mode_button_name")

        # 6. drop_place button mappings
        drop_btn = btn_map["drop_place"]["controls"][0]["button@$button_ref"]
        drop_mappings = drop_btn.get("button_mappings", [])
        drop_events = {m["from_button_id"]: m["to_button_id"] for m in drop_mappings}
        self.assertEqual(drop_events.get("button.menu_select"), "button.drop_all")
        self.assertEqual(drop_events.get("button.menu_ok"), "button.drop_all")
        self.assertEqual(drop_events.get("button.controller_select"), "button.drop_all")
        self.assertEqual(drop_events.get("button.controller_secondary_select"), "button.drop_all")

        # 7. Controles visuais presentes (default_control, hover_control, pressed_control)
        for mode_name, mode_panel in [("auto_place", auto_btn), ("drop_place", drop_btn)]:
            self.assertEqual(mode_panel.get("default_control"), "default", f"default_control ausente em {mode_name}")
            self.assertEqual(mode_panel.get("hover_control"), "hover", f"hover_control ausente em {mode_name}")
            self.assertEqual(mode_panel.get("pressed_control"), "pressed", f"pressed_control ausente em {mode_name}")

    def test_villager_trade_info_button_offset(self):
        """Verifica o offset ajustado do trade_info_button em villager.uidx para [24, 92]."""
        self.assertTrue(self.villager_path.exists(), f"Arquivo não encontrado: {self.villager_path}")
        data = parse_uidx(self.villager_path)

        # Encontrar trade_info_button recursivamente ou na árvore
        trade_info_button = None

        def search_dict(d):
            nonlocal trade_info_button
            if isinstance(d, dict):
                for k, v in d.items():
                    if "trade_info_button" in k:
                        trade_info_button = v
                        return
                    search_dict(v)
            elif isinstance(d, list):
                for item in d:
                    search_dict(item)

        search_dict(data)
        self.assertIsNotNone(trade_info_button, "trade_info_button não encontrado em villager.uidx")
        self.assertEqual(trade_info_button.get("offset"), [24, 92])
        self.assertEqual(trade_info_button.get("size"), [20, 20])
        self.assertEqual(trade_info_button.get("anchor_from"), "top_right")
        self.assertEqual(trade_info_button.get("anchor_to"), "top_right")


if __name__ == "__main__":
    unittest.main()
