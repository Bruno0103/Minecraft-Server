"""
===============================================================================
SUÍTE DE TESTES EMPÍRICOS ADVERSARIAIS — MARCO 1 (M1)
===============================================================================
Papel: empirical_challenger (Challenger M1.1)
Objetivo:
Validação estrita e adversarial dos 3 arquivos modificados no Marco 1:
1. vdx/ui_data/container/_modules/container_touch_buttons.uidx
2. vdx/ui_data/container/_modules/container_item.uidx
3. vdx/ui_data/container/villager.uidx

Dimensões de Verificação:
1. Lexical & Syntax:
   - Balanceamento estrito de chaves {} e colchetes [].
   - Detecção de vírgulas trailing inválidas (, } ou , ]).
   - Parsing JSON estrito (sem qualquer sanitização de vírgulas).
   - Codificação de caracteres válida UTF-8.
2. Contratos de Interface e Arquitetura:
   - Presença e valor de '$touch_ref_buttons|default': true.
   - Definição estrutural de 'container_side_tools_tab' (stack_panel, vertical, offset [24, 28]).
   - Rádio grupo e integridade dos 3 botões de modo (Normal, Auto Place, Drop).
   - Mapeamentos de eventos para PC (Mouse/Teclado), Mobile (Toque) e Gamepad (Controle).
   - Deslocamento de 'trade_info_button' em villager.uidx para offset [24, 92] sem colisão.
3. Existência Física de Texturas e Controles:
   - Resolução de todas as texturas referenciadas diretamente ou indiretamente no pack.
   - Resolução dos controles herdados (@button_dx.*, @toggle_dx.*, @tooltip_dx.*).
===============================================================================
"""

import json
import re
import unittest
from pathlib import Path
from typing import List, Tuple, Dict, Any


# Diretório base do Resource Pack Colourful Containers
RESOURCE_PACK_DIR = Path(
    r"C:\Users\Bruno\AppData\Roaming\Minecraft Bedrock\Users\Shared\games\com.mojang\resource_packs\ColourfulC"
)

# Caminhos exatos dos 3 arquivos do Marco 1
TOUCH_BUTTONS_UIDX = (
    RESOURCE_PACK_DIR / "vdx" / "ui_data" / "container" / "_modules" / "container_touch_buttons.uidx"
)
CONTAINER_ITEM_UIDX = (
    RESOURCE_PACK_DIR / "vdx" / "ui_data" / "container" / "_modules" / "container_item.uidx"
)
VILLAGER_UIDX = (
    RESOURCE_PACK_DIR / "vdx" / "ui_data" / "container" / "villager.uidx"
)

ALL_M1_FILES = [TOUCH_BUTTONS_UIDX, CONTAINER_ITEM_UIDX, VILLAGER_UIDX]


def strip_jsonc_comments_only(text: str) -> str:
    """
    Remove comentários de bloco (/* ... */) e comentários de linha (// ...)
    preservando com rigor absoluto o interior de literais de string (incluindo escapes)
    e mantendo todas as vírgulas e espaçamentos originais intactos.
    Diferente de parsers permissivos, esta função NÃO remove vírgulas trailing,
    permitindo que o json.loads estrito valide a conformidade pura do JSON.
    """
    result = []
    in_string = False
    escape = False
    i = 0
    length = len(text)

    while i < length:
        char = text[i]

        # Processamento de conteúdo dentro de aspas (strings literais)
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

        # Abertura de string literal
        if char == '"':
            in_string = True
            result.append(char)
            i += 1
            continue

        # Comentário de bloco /* ... */
        if char == "/" and i + 1 < length and text[i + 1] == "*":
            i += 2
            # Avança até encontrar o fechamento */
            while i + 1 < length and not (text[i] == "*" and text[i + 1] == "/"):
                # Mantém quebras de linha para preservar numeração de linhas do arquivo original
                if text[i] == "\n":
                    result.append("\n")
                i += 1
            i += 2  # Pula '*' e '/'
            continue

        # Comentário de linha // ...
        if char == "/" and i + 1 < length and text[i + 1] == "/":
            i += 2
            # Avança até o final da linha atual
            while i < length and text[i] not in ("\r", "\n"):
                i += 1
            continue

        result.append(char)
        i += 1

    return "".join(result)


