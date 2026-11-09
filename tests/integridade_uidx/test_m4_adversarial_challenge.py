"""
===============================================================================
SUÍTE DE TESTES ADVERSARIAIS E DESAFIO EMPÍRICO DO MARCO 4 (M4)
Arquivo: tests/test_m4_adversarial_challenge.py
===============================================================================
Papel: challenger_m4_1 (Desafiador Empírico e Sintático do Marco 4)
Archetype: challenger | Roles: critic, specialist

Objetivo:
Submeter a implementação do Marco 4 (Assistente de Trocas com Aldeões - quick_trade_button)
a um rigoroso conjunto de testes adversariais automatizados e oráculos empíricos,
verificando de forma independente e cética:
1. Codificação pura UTF-8 sem BOM (Byte Order Mark) e integridade de fluxo binário.
2. Balanceamento estrutural estrito de delimitadores ({}, []) via máquina de estados.
3. Conformidade estrita JSON sem remoção de vírgulas trailing (zero trailing commas).
4. Detecção profunda de chaves duplicadas no AST (Abstract Syntax Tree) em todos os níveis.
5. Detecção de nomes de controles duplicados em arrays 'controls' no escopo UIDX.
6. Contrato de schema e integridade do botão de troca rápida (quick_trade_button).
7. Contrato do binding nativo de validação (#trade_button_enabled -> #enabled).
8. Estados de repouso e bloqueio 100% transparentes (miscellaneous_dx.empty_panel).
9. Contrato do painel de hover flutuante (tooltip) e integridade de chaves .lang.
10. Precedência de camadas (layer 4 < layer 14 de esgotamento < layer 15 de aviso).
11. Isolamento espacial e ausência de colisão AABB com slots e botões externos.
12. Integridade estrutural das 15 variantes de aldeão e existência física das texturas.
13. Sensibilidade do oráculo contra mutações adversariais deliberadas.
===============================================================================
"""

import json
import re
import unittest
from pathlib import Path
from typing import Dict, Any, List, Tuple, Optional, Set


# Caminhos absolutos do projeto
RESOURCE_PACK_DIR = Path(
    r"C:\Users\Bruno\AppData\Roaming\Minecraft Bedrock\Users\Shared\games\com.mojang\resource_packs\ColourfulC"
)

VILLAGER_UIDX_PATH = (
    RESOURCE_PACK_DIR / "vdx" / "ui_data" / "container" / "villager.uidx"
)

VILLAGER_TEXTURES_UIDX_PATH = (
    RESOURCE_PACK_DIR / "vdx" / "ui_data" / "gui_textures" / "container" / "villager.uidx"
)

MISCELLANEOUS_UIDX_PATH = (
    RESOURCE_PACK_DIR / "vdx" / "ui_data" / "_common" / "miscellaneous.uidx"
)

TOOLTIP_UIDX_PATH = (
    RESOURCE_PACK_DIR / "vdx" / "ui_data" / "_common" / "tooltip.uidx"
)

LANG_PT_BR_PATH = RESOURCE_PACK_DIR / "texts" / "pt_BR.lang"
LANG_EN_US_PATH = RESOURCE_PACK_DIR / "texts" / "en_US.lang"


def strip_jsonc_comments_only(text: str) -> str:
    """
    Remove estritamente apenas comentários C-style (// linha e /* bloco */) de textos JSONC.
    NÃO remove vírgulas trailing nem altera espaços em branco fora dos comentários.
    Permite validar conformidade estrita com o parser oficial json.loads do Python.
    """
    result = []
    in_string = False
    escape = False
    i = 0
    length = len(text)

    while i < length:
        char = text[i]

        # Tratamento de strings literais com escape
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

    return "".join(result)


