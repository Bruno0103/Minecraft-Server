"""
===============================================================================
SUÍTE DE TESTES EMPÍRICOS ADVERSARIAIS DO MARCO 2 (M2)
Arquivo: tests/test_m2_adversarial_challenge.py
===============================================================================
Papel: empirical_challenger (Challenger M2.1 — Desafio Empírico e Sintático)
Objetivo:
Submeter a implementação do Marco 2 (Quick Crafting na Bancada 3x3 e
Inventário 2x2) a um rigoroso conjunto de testes adversariais automatizados,
investigando:
1. Codificação de bytes e ausência estrita de BOM UTF-8 (Byte Order Mark).
2. Balanceamento estrutural de delimitadores ({}, []).
3. Conformidade estrita JSON (ausência de trailing commas e parsing via json.loads puro).
4. Detecção estrita de chaves duplicadas no AST JSON via object_pairs_hook.
5. Detecção de nomes de controles duplicados em arrays 'controls' no UIDX.
6. Conformidade de contratos de UI da seta de fabricação e tooltip flutuante.
7. Paridade operacional do modo Auto Place ($auto_place_mode_button_name).
8. Resolução e existência física de todos os componentes VDX referenciados.
9. Análise crítica de cobertura e eficácia da suíte do Worker (test_m2_quick_craft_integrity.py).
===============================================================================
"""

import json
import re
import unittest
from pathlib import Path
from typing import Dict, Any, List, Tuple, Optional

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
WORKER_TEST_SUITE = Path(
    r"a:\BackupDesktopFileBruno\Estudos\Projetos-pessoais\Minecraft Server\tests\test_m2_quick_craft_integrity.py"
)

ALL_M2_FILES = [CRAFTING_TABLE_UIDX, INVENTORY_UIDX]