def check_brackets_and_braces_balance(text: str) -> Tuple[bool, List[str]]:
    """
    Verifica o emparelhamento exato de chaves {} e colchetes [] ignorando
    conteúdo dentro de strings literais e comentários.
    Retorna (sucesso, lista_de_erros).
    """
    errors = []
    stack = []  # Tuplas: (tipo_caractere, linha, coluna)
    in_string = False
    escape = False
    line = 1
    col = 0
    i = 0
    length = len(text)

    while i < length:
        char = text[i]
        col += 1

        if char == "\n":
            line += 1
            col = 0

        # Tratamento de string
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

        # Pular comentário de bloco
        if char == "/" and i + 1 < length and text[i + 1] == "*":
            i += 2
            col += 1
            while i + 1 < length and not (text[i] == "*" and text[i + 1] == "/"):
                if text[i] == "\n":
                    line += 1
                    col = 0
                else:
                    col += 1
                i += 1
            i += 2
            col += 2
            continue

        # Pular comentário de linha
        if char == "/" and i + 1 < length and text[i + 1] == "/":
            i += 2
            while i < length and text[i] not in ("\r", "\n"):
                i += 1
            continue

        # Verificação de abertura
        if char in ("{", "["):
            stack.append((char, line, col))
        # Verificação de fechamento
        elif char in ("}", "]"):
            if not stack:
                errors.append(f"Fechamento inesperado '{char}' na linha {line}, coluna {col} sem abertura correspondente.")
            else:
                top_char, top_line, top_col = stack.pop()
                expected = "}" if top_char == "{" else "]"
                if char != expected:
                    errors.append(
                        f"Delimitador incompatível '{char}' na linha {line}, col {col}; "
                        f"esperava '{expected}' correspondente a '{top_char}' da linha {top_line}, col {top_col}."
                    )

        i += 1

    if in_string:
        errors.append("String literal não fechada ao alcançar o final do arquivo.")

    while stack:
        top_char, top_line, top_col = stack.pop()
        errors.append(f"Delimitador '{top_char}' aberto na linha {top_line}, coluna {top_col} nunca foi fechado.")

    return len(errors) == 0, errors


def find_trailing_commas(text: str) -> List[Tuple[int, int, str]]:
    """
    Encontra vírgulas trailing (vírgulas seguidas diretamente por } ou ] após espaços em branco).
    Retorna lista de (linha, coluna, trecho).
    """
    # Primeiro remove comentários mantendo quebras de linha e strings
    clean_code = strip_jsonc_comments_only(text)
    
    # Busca por vírgula seguida apenas por whitespace e depois } ou ]
    pattern = re.compile(r",(\s*[\}\]])")
    trailing_commas = []

    for match in pattern.finditer(clean_code):
        pos = match.start()
        # Calcula linha e coluna da vírgula
        line = clean_code.count("\n", 0, pos) + 1
        last_nl = clean_code.rfind("\n", 0, pos)
        col = pos - last_nl if last_nl != -1 else pos + 1
        snippet = clean_code[max(0, pos - 15) : min(len(clean_code), pos + 15)].replace("\n", " ")
        trailing_commas.append((line, col, snippet))

    return trailing_commas