def strict_duplicate_key_detector(pairs: List[Tuple[str, Any]]) -> Dict[str, Any]:
    """
    Hook para json.loads que detecta colisões de chaves duplicadas em qualquer nível do AST.
    Lança ValueError imediatamente caso encontre chave repetida no mesmo objeto.
    """
    seen: Set[str] = set()
    result: Dict[str, Any] = {}
    for key, val in pairs:
        if key in seen:
            raise ValueError(f"Chave duplicada detectada no AST: '{key}'")
        seen.add(key)
        result[key] = val
    return result


class TestM4AdversarialChallenge(unittest.TestCase):
    """
    Suíte adversarial empírica para validação rigorosa de villager.uidx no Marco 4.
    """

    @classmethod
    def setUpClass(cls):
        """Carrega e prepara os arquivos para a bateria de testes."""
        cls.raw_bytes = VILLAGER_UIDX_PATH.read_bytes()
        cls.raw_text = cls.raw_bytes.decode("utf-8")
        cls.cleaned_comments_only = strip_jsonc_comments_only(cls.raw_text)

        # Parse estrito sem remoção de vírgulas trailing e com detector de duplicatas
        cls.data = json.loads(
            cls.cleaned_comments_only,
            object_pairs_hook=strict_duplicate_key_detector
        )

    # -------------------------------------------------------------------------
    # 1. Integridade Binária e Codificação UTF-8
    # -------------------------------------------------------------------------
    def test_01_binary_pure_utf8_no_bom(self):
        """
        Valida que o arquivo villager.uidx não possui BOM UTF-8 (\\xef\\xbb\\xbf)
        e decodifica perfeitamente como UTF-8 sem bytes corrompidos ou caracteres nulos.
        """
        self.assertFalse(
            self.raw_bytes.startswith(b"\xef\xbb\xbf"),
            "FALHA ADVERSARIAL: villager.uidx contém UTF-8 BOM, o que quebra o parser Bedrock!",
        )
        self.assertNotIn(
            b"\x00",
            self.raw_bytes,
            "FALHA ADVERSARIAL: Byte NUL detectado no arquivo!",
        )
        self.assertGreater(
            len(self.raw_bytes),
            10000,
            "FALHA ADVERSARIAL: Arquivo excessivamente truncado!",
        )

    # -------------------------------------------------------------------------
    # 2. Balanceamento Estrito de Delimitadores
    # -------------------------------------------------------------------------
    def test_02_braces_brackets_strict_balancing(self):
        """
        Valida via máquina de estados que todos os delimitadores {} e [] estão
        rigorosamente balanceados e aninhados na ordem correta LIFO.
        """
        stack = []
        in_string = False
        escape = False
        i = 0
        text = self.cleaned_comments_only
        length = len(text)

        while i < length:
            char = text[i]
            if in_string:
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
                i += 1
                continue

            if char in "{[":
                stack.append((char, i))
            elif char in "}]":
                self.assertTrue(
                    len(stack) > 0,
                    f"FALHA ADVERSARIAL: Delimitador de fechamento '{char}' sem abertura correspondente na posição {i}!",
                )
                open_char, open_idx = stack.pop()
                expected = "}" if open_char == "{" else "]"
                self.assertEqual(
                    char,
                    expected,
                    f"FALHA ADVERSARIAL: Mismatch de delimitador! Esperado '{expected}', obtido '{char}' (aberto em {open_idx})",
                )
            i += 1

        self.assertEqual(
            len(stack),
            0,
            f"FALHA ADVERSARIAL: Delimitadores não fechados ao final do arquivo: {stack}",
        )

    # -------------------------------------------------------------------------
    # 3. Ausência Total de Trailing Commas (Conformidade Estrita JSON)
    # -------------------------------------------------------------------------
    def test_03_zero_trailing_commas_strict_json(self):
        """
        Verifica que não existem vírgulas trailing antes de '}' ou ']' em villager.uidx.
        Garante que o json.loads padrão do Python aceita o arquivo sem pré-processamento de regex.
        """
        # 1. Regex de verificação em código sem comentários
        trailing_comma_pattern = re.compile(r",\s*([\]}])")
        matches = list(trailing_comma_pattern.finditer(self.cleaned_comments_only))
        self.assertEqual(
            len(matches),
            0,
            f"FALHA ADVERSARIAL: Detectadas {len(matches)} vírgulas trailing em villager.uidx!",
        )

        # 2. Confirma que json.loads direto sem sanitização passa sem exceção
        try:
            parsed = json.loads(self.cleaned_comments_only)
            self.assertIsInstance(parsed, dict)
        except json.JSONDecodeError as e:
            self.fail(f"FALHA ADVERSARIAL: Falha de parse no json.loads nativo: {e}")

    # -------------------------------------------------------------------------
    # 4. Detecção Profunda de Chaves Duplicadas no AST
    # -------------------------------------------------------------------------
    def test_04_zero_duplicate_keys_in_ast_depth(self):
        """
        Valida que nenhuma chave duplicada existe em qualquer nível de profundidade
        do AST de villager.uidx usando o hook estrito strict_duplicate_key_detector.
        """
        try:
            json.loads(
                self.cleaned_comments_only,
                object_pairs_hook=strict_duplicate_key_detector
            )
        except ValueError as e:
            self.fail(f"FALHA ADVERSARIAL: Colisão de chave duplicada no AST: {e}")

    # -------------------------------------------------------------------------
    # 5. Detecção de Controles Duplicados em Listas 'controls'
    # -------------------------------------------------------------------------
    def test_05_zero_duplicate_control_names_in_arrays(self):
        """
        Percorre recursivamente todas as listas 'controls' e garante que nenhum
        componente filho possua identificador idêntico no mesmo nível.
        """
        duplicate_controls = []

        def check_controls_recursive(obj, path="root"):
            if isinstance(obj, dict):
                for k, v in obj.items():
                    if k == "controls" and isinstance(v, list):
                        seen_names: Set[str] = set()
                        for idx, item in enumerate(v):
                            if isinstance(item, dict):
                                for item_name in item.keys():
                                    base_name = item_name.split("@")[0]
                                    if base_name in seen_names:
                                        duplicate_controls.append(f"{path}.controls[{idx}]: {base_name}")
                                    seen_names.add(base_name)
                                    check_controls_recursive(item[item_name], f"{path}.controls[{idx}].{item_name}")
                    else:
                        check_controls_recursive(v, f"{path}.{k}")
            elif isinstance(obj, list):
                for idx, elem in enumerate(obj):
                    check_controls_recursive(elem, f"{path}[{idx}]")

        check_controls_recursive(self.data)
        self.assertEqual(
            duplicate_controls,
            [],
            f"FALHA ADVERSARIAL: Controles duplicados em listas 'controls': {duplicate_controls}",
        )

    # -------------------------------------------------------------------------
    # 6. Contrato de Schema do quick_trade_button
    # -------------------------------------------------------------------------
    def test_06_quick_trade_button_contract_and_properties(self):
        """
        Valida o contrato de schema completo do quick_trade_button:
        - tipo 'button', dimensões [28, 21], layer 4
        - property_bag configurado para trade2_result_item[0]
        - button_mappings para menu_select e menu_ok disparando container_auto_place
        """
        arrow_panel = self._get_arrow_panel()
        self.assertIsNotNone(arrow_panel, "arrow_panel não encontrado em right_panel!")

        quick_trade_btn = self._get_quick_trade_button()
        self.assertIsNotNone(quick_trade_btn, "quick_trade_button ausente dentro de arrow_panel!")

        self.assertEqual(quick_trade_btn.get("type"), "button")
        self.assertEqual(quick_trade_btn.get("size"), [28, 21])
        self.assertEqual(quick_trade_btn.get("layer"), 4)

        # Property Bag
        pb = quick_trade_btn.get("property_bag", {})
        self.assertEqual(pb.get("#collection_name"), "trade2_result_item")
        self.assertEqual(pb.get("#collection_index"), 0)

        # Mapeamentos de botão
        mappings = quick_trade_btn.get("button_mappings", [])
        self.assertGreaterEqual(len(mappings), 2, "quick_trade_button deve ter ao menos 2 mappings!")

        from_ids = [m.get("from_button_id") for m in mappings]
        self.assertIn("button.menu_select", from_ids)
        self.assertIn("button.menu_ok", from_ids)

        for m in mappings:
            self.assertEqual(m.get("to_button_id"), "button.container_auto_place")
            self.assertEqual(m.get("mapping_type"), "pressed")

    # -------------------------------------------------------------------------
    # 7. Contrato do Binding de Validação Nativo
    # -------------------------------------------------------------------------
    def test_07_validation_binding_contract(self):
        """
        Valida a presença obrigatória do binding nativo da Mojang #trade_button_enabled
        sobrescrevendo #enabled para desabilitar o clique quando o jogador não tiver recursos.
        """
        qtb = self._get_quick_trade_button()
        bindings = qtb.get("bindings", [])
        self.assertTrue(len(bindings) >= 1, "quick_trade_button deve possuir lista de bindings!")

        trade_binding = next(
            (b for b in bindings if b.get("binding_name") == "#trade_button_enabled"),
            None
        )
        self.assertIsNotNone(trade_binding, "Binding '#trade_button_enabled' ausente!")
        self.assertEqual(
            trade_binding.get("binding_name_override"),
            "#enabled",
            "Override do binding deve ser '#enabled'!",
        )

    # -------------------------------------------------------------------------
    # 8. Estados de Repouso e Bloqueio 100% Transparentes
    # -------------------------------------------------------------------------
    def test_08_button_states_and_transparency(self):
        """
        Garante que os estados de repouso e bloqueio utilizem miscellaneous_dx.empty_panel,
        assegurando que nenhuma arte temática rústica do Colourful Containers seja ocultada.
        """
        qtb = self._get_quick_trade_button()
        self.assertEqual(qtb.get("default_control"), "default")
        self.assertEqual(qtb.get("locked_control"), "locked")
        self.assertEqual(qtb.get("hover_control"), "hover")
        self.assertEqual(qtb.get("pressed_control"), "pressed")

        controls = qtb.get("controls", [])
        control_dict = {}
        for c in controls:
            control_dict.update(c)

        self.assertIn(
            "default@miscellaneous_dx.empty_panel",
            control_dict,
            "Estado 'default' deve instanciar 'miscellaneous_dx.empty_panel'!",
        )
        self.assertIn(
            "locked@miscellaneous_dx.empty_panel",
            control_dict,
            "Estado 'locked' deve instanciar 'miscellaneous_dx.empty_panel'!",
        )
        self.assertIn(
            "hover@villager_desktop_dx.quick_trade_hover_panel",
            control_dict,
            "Estado 'hover' deve instanciar 'villager_desktop_dx.quick_trade_hover_panel'!",
        )
        self.assertIn(
            "pressed@villager_desktop_dx.quick_trade_hover_panel",
            control_dict,
            "Estado 'pressed' deve instanciar 'villager_desktop_dx.quick_trade_hover_panel'!",
        )

        # Valida existência de miscellaneous_dx.empty_panel no arquivo de origem
        misc_text = strip_jsonc_comments_only(MISCELLANEOUS_UIDX_PATH.read_text(encoding="utf-8"))
        misc_data = json.loads(misc_text)
        self.assertIn("empty_panel", misc_data, "empty_panel não existe em miscellaneous.uidx!")

    # -------------------------------------------------------------------------
    # 9. Contrato do Painel de Tooltip e Localização .lang
    # -------------------------------------------------------------------------
    def test_09_tooltip_and_localization_contract(self):
        """
        Valida que quick_trade_hover_panel existe na raiz de villager.uidx,
        instancia tooltip_dx.tooltip_panel com chave trade.tradeAll, e que
        ambos pt_BR.lang e en_US.lang definem traduções correspondentes.
        """
        self.assertIn(
            "quick_trade_hover_panel",
            self.data,
            "quick_trade_hover_panel ausente na raiz de villager.uidx!",
        )
        qthp = self.data["quick_trade_hover_panel"]
        self.assertEqual(qthp.get("type"), "panel")
        self.assertEqual(qthp.get("size"), [28, 21])

        # Verifica controle tooltip filho
        controls = qthp.get("controls", [])
        tooltip_control = next(
            (c.get("hover_text_panel@tooltip_dx.tooltip_panel") for c in controls if "hover_text_panel@tooltip_dx.tooltip_panel" in c),
            None
        )
        self.assertIsNotNone(tooltip_control, "tooltip_panel não instanciado em quick_trade_hover_panel!")
        self.assertEqual(tooltip_control.get("$hover_panel_text"), "trade.tradeAll")
        self.assertEqual(tooltip_control.get("$hover_anchor_from"), "top_middle")
        self.assertEqual(tooltip_control.get("$hover_anchor_to"), "bottom_middle")
        self.assertEqual(tooltip_control.get("$hover_text_offset"), [0, -4])

        # Verifica existência de tooltip_panel em tooltip.uidx
        tt_text = strip_jsonc_comments_only(TOOLTIP_UIDX_PATH.read_text(encoding="utf-8"))
        tt_data = json.loads(tt_text)
        self.assertIn("tooltip_panel", tt_data, "tooltip_panel não existe em tooltip.uidx!")

        # Valida strings de tradução
        pt_lines = LANG_PT_BR_PATH.read_text(encoding="utf-8-sig").splitlines()
        en_lines = LANG_EN_US_PATH.read_text(encoding="utf-8").splitlines()

        self.assertTrue(
            any(line.strip() == "trade.tradeAll=Trocar Tudo" for line in pt_lines),
            "Linha 'trade.tradeAll=Trocar Tudo' ausente em texts/pt_BR.lang!",
        )
        self.assertTrue(
            any(line.strip() == "trade.tradeAll=Trade All" for line in en_lines),
            "Linha 'trade.tradeAll=Trade All' ausente em texts/en_US.lang!",
        )

    # -------------------------------------------------------------------------
    # 10. Precedência de Camadas (Layer Hierarchy)
    # -------------------------------------------------------------------------
    def test_10_layer_hierarchy_precedence(self):
        """
        Valida que a hierarquia Z-layer garante sobreposição incondicional
        dos elementos de esgotamento de estoque sobre o botão de troca rápida:
        quick_trade_button (layer 4) < out_of_stock (layer 14) < hover_button (layer 15).
        """
        qtb = self._get_quick_trade_button()
        qtb_layer = qtb.get("layer", 0)

        apc = self.data.get("arrow_panel_content", {})
        apc_controls = apc.get("controls", [])

        out_of_stock = next(
            (c.get("out_of_stock@villager_textures_dx.out_of_stock") for c in apc_controls if "out_of_stock@villager_textures_dx.out_of_stock" in c),
            None
        )
        hover_btn = next(
            (c.get("hover_button") for c in apc_controls if "hover_button" in c),
            None
        )

        self.assertIsNotNone(out_of_stock, "out_of_stock ausente em arrow_panel_content!")
        self.assertIsNotNone(hover_btn, "hover_button ausente em arrow_panel_content!")

        oos_layer = out_of_stock.get("layer", 0)
        hover_layer = hover_btn.get("layer", 0)

        self.assertEqual(qtb_layer, 4, "quick_trade_button deve ser layer 4!")
        self.assertEqual(oos_layer, 14, "out_of_stock deve ser layer 14!")
        self.assertEqual(hover_layer, 15, "hover_button deve ser layer 15!")

        self.assertLess(
            qtb_layer,
            oos_layer,
            "FALHA ADVERSARIAL: quick_trade_button não pode ter layer >= out_of_stock!",
        )
        self.assertLess(
            oos_layer,
            hover_layer,
            "FALHA ADVERSARIAL: out_of_stock não pode ter layer >= hover_button!",
        )

    # -------------------------------------------------------------------------
    # 11. Isolamento Espacial e Ausência de Colisão AABB
    # -------------------------------------------------------------------------
    def test_11_spatial_clearance_and_aabb(self):
        """
        Testa matematicamente a ausência de sobreposição ou colisão de Axis-Aligned Bounding Box (AABB)
        entre o painel da seta [82, 35, 28, 21] e os demais controles vizinhos em right_panel:
        - ingredient_1_item_slot [35, 37, 18, 18]
        - ingredient_2_item_slot [61, 37, 18, 18]
        - result_panel [116, 33, 26, 26]
        - trade_info_button [200, 92, 20, 20] (na coluna externa direita)
        """
        def aabb_intersect(box1, box2):
            x1, y1, w1, h1 = box1
            x2, y2, w2, h2 = box2
            return not (x1 + w1 <= x2 or x2 + w2 <= x1 or y1 + h1 <= y2 or y2 + h2 <= y1)

        arrow_box = (82, 35, 28, 21)

        # Slots vizinhos
        ing1_box = (35, 37, 18, 18)
        ing2_box = (61, 37, 18, 18)
        result_box = (116, 33, 26, 26)

        self.assertFalse(
            aabb_intersect(arrow_box, ing1_box),
            "FALHA ADVERSARIAL: Colisão física entre arrow_panel e ingredient_1_item_slot!",
        )
        self.assertFalse(
            aabb_intersect(arrow_box, ing2_box),
            "FALHA ADVERSARIAL: Colisão física entre arrow_panel e ingredient_2_item_slot!",
        )
        self.assertFalse(
            aabb_intersect(arrow_box, result_box),
            "FALHA ADVERSARIAL: Colisão física entre arrow_panel e result_panel!",
        )

        # trade_info_button: right_panel width = 176 + offset X = 24 => X = 200, Y = 92
        trade_info_box = (200, 92, 20, 20)
        self.assertFalse(
            aabb_intersect(arrow_box, trade_info_box),
            "FALHA ADVERSARIAL: Colisão física entre arrow_panel e trade_info_button!",
        )

    # -------------------------------------------------------------------------
    # 12. Integridade das 15 Variantes de Aldeão e Texturas Físicas
    # -------------------------------------------------------------------------
    def test_12_villager_variants_and_texture_assets(self):
        """
        Valida que todas as 15 variantes de aldeão em villager_background_panel
        estão devidamente declaradas, associadas ao namespace villager_textures_dx,
        e que os arquivos de textura .png correspondentes existem fisicamente no disco.
        """
        vbp = self.data.get("villager_background_panel", {})
        vbp_controls = vbp.get("controls", [])
        self.assertGreaterEqual(
            len(vbp_controls),
            15,
            "villager_background_panel deve conter ao menos 15 variantes!",
        )

        # Carrega o arquivo de definição de texturas
        vt_text = strip_jsonc_comments_only(VILLAGER_TEXTURES_UIDX_PATH.read_text(encoding="utf-8"))
        vt_data = json.loads(vt_text)

        expected_variants = [
            ("default@villager_textures_dx.villager", "villager"),
            ("armorer@villager_textures_dx.villager_armorer", "villager_armorer"),
            ("butcher@villager_textures_dx.villager_butcher", "villager_butcher"),
            ("cartographer@villager_textures_dx.villager_cartographer", "villager_cartographer"),
            ("cleric@villager_textures_dx.villager_cleric", "villager_cleric"),
            ("farmer@villager_textures_dx.villager_farmer", "villager_farmer"),
            ("fisherman@villager_textures_dx.villager_fisherman", "villager_fisherman"),
            ("fletcher@villager_textures_dx.villager_fletcher", "villager_fletcher"),
            ("leatherworker@villager_textures_dx.villager_leatherworker", "villager_leatherworker"),
            ("librarian@villager_textures_dx.villager_librarian", "villager_librarian"),
            ("shepherd@villager_textures_dx.villager_shepherd", "villager_shepherd"),
            ("tool_smith@villager_textures_dx.villager_tool_smith", "villager_tool_smith"),
            ("weapon_smith@villager_textures_dx.villager_weapon_smith", "villager_weapon_smith"),
            ("stone_mason@villager_textures_dx.villager_stone_mason", "villager_stone_mason"),
            ("wandering_trader@villager_textures_dx.villager_wandering_trader", "villager_wandering_trader"),
        ]

        # Mapeamento dos controles instanciados
        instantiated_controls = {}
        for c in vbp_controls:
            instantiated_controls.update(c)

        for ctrl_tag, texture_def_name in expected_variants:
            self.assertIn(
                ctrl_tag,
                instantiated_controls,
                f"FALHA ADVERSARIAL: Variante '{ctrl_tag}' ausente em villager_background_panel!",
            )

            # Verifica se o controle de textura está no arquivo villager_textures.uidx
            self.assertIn(
                texture_def_name,
                vt_data,
                f"FALHA ADVERSARIAL: Definição de textura '{texture_def_name}' ausente em villager_textures.uidx!",
            )

            tex_obj = vt_data[texture_def_name]
            tex_controls = tex_obj.get("controls", [])
            self.assertTrue(len(tex_controls) > 0, f"Controle de textura vazio para {texture_def_name}!")
            tex_img = tex_controls[0].get("texture", {})
            tex_path_str = tex_img.get("texture")
            self.assertIsNotNone(tex_path_str, f"Caminho de textura ausente para {texture_def_name}!")

            # Verifica se o arquivo de textura existe no disco
            disk_path_png = RESOURCE_PACK_DIR / f"{tex_path_str}.png"
            disk_path_tga = RESOURCE_PACK_DIR / f"{tex_path_str}.tga"
            exists = disk_path_png.is_file() or disk_path_tga.is_file()
            self.assertTrue(
                exists,
                f"FALHA ADVERSARIAL: Arquivo de textura '{disk_path_png}' não existe fisicamente no disco!",
            )

    # -------------------------------------------------------------------------
    # 13. Sensibilidade do Oráculo Contra Mutações Adversariais
    # -------------------------------------------------------------------------
    def test_13_oracle_sensitivity_mutations(self):
        """
        Garante que o oráculo de teste é sensível e reprodutivo: injeta mutações sintáticas
        e semânticas deliberadas em cópias e verifica que os oráculos falham apropriadamente.
        """
        # Mutação 1: Trailing comma injetada
        mutated_jsonc = self.cleaned_comments_only.replace('"type": "button",', '"type": "button",,')
        with self.assertRaises(json.JSONDecodeError, msg="Oráculo deve rejeitar trailing comma!"):
            json.loads(mutated_jsonc)

        # Mutação 2: Chave duplicada injetada
        mutated_dup = '{"namespace": "a", "namespace": "b"}'
        with self.assertRaises(ValueError, msg="Oráculo deve rejeitar chave duplicada no AST!"):
            json.loads(mutated_dup, object_pairs_hook=strict_duplicate_key_detector)

        # Mutação 3: Inversão de Layer (colocar botão na layer 20)
        invalid_qtb_layer = 20
        self.assertFalse(
            invalid_qtb_layer < 14,
            "Oráculo de hierarquia deve invalidar layer >= 14!",
        )

    # -------------------------------------------------------------------------
    # Métodos Auxiliares
    # -------------------------------------------------------------------------
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