def strip_jsonc_comments_only(text: str) -> str:
    """
    Remove comentários C-style (// e /* ... */) de strings JSONC / UIDX
    preservando rigorosamente strings literais, escapes e quebras de linha,
    SEM remover vírgulas trailing. Permite testar a conformidade pura do JSON.
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

    return "".join(result)


def check_brackets_and_braces_balance(text: str) -> Tuple[bool, List[str]]:
    """
    Verifica o emparelhamento exato de chaves {} e colchetes [] ignorando
    conteúdo dentro de strings literais e comentários.
    Retorna (sucesso, lista_de_erros).
    """
    errors = []
    stack = []
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

        if char in ("{", "["):
            stack.append((char, line, col))
        elif char in ("}", "]"):
            if not stack:
                errors.append(
                    f"Fechamento inesperado '{char}' na linha {line}, coluna {col} sem abertura correspondente."
                )
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
        errors.append(
            f"Delimitador '{top_char}' aberto na linha {top_line}, coluna {top_col} nunca foi fechado."
        )

    return len(errors) == 0, errors


def find_trailing_commas(text: str) -> List[Tuple[int, int, str]]:
    """
    Encontra vírgulas trailing (vírgula seguida apenas por whitespace e depois } ou ]).
    Retorna lista de (linha, coluna, trecho).
    """
    clean_code = strip_jsonc_comments_only(text)
    pattern = re.compile(r",(\s*[\}\]])")
    trailing_commas = []

    for match in pattern.finditer(clean_code):
        pos = match.start()
        line = clean_code.count("\n", 0, pos) + 1
        last_nl = clean_code.rfind("\n", 0, pos)
        col = pos - last_nl if last_nl != -1 else pos + 1
        snippet = clean_code[max(0, pos - 15) : min(len(clean_code), pos + 15)].replace("\n", " ")
        trailing_commas.append((line, col, snippet))

    return trailing_commas


def strict_duplicate_key_detector(pairs: List[Tuple[str, Any]]) -> Dict[str, Any]:
    """
    Hook para json.loads que detecta e rejeita terminantemente qualquer chave duplicada
    dentro do mesmo escopo de objeto JSON/UIDX.
    """
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"Chave duplicada detectada no objeto JSON: '{key}'")
        result[key] = value
    return result


def find_control_recursive(data: Any, target_name: str) -> Optional[Dict[str, Any]]:
    """Busca recursivamente em estruturas de dados por um controle contendo target_name na chave."""
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


class TestM2AdversarialChallenge(unittest.TestCase):
    """
    Suíte de Testes Adversariais do Marco 2 (M2).
    Desenvolvida para comprovar empiricamente a robustez sintática e semântica
    dos arquivos de UI da bancada 3x3 e inventário 2x2.
    """

    @classmethod
    def setUpClass(cls):
        """Verifica pré-condições físicas essenciais de existência de arquivos."""
        for file_path in ALL_M2_FILES:
            assert file_path.exists(), f"Arquivo crítico não encontrado: {file_path}"
        assert WORKER_TEST_SUITE.exists(), f"Suíte do Worker não encontrada: {WORKER_TEST_SUITE}"

    # =========================================================================
    # 1. TESTES DE CODIFICAÇÃO E BYTES
    # =========================================================================

    def test_01_no_utf8_bom_and_clean_bytes(self):
        """
        Valida que nenhum arquivo possui marcador UTF-8 BOM (\xef\xbb\xbf)
        nem bytes nulos (\x00) ou caracteres de controle inválidos.
        A presença de BOM quebra o parser nativo da UI engine do Bedrock.
        """
        for file_path in ALL_M2_FILES:
            with self.subTest(file=file_path.name):
                raw_bytes = file_path.read_bytes()
                # Verifica ausência de BOM UTF-8
                self.assertFalse(
                    raw_bytes.startswith(b"\xef\xbb\xbf"),
                    f"ERRO CRÍTICO: Marcador UTF-8 BOM detectado no arquivo {file_path.name}!",
                )
                # Verifica ausência de bytes nulos
                self.assertNotIn(
                    b"\x00",
                    raw_bytes,
                    f"Byte nulo '\\x00' corrompido encontrado em {file_path.name}!",
                )
                # Verifica se decodifica estritamente em UTF-8
                try:
                    decoded = raw_bytes.decode("utf-8")
                    self.assertGreater(len(decoded), 0)
                except UnicodeDecodeError as exc:
                    self.fail(f"Falha de decodificação UTF-8 em {file_path.name}: {exc}")

    # =========================================================================
    # 2. TESTES DE SINTAXE LEXICAL RIGOROSA
    # =========================================================================

    def test_02_braces_and_brackets_strict_balancing(self):
        """
        Valida o balanceamento estrito de todos os delimitadores {} e []
        ao longo de todo o arquivo, ignorando strings e comentários.
        """
        for file_path in ALL_M2_FILES:
            with self.subTest(file=file_path.name):
                content = file_path.read_text(encoding="utf-8")
                balanced, errors = check_brackets_and_braces_balance(content)
                self.assertTrue(
                    balanced,
                    f"Desbalanceamento de delimitadores em {file_path.name}:\n" + "\n".join(errors),
                )

    def test_03_zero_trailing_commas_pure_json(self):
        """
        Valida que não existem vírgulas sobressalentes (trailing commas)
        em nenhum dos arquivos modificados.
        Garante conformidade máxima com especificações JSON restritivas.
        """
        for file_path in ALL_M2_FILES:
            with self.subTest(file=file_path.name):
                content = file_path.read_text(encoding="utf-8")
                trailing = find_trailing_commas(content)
                self.assertEqual(
                    len(trailing),
                    0,
                    f"Vírgula(s) trailing detectada(s) em {file_path.name}:\n"
                    + "\n".join([f"Linha {t[0]}, Col {t[1]}: '{t[2]}'" for t in trailing]),
                )

    def test_04_strict_json_parse_without_comma_sanitization(self):
        """
        Valida que ambos os arquivos passam pelo json.loads padrão do Python
        APENAS com a remoção de comentários (sem remoção de vírgulas por regex).
        Prova matematicamente a pureza sintática da estrutura JSON.
        """
        for file_path in ALL_M2_FILES:
            with self.subTest(file=file_path.name):
                content = file_path.read_text(encoding="utf-8")
                clean = strip_jsonc_comments_only(content)
                try:
                    data = json.loads(clean)
                    self.assertIsInstance(
                        data, dict, f"Raiz de {file_path.name} deve ser um dicionário JSON válido"
                    )
                except json.JSONDecodeError as exc:
                    self.fail(
                        f"Falha de parsing JSON estrito em {file_path.name} na linha {exc.lineno}, "
                        f"coluna {exc.colno}: {exc.msg}"
                    )

    def test_05_zero_duplicate_keys_in_json_ast(self):
        """
        Valida através de um hook especializado (strict_duplicate_key_detector)
        que nenhum objeto no arquivo possui chaves duplicadas.
        Chaves duplicadas causam sobrescrita silenciosa de propriedades na engine Bedrock.
        """
        for file_path in ALL_M2_FILES:
            with self.subTest(file=file_path.name):
                content = file_path.read_text(encoding="utf-8")
                clean = strip_jsonc_comments_only(content)
                try:
                    data = json.loads(clean, object_pairs_hook=strict_duplicate_key_detector)
                    self.assertIsInstance(data, dict)
                except ValueError as exc:
                    self.fail(f"Chave duplicada detectada em {file_path.name}: {exc}")

    def test_06_zero_duplicate_control_names_in_uiobj_arrays(self):
        """
        Varre recursivamente todas as listas 'controls' de componentes UIDX.
        Em Minecraft Bedrock, cada controle em uma lista deve ter um nome de instância único.
        Nomes colidentes causam descarte silencioso do controle irmão.
        """
        for file_path in ALL_M2_FILES:
            with self.subTest(file=file_path.name):
                content = file_path.read_text(encoding="utf-8")
                clean = strip_jsonc_comments_only(content)
                data = json.loads(clean)

                collision_errors = []

                def check_controls_array(node: Any, path_context: str):
                    if isinstance(node, dict):
                        for k, v in node.items():
                            if k == "controls" and isinstance(v, list):
                                seen_names = set()
                                for idx, ctrl in enumerate(v):
                                    if isinstance(ctrl, dict):
                                        for ctrl_key in ctrl.keys():
                                            # Extrai o nome do controle antes do '@' de herança
                                            name = ctrl_key.split("@")[0].strip()
                                            if name in seen_names:
                                                collision_errors.append(
                                                    f"Controle duplicado '{name}' ({ctrl_key}) "
                                                    f"em {path_context}.controls[{idx}]"
                                                )
                                            seen_names.add(name)
                                    check_controls_array(ctrl, f"{path_context}.controls[{idx}]")
                            else:
                                check_controls_array(v, f"{path_context}.{k}")
                    elif isinstance(node, list):
                        for idx, item in enumerate(node):
                            check_controls_array(item, f"{path_context}[{idx}]")

                check_controls_array(data, file_path.name)
                self.assertEqual(
                    len(collision_errors),
                    0,
                    f"Colisão de nomes em listas 'controls' detectada em {file_path.name}:\n"
                    + "\n".join(collision_errors),
                )

    # =========================================================================
    # 3. TESTES DE CONTRATO DE UI E QUICK CRAFTING
    # =========================================================================

    def test_07_crafting_table_arrow_contract_and_properties(self):
        """
        Valida exaustivamente os contratos da seta de crafting 3x3 em crafting_table.uidx:
        - Painel arrow: tamanho [28, 21], offset [87, 32], ancorado top_left.
        - Botão interativo: layer 4, default_control transparente (empty_panel),
          hover_control e pressed_control apontando para quick_craft_hover_panel.
        - Property bag: #collection_name='crafting_output_items', #collection_index=0.
        - Mapeamentos: menu_select e menu_ok mapeados para button.crafting_output_tertiary.
        - quick_craft_hover_panel: instanciando tooltip_dx.tooltip_panel com texto localizável.
        """
        content = CRAFTING_TABLE_UIDX.read_text(encoding="utf-8")
        clean = strip_jsonc_comments_only(content)
        data = json.loads(clean)

        # 1. Verifica declaração de quick_craft_hover_panel no namespace
        self.assertIn(
            "quick_craft_hover_panel",
            data,
            "quick_craft_hover_panel não declarado no nível raiz de crafting_table.uidx",
        )
        hover_panel = data["quick_craft_hover_panel"]
        self.assertEqual(hover_panel.get("type"), "panel")
        self.assertEqual(hover_panel.get("size"), [28, 21])

        # Verifica controles do hover panel
        ctrls = hover_panel.get("controls", [])
        self.assertTrue(len(ctrls) > 0, "quick_craft_hover_panel deve possuir controles")
        first_ctrl = ctrls[0]
        self.assertIn("hover_text_panel@tooltip_dx.tooltip_panel", first_ctrl)
        tooltip_def = first_ctrl["hover_text_panel@tooltip_dx.tooltip_panel"]
        self.assertEqual(tooltip_def.get("$hover_panel_text"), "controller.buttonTip.craftAll")
        self.assertEqual(tooltip_def.get("$hover_anchor_from"), "top_middle")
        self.assertEqual(tooltip_def.get("$hover_anchor_to"), "bottom_middle")
        self.assertEqual(tooltip_def.get("$hover_text_offset"), [0, -4])

        # 2. Localiza o painel arrow dentro de top_half_panel
        arrow_panel = find_control_recursive(data, "arrow")
        self.assertIsNotNone(arrow_panel, "Controle 'arrow' ausente na bancada 3x3")
        self.assertEqual(arrow_panel.get("size"), [28, 21])
        self.assertEqual(arrow_panel.get("offset"), [87, 32])
        self.assertEqual(arrow_panel.get("anchor_from"), "top_left")

        # 3. Inspeciona o botão dentro da seta
        btn_control = find_control_recursive(arrow_panel, "button")
        self.assertIsNotNone(btn_control, "Botão interativo ausente dentro de 'arrow'")
        self.assertEqual(btn_control.get("layer"), 4)
        self.assertEqual(btn_control.get("default_control"), "default")
        self.assertEqual(btn_control.get("hover_control"), "hover")
        self.assertEqual(btn_control.get("pressed_control"), "pressed")

        # Verifica controls do botão
        btn_controls = btn_control.get("controls", [])
        btn_ctrl_keys = [list(c.keys())[0] for c in btn_controls if isinstance(c, dict)]
        self.assertIn(
            "default@miscellaneous_dx.empty_panel",
            btn_ctrl_keys,
            "default_control deve herdar miscellaneous_dx.empty_panel para repouso transparente",
        )
        self.assertIn(
            "hover@crafting_table_mixed_dx.quick_craft_hover_panel",
            btn_ctrl_keys,
            "hover_control deve instanciar quick_craft_hover_panel",
        )
        self.assertIn(
            "pressed@crafting_table_mixed_dx.quick_craft_hover_panel",
            btn_ctrl_keys,
            "pressed_control deve instanciar quick_craft_hover_panel",
        )

        # Verifica property bag e mapeamentos
        prop_bag = btn_control.get("property_bag", {})
        self.assertEqual(prop_bag.get("#collection_name"), "crafting_output_items")
        self.assertEqual(prop_bag.get("#collection_index"), 0)

        mappings = btn_control.get("button_mappings", [])
        m_dict = {m.get("from_button_id"): m.get("to_button_id") for m in mappings}
        self.assertEqual(m_dict.get("button.menu_select"), "button.crafting_output_tertiary")
        self.assertEqual(m_dict.get("button.menu_ok"), "button.crafting_output_tertiary")

    def test_08_inventory_arrow_contract_and_properties(self):
        """
        Valida exaustivamente os contratos da seta de crafting 2x2 em inventory.uidx:
        - Painel arrow: tamanho [18, 18], offset [134, 27], ancorado top_left.
        - Botão interativo: layer 4, default_control transparente (empty_panel),
          hover_control e pressed_control instanciando tooltip_dx.tooltip_panel inline.
        - Property bag: #collection_name='crafting_output_items', #collection_index=0.
        - Mapeamentos: menu_select e menu_ok mapeados para button.crafting_output_tertiary.
        - Texto do tooltip: 'controller.buttonTip.craftAll'.
        """
        content = INVENTORY_UIDX.read_text(encoding="utf-8")
        clean = strip_jsonc_comments_only(content)
        data = json.loads(clean)

        # 1. Localiza o painel arrow dentro de top_half_panel
        arrow_panel = find_control_recursive(data, "arrow")
        self.assertIsNotNone(arrow_panel, "Controle 'arrow' ausente no inventário 2x2")
        self.assertEqual(arrow_panel.get("size"), [18, 18])
        self.assertEqual(arrow_panel.get("offset"), [134, 27])
        self.assertEqual(arrow_panel.get("anchor_from"), "top_left")

        # 2. Inspeciona o botão dentro da seta
        btn_control = find_control_recursive(arrow_panel, "button")
        self.assertIsNotNone(btn_control, "Botão interativo ausente dentro de 'arrow' 2x2")
        self.assertEqual(btn_control.get("layer"), 4)
        self.assertEqual(btn_control.get("default_control"), "default")
        self.assertEqual(btn_control.get("hover_control"), "hover")
        self.assertEqual(btn_control.get("pressed_control"), "pressed")

        # Verifica controls do botão
        btn_controls = btn_control.get("controls", [])
        ctrl_map = {}
        for c in btn_controls:
            if isinstance(c, dict):
                ctrl_map.update(c)

        self.assertIn("default@miscellaneous_dx.empty_panel", ctrl_map)
        self.assertIn("hover", ctrl_map)
        self.assertIn("pressed", ctrl_map)

        hover_ctrl = ctrl_map["hover"]
        self.assertEqual(hover_ctrl.get("size"), [18, 18])
        hover_inner = hover_ctrl.get("controls", [])
        self.assertTrue(len(hover_inner) > 0)
        self.assertIn("hover_text_panel@tooltip_dx.tooltip_panel", hover_inner[0])
        self.assertEqual(
            hover_inner[0]["hover_text_panel@tooltip_dx.tooltip_panel"].get("$hover_panel_text"),
            "controller.buttonTip.craftAll",
        )

        # Verifica property bag e mapeamentos
        prop_bag = btn_control.get("property_bag", {})
        self.assertEqual(prop_bag.get("#collection_name"), "crafting_output_items")
        self.assertEqual(prop_bag.get("#collection_index"), 0)

        mappings = btn_control.get("button_mappings", [])
        m_dict = {m.get("from_button_id"): m.get("to_button_id") for m in mappings}
        self.assertEqual(m_dict.get("button.menu_select"), "button.crafting_output_tertiary")
        self.assertEqual(m_dict.get("button.menu_ok"), "button.crafting_output_tertiary")

    def test_09_output_slot_auto_place_parity_and_no_fallback(self):
        """
        Valida que tanto a bancada 3x3 quanto o inventário 2x2 possuem rigorosamente
        '$auto_place_mode_button_name': 'button.crafting_output_tertiary' no slot
        de saída 'work_bench_output'.
        Garante que nenhum dos dois caia no fallback 'button.container_auto_place'.
        """
        for file_path in ALL_M2_FILES:
            with self.subTest(file=file_path.name):
                content = file_path.read_text(encoding="utf-8")
                clean = strip_jsonc_comments_only(content)
                data = json.loads(clean)

                output_slot = find_control_recursive(data, "work_bench_output")
                self.assertIsNotNone(
                    output_slot, f"work_bench_output ausente em {file_path.name}"
                )
                auto_place_btn = output_slot.get("$auto_place_mode_button_name")
                self.assertEqual(
                    auto_place_btn,
                    "button.crafting_output_tertiary",
                    f"{file_path.name} DEVE definir $auto_place_mode_button_name como "
                    f"'button.crafting_output_tertiary', mas tem '{auto_place_btn}'!",
                )

    # =========================================================================
    # 4. TESTES DE RESOLUÇÃO CRUZADA DE COMPONENTES VDX
    # =========================================================================

    def test_10_cross_file_component_and_namespace_resolvability(self):
        """
        Verifica se todos os componentes referenciados nas alterações existem
        nos arquivos correspondentes do Resource Pack:
        - tooltip_dx.tooltip_panel em vdx/ui_data/_common/tooltip.uidx
        - miscellaneous_dx.empty_panel em vdx/ui_data/_common/miscellaneous.uidx
        - container_item_dx.container_item em vdx/ui_data/container/_modules/container_item.uidx
        """
        tooltip_file = RESOURCE_PACK_DIR / "vdx" / "ui_data" / "_common" / "tooltip.uidx"
        misc_file = RESOURCE_PACK_DIR / "vdx" / "ui_data" / "_common" / "miscellaneous.uidx"
        container_item_file = (
            RESOURCE_PACK_DIR / "vdx" / "ui_data" / "container" / "_modules" / "container_item.uidx"
        )

        self.assertTrue(tooltip_file.exists(), f"{tooltip_file} deve existir")
        self.assertTrue(misc_file.exists(), f"{misc_file} deve existir")
        self.assertTrue(container_item_file.exists(), f"{container_item_file} deve existir")

        # Verifica presença de tooltip_panel em tooltip.uidx
        tooltip_txt = tooltip_file.read_text(encoding="utf-8")
        self.assertIn('"tooltip_panel"', tooltip_txt)

        # Verifica presença de empty_panel em miscellaneous.uidx
        misc_txt = misc_file.read_text(encoding="utf-8")
        self.assertIn('"empty_panel"', misc_txt)

        # Verifica presença de container_item em container_item.uidx
        ci_txt = container_item_file.read_text(encoding="utf-8")
        self.assertIn('"container_item"', ci_txt)

    # =========================================================================
    # 5. AUDITORIA DA SUÍTE DE TESTES DO WORKER
    # =========================================================================

    def test_11_audit_worker_test_coverage_and_quality(self):
        """
        Audita a suíte de testes unitários criada pelo worker (test_m2_quick_craft_integrity.py).
        Verifica se ela contém pelo menos 8 testes, cobre ambos os contêineres e
        testa as setas e os slots de saída sem asserções tautológicas.
        """
        worker_code = WORKER_TEST_SUITE.read_text(encoding="utf-8")

        # Verifica presença dos 8 métodos de teste esperados
        expected_tests = [
            "test_01_files_exist_and_utf8_validity",
            "test_02_jsonc_syntax_integrity",
            "test_03_crafting_table_arrow_action",
            "test_04_crafting_table_arrow_tooltip",
            "test_05_crafting_table_output_slot_auto_place",
            "test_06_inventory_arrow_action",
            "test_07_inventory_arrow_tooltip",
            "test_08_inventory_output_slot_auto_place",
        ]

        for t in expected_tests:
            self.assertIn(
                f"def {t}",
                worker_code,
                f"Método de teste '{t}' ausente na suíte do worker!",
            )

        # Verifica se não há assertTrue(True) ou asserções vazias fraudulentas
        fraudulent_patterns = [
            r"self\.assertTrue\s*\(\s*True\s*\)",
            r"self\.assertFalse\s*\(\s*False\s*\)",
            r"self\.assertEqual\s*\(\s*1\s*,\s*1\s*\)",
        ]
        for pat in fraudulent_patterns:
            matches = re.findall(pat, worker_code)
            self.assertEqual(
                len(matches),
                0,
                f"Asserção fraudulenta/tautológica encontrada na suíte do worker: {pat}",
            )

    # =========================================================================
    # 6. TESTES DE SENSIBILIDADE DO ORÁCULO CONTRA MUTAÇÕES (NEGATIVE TESTING)
    # =========================================================================

    def test_12_oracle_sensitivity_against_syntax_mutations(self):
        """
        Garante que nosso próprio oráculo não produz falsos positivos.
        Submete o parser a códigos intencionalmente corrompidos (mutantes sintáticos)
        e valida que cada falha é capturada com precisão.
        """
        # 1. Mutante: delimitador desbalanceado
        corrupted_brackets = '{"teste": [ 1, 2 }'
        balanced, errors = check_brackets_and_braces_balance(corrupted_brackets)
        self.assertFalse(balanced, "Oráculo falhou em detectar delimitadores desbalanceados!")
        self.assertGreater(len(errors), 0)

        # 2. Mutante: vírgula trailing
        corrupted_trailing = '{"item": 1, }'
        trailing = find_trailing_commas(corrupted_trailing)
        self.assertEqual(len(trailing), 1, "Oráculo falhou em detectar vírgula trailing!")

        # 3. Mutante: chave duplicada no dicionário
        corrupted_duplicate_key = '{"prop": "valor1", "prop": "valor2"}'
        with self.assertRaises(ValueError):
            json.loads(corrupted_duplicate_key, object_pairs_hook=strict_duplicate_key_detector)


if __name__ == "__main__":
    unittest.main()