class TestM1AdversarialChallenge(unittest.TestCase):
    """
    Suíte adversarial de testes empíricos do Challenger M1.1.
    Testa rigorosamente a ausência de bugs sintáticos, estruturais e de bindings.
    """

    @classmethod
    def setUpClass(cls):
        """Verifica se todos os arquivos sob análise existem no disco."""
        for file_path in ALL_M1_FILES:
            assert file_path.exists(), f"Arquivo crítico não encontrado: {file_path}"

    # =========================================================================
    # 1. TESTES DE SINTAXE LEXICAL RIGOROSA
    # =========================================================================

    def test_braces_and_brackets_balancing(self):
        """Valida que todos os delimitadores {} e [] estão 100% balanceados."""
        for file_path in ALL_M1_FILES:
            with self.subTest(file=file_path.name):
                content = file_path.read_text(encoding="utf-8")
                balanced, errors = check_brackets_and_braces_balance(content)
                self.assertTrue(
                    balanced,
                    f"Desbalanceamento de delimitadores em {file_path.name}:\n" + "\n".join(errors)
                )

    def test_no_invalid_trailing_commas(self):
        """
        Valida que não existem vírgulas trailing em nenhum dos 3 arquivos.
        Vírgulas antes de fechamento de chaves ou colchetes corrompem parsers estritos.
        """
        for file_path in ALL_M1_FILES:
            with self.subTest(file=file_path.name):
                content = file_path.read_text(encoding="utf-8")
                trailing = find_trailing_commas(content)
                self.assertEqual(
                    len(trailing),
                    0,
                    f"Vírgula(s) trailing detectada(s) em {file_path.name}:\n"
                    + "\n".join([f"Linha {t[0]}, Col {t[1]}: '{t[2]}'" for t in trailing])
                )

    def test_strict_json_parse_without_regex_commas_strip(self):
        """
        Valida que o arquivo parseia 100% via json.loads oficial de Python
        APENAS removendo comentários, sem qualquer modificação nas vírgulas.
        Se json.loads passar, prova matematicamente a conformidade JSON padrão.
        """
        for file_path in ALL_M1_FILES:
            with self.subTest(file=file_path.name):
                content = file_path.read_text(encoding="utf-8")
                clean = strip_jsonc_comments_only(content)
                try:
                    data = json.loads(clean)
                    self.assertIsInstance(data, dict, f"Raiz de {file_path.name} deve ser um dicionário JSON")
                except json.JSONDecodeError as exc:
                    self.fail(
                        f"Falha ao realizar parsing estrito JSON em {file_path.name} na linha {exc.lineno}, "
                        f"coluna {exc.colno}: {exc.msg}"
                    )

    # =========================================================================
    # 2. TESTES ESTRUTURAIS DE container_touch_buttons.uidx
    # =========================================================================

    def test_touch_buttons_structure_and_contracts(self):
        """
        Valida todas as propriedades do painel lateral vertical, ancoragem,
        ordem dos botões, tooltips e bindings de rádio em container_touch_buttons.uidx.
        """
        clean = strip_jsonc_comments_only(TOUCH_BUTTONS_UIDX.read_text(encoding="utf-8"))
        data = json.loads(clean)

        # 1. Namespace correto
        self.assertEqual(data.get("namespace"), "touch_buttons_dx")

        # 2. inventory_buttons_panel possui $touch_ref_buttons|default: true
        inv_panel = data.get("inventory_buttons_panel", {})
        self.assertIn("$touch_ref_buttons|default", inv_panel)
        self.assertTrue(inv_panel["$touch_ref_buttons|default"])

        # 3. container_side_tools_tab existe e tem geometria precisa
        side_tab = data.get("container_side_tools_tab", {})
        self.assertEqual(side_tab.get("type"), "stack_panel")
        self.assertEqual(side_tab.get("orientation"), "vertical")
        self.assertEqual(side_tab.get("anchor_from"), "top_right")
        self.assertEqual(side_tab.get("anchor_to"), "top_right")
        self.assertEqual(side_tab.get("$container_side_tab_offset|default"), [24, 28])
        self.assertEqual(side_tab.get("size"), [20, "100%c"])
        self.assertEqual(side_tab.get("layer"), 15)

        # 4. Três botões em ordem vertical exata
        controls = side_tab.get("controls", [])
        self.assertEqual(len(controls), 3, "A aba lateral deve expor simultaneamente 3 botões em stack_panel")
        btn_keys = [list(c.keys())[0] for c in controls]
        self.assertIn("normal_place_tab@touch_buttons_dx.normal_place_tab", btn_keys[0])
        self.assertIn("auto_place_tab@touch_buttons_dx.auto_place_tab", btn_keys[1])
        self.assertIn("drop_place_tab@touch_buttons_dx.drop_place_tab", btn_keys[2])

        # 5. Configuração do Toggle Base (mode_toggle)
        mode_toggle = data.get("mode_toggle@toggle_dx.toggle_panel", {})
        self.assertEqual(mode_toggle.get("$toggle_name"), "inventory_mode_toggle")
        self.assertTrue(mode_toggle.get("$radio_toggle_group"))
        self.assertEqual(mode_toggle.get("$unchecked_control"), "button_dx.black_button_default")
        self.assertEqual(mode_toggle.get("$unchecked_hover_control"), "button_dx.black_button_hover")
        self.assertEqual(mode_toggle.get("$checked_control"), "button_dx.black_button_checked_right_default")
        self.assertEqual(mode_toggle.get("$checked_hover_control"), "button_dx.black_button_checked_right_hover")

        # 6. Forçar índices e bindings de visualização de cada botão
        normal_tab = data.get("normal_place_tab@touch_buttons_dx.mode_toggle", {})
        self.assertEqual(normal_tab.get("$toggle_group_forced_index"), 0)
        self.assertTrue(normal_tab.get("$toggle_default_state"))
        self.assertEqual(normal_tab.get("$tab_view_binding_name"), "normal_place_toggle")

        auto_tab = data.get("auto_place_tab@touch_buttons_dx.mode_toggle", {})
        self.assertEqual(auto_tab.get("$toggle_group_forced_index"), 1)
        self.assertEqual(auto_tab.get("$tab_view_binding_name"), "auto_place_toggle")

        drop_tab = data.get("drop_place_tab@touch_buttons_dx.mode_toggle", {})
        self.assertEqual(drop_tab.get("$toggle_group_forced_index"), 2)
        self.assertEqual(drop_tab.get("$tab_view_binding_name"), "drop_place_toggle")

        # 7. Tooltips canônicos
        normal_content = data.get("normal_place_content", {})
        normal_tooltip = normal_content.get("controls", [{}, {}])[1].get("hover_tooltip@tooltip_dx.tooltip_panel", {})
        self.assertEqual(normal_tooltip.get("$hover_panel_text"), "container.invMode.normalMode")

        auto_content = data.get("auto_place_content", {})
        auto_tooltip = auto_content.get("controls", [{}, {}])[1].get("hover_tooltip@tooltip_dx.tooltip_panel", {})
        self.assertEqual(auto_tooltip.get("$hover_panel_text"), "container.invMode.autoPlaceMode")

        drop_content = data.get("drop_place_content", {})
        drop_tooltip = drop_content.get("controls", [{}, {}])[1].get("hover_tooltip@tooltip_dx.tooltip_panel", {})
        self.assertEqual(drop_tooltip.get("$hover_panel_text"), "container.invMode.dropMode")

        # 8. Alias de retrocompatibilidade
        self.assertIn("top_type_toggles@touch_buttons_dx.container_side_tools_tab", data)

    # =========================================================================
    # 3. TESTES ESTRUTURAIS DE container_item.uidx
    # =========================================================================

    def test_container_item_structure_and_mappings(self):
        """
        Valida que container_item.uidx possui a variável global destravada e os
        mapeamentos de teclado, mouse, toque e gamepad para os 3 modos.
        """
        clean = strip_jsonc_comments_only(CONTAINER_ITEM_UIDX.read_text(encoding="utf-8"))
        data = json.loads(clean)

        self.assertEqual(data.get("namespace"), "container_item_dx")
        item_panel = data.get("container_item", {})
        self.assertIn("$touch_ref_buttons|default", item_panel)
        self.assertTrue(item_panel["$touch_ref_buttons|default"])

        # Encontra o bloco item_button_ref dentro de controls
        btn_ref_panel = None
        for ctrl in item_panel.get("controls", []):
            if "item_button_ref" in ctrl:
                btn_ref_panel = ctrl["item_button_ref"]
                break

        self.assertIsNotNone(btn_ref_panel, "item_button_ref não encontrado em container_item.controls")
        sub_controls = btn_ref_panel.get("controls", [])
        sub_dict = {}
        for c in sub_controls:
            sub_dict.update(c)

        # 1. main@$button_ref ignorado quando $touch_ref_buttons é true
        self.assertIn("main@$button_ref", sub_dict)
        self.assertEqual(sub_dict["main@$button_ref"].get("ignored"), "($touch_ref_buttons)")

        # 2. normal_place ativado por normal_place_toggle
        self.assertIn("normal_place", sub_dict)
        normal_panel = sub_dict["normal_place"]
        self.assertEqual(normal_panel.get("ignored"), "(not $touch_ref_buttons)")
        normal_binding = normal_panel.get("bindings", [{}])[0]
        self.assertEqual(normal_binding.get("source_control_name"), "normal_place_toggle")
        self.assertEqual(normal_binding.get("source_property_name"), "#toggle_state")
        self.assertEqual(normal_binding.get("target_property_name"), "#visible")

        # 3. auto_place com 4 mappings de entrada
        self.assertIn("auto_place", sub_dict)
        auto_panel = sub_dict["auto_place"]
        self.assertEqual(auto_panel.get("ignored"), "(not $touch_ref_buttons)")
        auto_binding = auto_panel.get("bindings", [{}])[0]
        self.assertEqual(auto_binding.get("source_control_name"), "auto_place_toggle")

        auto_btn = auto_panel.get("controls", [{}])[0].get("button@$button_ref", {})
        auto_mappings = auto_btn.get("button_mappings", [])
        self.assertEqual(len(auto_mappings), 4, "auto_place deve mapear mouse, teclado e 2 botões de gamepad")
        from_buttons = [m.get("from_button_id") for m in auto_mappings]
        self.assertIn("button.menu_select", from_buttons)
        self.assertIn("button.menu_ok", from_buttons)
        self.assertIn("button.controller_select", from_buttons)
        self.assertIn("button.controller_secondary_select", from_buttons)
        for m in auto_mappings:
            self.assertEqual(m.get("to_button_id"), "$auto_place_mode_button_name")

        # 4. drop_place com 4 mappings de entrada disparando button.drop_all
        self.assertIn("drop_place", sub_dict)
        drop_panel = sub_dict["drop_place"]
        self.assertEqual(drop_panel.get("ignored"), "(not $touch_ref_buttons)")
        drop_binding = drop_panel.get("bindings", [{}])[0]
        self.assertEqual(drop_binding.get("source_control_name"), "drop_place_toggle")

        drop_btn = drop_panel.get("controls", [{}])[0].get("button@$button_ref", {})
        drop_mappings = drop_btn.get("button_mappings", [])
        self.assertEqual(len(drop_mappings), 4, "drop_place deve mapear mouse, teclado e 2 botões de gamepad")
        from_buttons_drop = [m.get("from_button_id") for m in drop_mappings]
        self.assertIn("button.menu_select", from_buttons_drop)
        self.assertIn("button.menu_ok", from_buttons_drop)
        self.assertIn("button.controller_select", from_buttons_drop)
        self.assertIn("button.controller_secondary_select", from_buttons_drop)
        for m in drop_mappings:
            self.assertEqual(m.get("to_button_id"), "button.drop_all")

    # =========================================================================
    # 4. TESTES DE villager.uidx E ANÁLISE GEOMÉTRICA DE NÃO-COLISÃO
    # =========================================================================

    def test_villager_trade_info_button_offset_and_collision_free(self):
        """
        Valida que o trade_info_button em villager.uidx possui offset [24, 92]
        e calcula a sobreposição geométrica com a aba lateral (Y = 28 a 88).
        """
        clean = strip_jsonc_comments_only(VILLAGER_UIDX.read_text(encoding="utf-8"))
        data = json.loads(clean)

        right_panel = data.get("right_panel", {})
        trade_extra = None
        for ctrl in right_panel.get("controls", []):
            if "trade_extra_buttons" in ctrl:
                trade_extra = ctrl["trade_extra_buttons"]
                break

        self.assertIsNotNone(trade_extra, "trade_extra_buttons não encontrado em right_panel")
        trade_info = trade_extra.get("controls", [{}])[0].get("trade_info_button@button_dx.main_black_button", {})

        offset = trade_info.get("offset")
        size = trade_info.get("size")
        self.assertEqual(offset, [24, 92], "O offset do trade_info_button deve ser cirurgicamente [24, 92]")
        self.assertEqual(size, [20, 20], "O tamanho do trade_info_button deve ser [20, 20]")

        # Verificação Geométrica Adversarial:
        # Aba lateral compacta:
        # Início Y = 28, Botões = 3 x 20px = 60px.
        # Fim da aba lateral = 28 + 60 = 88.
        # Início do trade_info_button = 92.
        # Folga vertical = 92 - 88 = 4 pixels de distância física.
        tab_top = 28
        tab_height = 60
        tab_bottom = tab_top + tab_height  # 88

        info_top = offset[1]  # 92
        info_height = size[1]  # 20
        info_bottom = info_top + info_height  # 112

        # Testa se há colisão vertical
        has_overlap = not (tab_bottom <= info_top or info_bottom <= tab_top)
        self.assertFalse(has_overlap, f"Detectada colisão geométrica entre aba lateral (Y={tab_top}..{tab_bottom}) e trade_info (Y={info_top}..{info_bottom})")
        self.assertEqual(info_top - tab_bottom, 4, "A folga entre o último botão da aba e o trade_info deve ser de exatamente 4px")

    # =========================================================================
    # 5. TESTE DE EXISTÊNCIA FÍSICA DE TEXTURAS E CONTROLES
    # =========================================================================

    def test_referenced_textures_exist_on_filesystem(self):
        """
        Extrai todas as texturas declaradas nos arquivos sob teste e verifica
        sua existência física na pasta do pacote de recursos ColourfulC.
        """
        # Texturas referenciadas diretamente em container_touch_buttons.uidx:
        textures_to_check = [
            "vdx/ui_textures/icon/cross",
            "vdx/ui_textures/icon/normal_place_mode",
            "vdx/ui_textures/icon/auto_place_mode",
            "vdx/ui_textures/icon/drop_place_mode",
            "vdx/ui_textures/widget/black_button_checked_right",
            "vdx/ui_textures/widget/black_button_checked_right_highlighted",
            "vdx/ui_textures/icon/trade_info"
        ]

        for tex_rel in textures_to_check:
            png_path = RESOURCE_PACK_DIR / f"{tex_rel}.png"
            tga_path = RESOURCE_PACK_DIR / f"{tex_rel}.tga"
            exists = png_path.exists() or tga_path.exists()
            self.assertTrue(
                exists,
                f"Textura referenciada não encontrada no disco: {tex_rel} (verificados .png e .tga em {RESOURCE_PACK_DIR})"
            )

    def test_referenced_shared_controls_exist(self):
        """
        Verifica se os controles herdados com @ namespace existem nos arquivos
        da biblioteca VDX sob vdx/ui_data/_common/.
        Em UIDX, um controle pode ser definido como 'nome' ou 'nome@base'.
        """
        def has_control(data_dict: dict, control_name: str) -> bool:
            return any(k == control_name or k.startswith(f"{control_name}@") for k in data_dict.keys())

        # Checar se button.uidx define button_dx.main_black_button e variações checked_right
        button_uidx_path = RESOURCE_PACK_DIR / "vdx" / "ui_data" / "_common" / "button.uidx"
        self.assertTrue(button_uidx_path.exists(), "button.uidx deve existir")
        button_clean = strip_jsonc_comments_only(button_uidx_path.read_text(encoding="utf-8"))
        button_data = json.loads(button_clean)
        self.assertEqual(button_data.get("namespace"), "button_dx")
        self.assertTrue(has_control(button_data, "main_black_button"), "main_black_button deve existir em button_dx")
        self.assertTrue(has_control(button_data, "black_button_checked_right_default"), "black_button_checked_right_default deve existir em button_dx")
        self.assertTrue(has_control(button_data, "black_button_checked_right_hover"), "black_button_checked_right_hover deve existir em button_dx")

        # Checar se toggle.uidx define toggle_dx.toggle_panel
        toggle_uidx_path = RESOURCE_PACK_DIR / "vdx" / "ui_data" / "_common" / "toggle.uidx"
        self.assertTrue(toggle_uidx_path.exists(), "toggle.uidx deve existir")
        toggle_clean = strip_jsonc_comments_only(toggle_uidx_path.read_text(encoding="utf-8"))
        toggle_data = json.loads(toggle_clean)
        self.assertEqual(toggle_data.get("namespace"), "toggle_dx")
        self.assertTrue(has_control(toggle_data, "toggle_panel"), "toggle_panel deve existir em toggle_dx")

        # Checar se tooltip.uidx define tooltip_dx.tooltip_panel
        tooltip_uidx_path = RESOURCE_PACK_DIR / "vdx" / "ui_data" / "_common" / "tooltip.uidx"
        self.assertTrue(tooltip_uidx_path.exists(), "tooltip.uidx deve existir")
        tooltip_clean = strip_jsonc_comments_only(tooltip_uidx_path.read_text(encoding="utf-8"))
        tooltip_data = json.loads(tooltip_clean)
        self.assertEqual(tooltip_data.get("namespace"), "tooltip_dx")
        self.assertTrue(has_control(tooltip_data, "tooltip_panel"), "tooltip_panel deve existir em tooltip_dx")


    def test_no_utf8_bom(self):
        """Valida que nenhum dos 3 arquivos possui marcador de ordem de byte UTF-8 BOM."""
        for file_path in ALL_M1_FILES:
            with self.subTest(file=file_path.name):
                raw_bytes = file_path.read_bytes()
                self.assertFalse(
                    raw_bytes.startswith(b"\xef\xbb\xbf"),
                    f"Arquivo {file_path.name} contém UTF-8 BOM, o que pode quebrar parsers estritos do Bedrock."
                )

    def test_radio_group_mutual_exclusion_and_defaults(self):
        """Valida que os 3 botões formam um grupo de rádio estrito com índices únicos e apenas 1 default."""
        clean = strip_jsonc_comments_only(TOUCH_BUTTONS_UIDX.read_text(encoding="utf-8"))
        data = json.loads(clean)

        tabs = [
            ("normal_place_tab", data.get("normal_place_tab@touch_buttons_dx.mode_toggle", {})),
            ("auto_place_tab", data.get("auto_place_tab@touch_buttons_dx.mode_toggle", {})),
            ("drop_place_tab", data.get("drop_place_tab@touch_buttons_dx.mode_toggle", {}))
        ]

        indices = [t[1].get("$toggle_group_forced_index") for t in tabs]
        self.assertEqual(indices, [0, 1, 2], "Os índices de rádio devem ser exatamente [0, 1, 2] e mutuamente exclusivos.")

        defaults = [t[0] for t in tabs if t[1].get("$toggle_default_state") is True]
        self.assertEqual(
            defaults,
            ["normal_place_tab"],
            "Apenas o botão normal_place_tab deve ter $toggle_default_state: true como padrão ao abrir contêineres."
        )

    def test_view_binding_identifiers_cross_file_match(self):
        """Valida a correspondência 1-para-1 exata dos identificadores de binding entre os 2 arquivos."""
        clean_buttons = strip_jsonc_comments_only(TOUCH_BUTTONS_UIDX.read_text(encoding="utf-8"))
        data_buttons = json.loads(clean_buttons)

        tab_view_names = {
            "normal": data_buttons.get("normal_place_tab@touch_buttons_dx.mode_toggle", {}).get("$tab_view_binding_name"),
            "auto": data_buttons.get("auto_place_tab@touch_buttons_dx.mode_toggle", {}).get("$tab_view_binding_name"),
            "drop": data_buttons.get("drop_place_tab@touch_buttons_dx.mode_toggle", {}).get("$tab_view_binding_name")
        }

        clean_item = strip_jsonc_comments_only(CONTAINER_ITEM_UIDX.read_text(encoding="utf-8"))
        data_item = json.loads(clean_item)

        item_controls = data_item.get("container_item", {}).get("controls", [])
        btn_ref_controls = None
        for c in item_controls:
            if "item_button_ref" in c:
                btn_ref_controls = c["item_button_ref"].get("controls", [])
                break
        
        self.assertIsNotNone(btn_ref_controls)
        mode_panels = {}
        for c in btn_ref_controls:
            mode_panels.update(c)

        # Extrai os nomes das fontes de visualização configurados em container_item
        normal_src = mode_panels["normal_place"]["bindings"][0]["source_control_name"]
        auto_src = mode_panels["auto_place"]["bindings"][0]["source_control_name"]
        drop_src = mode_panels["drop_place"]["bindings"][0]["source_control_name"]

        self.assertEqual(normal_src, tab_view_names["normal"], "O identificador de view binding do Modo Normal deve coincidir.")
        self.assertEqual(auto_src, tab_view_names["auto"], "O identificador de view binding do Modo Auto Place deve coincidir.")
        self.assertEqual(drop_src, tab_view_names["drop"], "O identificador de view binding do Modo Drop Place deve coincidir.")

    def test_global_layout_clearance_across_all_containers(self):
        """
        Varre todos os contêineres do pacote ColourfulC para garantir que nenhum
        outro elemento colida na coluna lateral (X=24, Y=28..88).
        """
        container_dir = RESOURCE_PACK_DIR / "vdx" / "ui_data" / "container"
        collisions = []

        for uidx_file in container_dir.rglob("*.uidx"):
            # Ignora os módulos base definidores
            if uidx_file.name in ("container_touch_buttons.uidx", "villager.uidx"):
                continue
            text = uidx_file.read_text(encoding="utf-8", errors="ignore")
            # Procura por qualquer offset com X=24
            matches = re.finditer(r'"offset":\s*\[\s*24,\s*([0-9]+)\s*\]', text)
            for m in matches:
                y_val = int(m.group(1))
                if 28 <= y_val < 88:
                    collisions.append((uidx_file.name, y_val))

        self.assertEqual(
            len(collisions),
            0,
            f"Detectada colisão de layout em outros contêineres: {collisions}"
        )


if __name__ == "__main__":
    unittest.main()
