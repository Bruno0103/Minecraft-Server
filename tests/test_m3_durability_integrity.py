"""
===============================================================================
SUÍTE DE TESTES AUTOMATIZADOS DE INTEGRIDADE DO MARCO 3 (M3)
Arquivo: tests/test_m3_durability_integrity.py
===============================================================================
Objetivo:
Validar de forma automatizada, sintática, semântica e geométrica a implementação
da Durabilidade Numérica dos Itens no Resource Pack 'Colourful Containers BE: Mixed'
(ColourfulC), com foco no arquivo centralizador:
  vdx/ui_data/_common/durability_bar.uidx
e nas referências cruzadas em:
  vdx/ui_data/container/_modules/container_item.uidx
  vdx/ui_data/container/_modules/selected_item.uidx

Aspectos Verificados:
1. Existência física dos arquivos e codificação UTF-8 sem Byte Order Mark (BOM).
2. Validade sintática estrita JSONC (remoção precisa de comentários C-style e trailing commas).
3. Ausência de chaves duplicadas no AST (validador de integridade léxica/semântica).
4. Definição estrutural, tipográfica e geométrica do rótulo 'durability_label'.
5. Contrato de bindings de coleção ('#item_durability_visible' e '#item_durability_current_amount').
6. Definição estrutural, tipográfica e de camada do rótulo do cursor 'durability_label_grabbed'.
7. Contrato de bindings do cursor ('#selected_item_durability_visible' e '#selected_item_durability_current_amount').
8. Injeção direta no nível raiz de controles do painel 'durability_bar_common'.
9. Injeção direta no nível raiz de controles do painel 'durability_bar_selected_icon'.
10. Isolamento espacial e folga geométrica de quadrante contra colisão com 'stack_count_label'.
11. Integridade de referências cruzadas nos contêineres e itens selecionados.
12. Preservação de documentação explicativa em Português do Brasil e licença do autor (CrisXolt).
===============================================================================
"""

import json
import re
import unittest
from pathlib import Path
from typing import Dict, Any, List, Tuple, Optional

# Diretório base do Resource Pack Colourful Containers BE: Mixed
RESOURCE_PACK_DIR = Path(
    r"C:\Users\Bruno\AppData\Roaming\Minecraft Bedrock\Users\Shared\games\com.mojang\resource_packs\ColourfulC"
)

# Caminho do arquivo central do Marco 3
DURABILITY_BAR_UIDX = (
    RESOURCE_PACK_DIR / "vdx" / "ui_data" / "_common" / "durability_bar.uidx"
)

# Caminhos dos módulos de contêiner e cursor dependentes
CONTAINER_ITEM_UIDX = (
    RESOURCE_PACK_DIR / "vdx" / "ui_data" / "container" / "_modules" / "container_item.uidx"
)
SELECTED_ITEM_UIDX = (
    RESOURCE_PACK_DIR / "vdx" / "ui_data" / "container" / "_modules" / "selected_item.uidx"
)


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
    # Remove vírgulas sobressalentes antes do fechamento de arrays ] ou objetos }
    cleaned = re.sub(r",\s*([\]}])", r"\1", cleaned)
    return cleaned


def parse_uidx(file_path: Path) -> Dict[str, Any]:
    """
    Lê um arquivo UIDX/JSONC diretamente do disco e retorna a estrutura parseada.
    """
    raw_content = file_path.read_text(encoding="utf-8")
    clean_content = strip_jsonc_comments_and_trailing_commas(raw_content)
    return json.loads(clean_content)


def parse_uidx_with_duplicate_check(file_path: Path) -> Dict[str, Any]:
    """
    Lê e decodifica o arquivo JSONC/UIDX garantindo que nenhuma chave seja duplicada
    no mesmo nível de escopo do dicionário, levantando ValueError se houver duplicata.
    """
    raw_content = file_path.read_text(encoding="utf-8")
    clean_content = strip_jsonc_comments_and_trailing_commas(raw_content)

    def check_duplicates(ordered_pairs: List[Tuple[str, Any]]) -> Dict[str, Any]:
        result_dict = {}
        for key, value in ordered_pairs:
            if key in result_dict:
                raise ValueError(f"Chave duplicada detectada no mesmo objeto: '{key}'")
            result_dict[key] = value
        return result_dict

    return json.loads(clean_content, object_pairs_hook=check_duplicates)


class TestM3DurabilityIntegrity(unittest.TestCase):
    """
    Suíte de testes de integridade automatizada para o Marco 3 (M3).
    Valida a implementação da durabilidade numérica em itens danificados e cursor.
    """

    def setUp(self):
        """Prepara os caminhos dos arquivos para verificação."""
        self.durability_bar_path = DURABILITY_BAR_UIDX
        self.container_item_path = CONTAINER_ITEM_UIDX
        self.selected_item_path = SELECTED_ITEM_UIDX

    def test_01_files_existence_and_utf8_encoding(self):
        """
        Teste 01: Valida a existência física dos arquivos e a ausência estrita
        de Byte Order Mark (UTF-8 BOM), garantindo compatibilidade com o parser do Bedrock.
        """
        for file_path in [self.durability_bar_path, self.container_item_path, self.selected_item_path]:
            self.assertTrue(
                file_path.exists(),
                f"Arquivo obrigatório não encontrado no sistema de arquivos: {file_path}",
            )
            # Lê em modo binário para verificar os primeiros bytes
            binary_content = file_path.read_bytes()
            self.assertFalse(
                binary_content.startswith(b"\xef\xbb\xbf"),
                f"O arquivo {file_path.name} contém Byte Order Mark UTF-8 (BOM), o que corrompe o parser do Bedrock!",
            )
            # Garante que decodifica em UTF-8 puro sem exceções
            try:
                binary_content.decode("utf-8")
            except UnicodeDecodeError as exc:
                self.fail(f"Falha de decodificação UTF-8 no arquivo {file_path.name}: {exc}")

    def test_02_jsonc_syntax_and_ast_integrity(self):
        """
        Teste 02: Valida se 'durability_bar.uidx' possui sintaxe JSONC estrita válida
        e se o namespace declarado é 'durability_bar_dx'.
        """
        try:
            data = parse_uidx(self.durability_bar_path)
            self.assertIsInstance(data, dict, "A raiz do arquivo UIDX deve ser um objeto JSON.")
            self.assertEqual(
                data.get("namespace"),
                "durability_bar_dx",
                "O namespace do arquivo deve ser exatamente 'durability_bar_dx'.",
            )
        except Exception as exc:
            self.fail(f"Erro ao realizar parsing sintático JSONC de durability_bar.uidx: {exc}")

    def test_03_no_duplicate_keys_ast(self):
        """
        Teste 03: Valida que nenhuma chave foi acidentalmente duplicada no AST
        durante a injeção dos nós de durabilidade em 'durability_bar.uidx'.
        """
        try:
            data = parse_uidx_with_duplicate_check(self.durability_bar_path)
            self.assertIn("namespace", data)
        except ValueError as exc:
            self.fail(f"Violação de integridade do AST: {exc}")

    def test_04_durability_label_structural_definition(self):
        """
        Teste 04: Valida a declaração e propriedades visuais de 'durability_label',
        que herda de 'label_dx.global_label_number' para os slots convencionais.
        """
        data = parse_uidx(self.durability_bar_path)

        # Localiza o nó 'durability_label@label_dx.global_label_number' ou 'durability_label'
        label_key = None
        for key in data.keys():
            if key.startswith("durability_label@") or key == "durability_label":
                label_key = key
                break

        self.assertIsNotNone(
            label_key,
            "Controle 'durability_label@label_dx.global_label_number' não foi declarado na raiz de durability_bar.uidx",
        )
        self.assertIn(
            "label_dx.global_label_number",
            label_key,
            f"O controle '{label_key}' deve herdar explicitamente de 'label_dx.global_label_number'",
        )

        label_node = data[label_key]
        # Validação tipográfica e estética
        self.assertEqual(
            label_node.get("color"),
            [1.0, 1.0, 1.0],
            "A cor do texto deve ser branca pura [ 1.0, 1.0, 1.0 ] para máxima legibilidade.",
        )
        self.assertTrue(
            label_node.get("shadow"),
            "A propriedade 'shadow' deve ser True para projetar sombra contra texturas de itens.",
        )
        self.assertEqual(
            label_node.get("font_size"),
            "small",
            "O tamanho da fonte deve ser 'small' para não vazar a área útil de 16x16 do slot.",
        )
        # Validação geométrica (isolamento no topo central)
        self.assertEqual(
            label_node.get("anchor_from"),
            "top_middle",
            "A âncora de origem ('anchor_from') deve ser 'top_middle'.",
        )
        self.assertEqual(
            label_node.get("anchor_to"),
            "top_middle",
            "A âncora de destino ('anchor_to') deve ser 'top_middle'.",
        )
        self.assertEqual(
            label_node.get("offset"),
            [0, 1],
            "O offset deve ser exatamente [ 0, 1 ] para não ultrapassar a margem superior.",
        )
        # Validação de camada Z-index (superior à barra vanilla layer 10 e stack count layer 11)
        self.assertEqual(
            label_node.get("layer"),
            12,
            "A camada 'layer' deve ser 12 para renderizar com clareza acima do ícone e da barra.",
        )
        self.assertEqual(
            label_node.get("text"),
            "#durability_number_text",
            "A propriedade 'text' deve apontar para o binding dinâmico '#durability_number_text'.",
        )

    def test_05_durability_label_bindings_contract(self):
        """
        Teste 05: Valida o contrato de bindings de 'durability_label', garantindo
        que a visibilidade condicional venha de '#item_durability_visible' e o valor numérico
        venha de '#item_durability_current_amount' na coleção '$item_collection_name'.
        """
        data = parse_uidx(self.durability_bar_path)
        label_node = None
        for key, val in data.items():
            if key.startswith("durability_label@") or key == "durability_label":
                label_node = val
                break

        self.assertIsNotNone(label_node, "Controle 'durability_label' não encontrado.")
        bindings = label_node.get("bindings", [])
        self.assertIsInstance(bindings, list, "A propriedade 'bindings' deve ser uma lista.")
        self.assertGreaterEqual(
            len(bindings),
            2,
            "O controle 'durability_label' deve possuir ao menos 2 bindings (visibilidade e texto).",
        )

        visible_binding = None
        amount_binding = None

        for b in bindings:
            name = b.get("binding_name")
            override = b.get("binding_name_override")
            if name == "#item_durability_visible" and override == "#visible":
                visible_binding = b
            elif name == "#item_durability_current_amount" and override == "#durability_number_text":
                amount_binding = b

        self.assertIsNotNone(
            visible_binding,
            "Binding de visibilidade condicional '#item_durability_visible' -> '#visible' ausente em durability_label.",
        )
        self.assertEqual(
            visible_binding.get("binding_type"),
            "collection",
            "O binding de visibilidade deve ser do tipo 'collection'.",
        )
        self.assertEqual(
            visible_binding.get("binding_collection_name"),
            "$item_collection_name",
            "O binding de visibilidade deve referenciar a coleção '$item_collection_name'.",
        )

        self.assertIsNotNone(
            amount_binding,
            "Binding de valor numérico '#item_durability_current_amount' -> '#durability_number_text' ausente.",
        )
        self.assertEqual(
            amount_binding.get("binding_type"),
            "collection",
            "O binding de valor numérico deve ser do tipo 'collection'.",
        )
        self.assertEqual(
            amount_binding.get("binding_collection_name"),
            "$item_collection_name",
            "O binding de valor numérico deve referenciar a coleção '$item_collection_name'.",
        )

    def test_06_durability_label_grabbed_structural_definition(self):
        """
        Teste 06: Valida a declaração e propriedades de 'durability_label_grabbed',
        dedicado ao item suspenso no cursor ou manipulado pelo toque mobile.
        """
        data = parse_uidx(self.durability_bar_path)

        grabbed_key = None
        for key in data.keys():
            if key.startswith("durability_label_grabbed@") or key == "durability_label_grabbed":
                grabbed_key = key
                break

        self.assertIsNotNone(
            grabbed_key,
            "Controle 'durability_label_grabbed@label_dx.global_label_number' não encontrado em durability_bar.uidx",
        )
        self.assertIn(
            "label_dx.global_label_number",
            grabbed_key,
            f"O controle '{grabbed_key}' deve herdar de 'label_dx.global_label_number'",
        )

        grabbed_node = data[grabbed_key]
        self.assertEqual(
            grabbed_node.get("color"),
            [1.0, 1.0, 1.0],
            "A cor deve ser branca pura [ 1.0, 1.0, 1.0 ].",
        )
        self.assertTrue(
            grabbed_node.get("shadow"),
            "A sombra deve ser True.",
        )
        self.assertEqual(
            grabbed_node.get("font_size"),
            "small",
            "O tamanho da fonte deve ser 'small'.",
        )
        self.assertEqual(
            grabbed_node.get("anchor_from"),
            "top_middle",
            "A âncora deve ser 'top_middle'.",
        )
        self.assertEqual(
            grabbed_node.get("anchor_to"),
            "top_middle",
            "A âncora deve ser 'top_middle'.",
        )
        self.assertEqual(
            grabbed_node.get("offset"),
            [0, 1],
            "O offset deve ser [ 0, 1 ].",
        )
        self.assertEqual(
            grabbed_node.get("layer"),
            65,
            "O layer do rótulo suspenso deve ser 65 (acima de ícone 60, barra 61 e stack count 63).",
        )
        self.assertEqual(
            grabbed_node.get("text"),
            "#durability_number_text",
            "O texto deve ser '#durability_number_text'.",
        )

    def test_07_durability_label_grabbed_bindings_contract(self):
        """
        Teste 07: Valida o contrato de bindings de 'durability_label_grabbed',
        garantindo os bindings globais do cursor com 'always_when_visible'.
        """
        data = parse_uidx(self.durability_bar_path)
        grabbed_node = None
        for key, val in data.items():
            if key.startswith("durability_label_grabbed@") or key == "durability_label_grabbed":
                grabbed_node = val
                break

        self.assertIsNotNone(grabbed_node, "Controle 'durability_label_grabbed' ausente.")
        bindings = grabbed_node.get("bindings", [])
        self.assertGreaterEqual(
            len(bindings),
            2,
            "Deve possuir ao menos 2 bindings para o item agarrado.",
        )

        visible_binding = None
        amount_binding = None

        for b in bindings:
            name = b.get("binding_name")
            override = b.get("binding_name_override")
            if name == "#selected_item_durability_visible" and override == "#visible":
                visible_binding = b
            elif name == "#selected_item_durability_current_amount" and override == "#durability_number_text":
                amount_binding = b

        self.assertIsNotNone(
            visible_binding,
            "Binding '#selected_item_durability_visible' -> '#visible' ausente em durability_label_grabbed.",
        )
        self.assertEqual(
            visible_binding.get("binding_condition"),
            "always_when_visible",
            "O binding de visibilidade do cursor deve ter binding_condition: 'always_when_visible'.",
        )

        self.assertIsNotNone(
            amount_binding,
            "Binding '#selected_item_durability_current_amount' -> '#durability_number_text' ausente.",
        )
        self.assertEqual(
            amount_binding.get("binding_condition"),
            "always_when_visible",
            "O binding de valor numérico do cursor deve ter binding_condition: 'always_when_visible'.",
        )

    def test_08_injection_in_durability_bar_common(self):
        """
        Teste 08: Valida se 'durability_label' foi injetado na lista 'controls'
        do painel raiz 'durability_bar_common', garantindo propagação para 100% dos slots.
        """
        data = parse_uidx(self.durability_bar_path)
        self.assertIn("durability_bar_common", data, "Painel 'durability_bar_common' não encontrado.")

        common_panel = data["durability_bar_common"]
        controls = common_panel.get("controls", [])
        self.assertIsInstance(controls, list, "'controls' em durability_bar_common deve ser uma lista.")

        # Coleta todas as chaves de controles presentes na lista
        control_keys = []
        for ctrl_dict in controls:
            if isinstance(ctrl_dict, dict):
                control_keys.extend(ctrl_dict.keys())

        # Procura por chave instanciando durability_label
        has_label_instance = any(
            k.startswith("durability_label@") or k == "durability_label"
            for k in control_keys
        )
        self.assertTrue(
            has_label_instance,
            f"O controle 'durability_label' não foi encontrado na lista 'controls' de 'durability_bar_common': {control_keys}",
        )

    def test_09_injection_in_durability_bar_selected_icon(self):
        """
        Teste 09: Valida se 'durability_label_grabbed' foi injetado na lista 'controls'
        do painel raiz 'durability_bar_selected_icon' (cursor flutuante).
        """
        data = parse_uidx(self.durability_bar_path)
        self.assertIn(
            "durability_bar_selected_icon",
            data,
            "Painel 'durability_bar_selected_icon' não encontrado.",
        )

        selected_panel = data["durability_bar_selected_icon"]
        controls = selected_panel.get("controls", [])
        self.assertIsInstance(controls, list, "'controls' em durability_bar_selected_icon deve ser uma lista.")

        control_keys = []
        for ctrl_dict in controls:
            if isinstance(ctrl_dict, dict):
                control_keys.extend(ctrl_dict.keys())

        has_grabbed_instance = any(
            k.startswith("durability_label_grabbed@") or k == "durability_label_grabbed"
            for k in control_keys
        )
        self.assertTrue(
            has_grabbed_instance,
            f"O controle 'durability_label_grabbed' não foi encontrado em 'durability_bar_selected_icon': {control_keys}",
        )

    def test_10_spatial_isolation_and_quadrant_clearance(self):
        """
        Teste 10: Valida matematicamente o isolamento espacial e a ausência de colisão física
        entre o rótulo de durabilidade (quadrante superior) e os controles inferiores (stack count e barra).
        """
        data = parse_uidx(self.durability_bar_path)
        label_key = [k for k in data.keys() if "durability_label" in k and "grabbed" not in k][0]
        label_node = data[label_key]

        anchor_from = label_node.get("anchor_from")
        offset = label_node.get("offset")

        self.assertEqual(anchor_from, "top_middle")
        self.assertEqual(offset, [0, 1])

        # Altura padrão de caracteres de fonte 'small' no Bedrock: 5 pixels
        # Com offset Y = 1, o label ocupa as coordenadas verticais Y: 1 a 6.
        label_y_top = offset[1]
        label_y_bottom = label_y_top + 5  # Y = 6

        # Em um slot 16x16:
        # A barra gráfica vanilla fica ancorada em bottom_left com offset [2, -2] (Y: 13 a 14).
        bar_y_top = 16 - 2 - 1  # Y = 13
        # O stack_count fica ancorado em bottom_right com offset [1, 2] (Y: 11 a 16).
        stack_y_top = 16 - 5  # Y = 11

        # Folga vertical calculada entre o final do rótulo e o início do stack_count
        clearance_to_stack = stack_y_top - label_y_bottom
        clearance_to_bar = bar_y_top - label_y_bottom

        self.assertGreaterEqual(
            clearance_to_stack,
            5,
            f"Folga vertical insuficiente para stack_count ({clearance_to_stack}px < 5px). Risco de colisão!",
        )
        self.assertGreaterEqual(
            clearance_to_bar,
            5,
            f"Folga vertical insuficiente para a barra gráfica ({clearance_to_bar}px < 5px). Risco de colisão!",
        )

    def test_11_cross_reference_integrity(self):
        """
        Teste 11: Valida se os arquivos do sistema continuam referenciando
        corretamente os painéis centralizadores sem quebras de ligação.
        """
        container_text = self.container_item_path.read_text(encoding="utf-8")
        self.assertIn(
            "durability_bar@durability_bar_dx.durability_bar_common",
            container_text,
            "container_item.uidx deve continuar instanciando durability_bar_dx.durability_bar_common.",
        )

        selected_text = self.selected_item_path.read_text(encoding="utf-8")
        self.assertIn(
            "durability_bar_selected_icon@durability_bar_dx.durability_bar_selected_icon",
            selected_text,
            "selected_item.uidx deve continuar instanciando durability_bar_dx.durability_bar_selected_icon.",
        )

    def test_12_portuguese_documentation_and_author_license(self):
        """
        Teste 12: Valida a conformidade das regras de documentação do projeto:
        - Todo o código inserido deve conter comentários explicativos em Português do Brasil.
        - Os créditos e licença do autor original (CrisXolt) devem permanecer intactos.
        """
        raw_text = self.durability_bar_path.read_text(encoding="utf-8")

        # 1. Preservação dos créditos originais de CrisXolt
        self.assertIn(
            "CrisXolt",
            raw_text,
            "Os créditos do autor original (@CrisXolt) foram indevidamente removidos do cabeçalho!",
        )

        # 2. Preservação de comentários em português detalhando o Marco 3
        self.assertIn(
            "MARCO 3",
            raw_text,
            "Comentário identificador do MARCO 3 ausente em durability_bar.uidx.",
        )
        self.assertIn(
            "Rótulo Numérico de Durabilidade",
            raw_text,
            "Comentários explicativos em Português do Brasil ausentes nas definições de durabilidade.",
        )


if __name__ == "__main__":
    unittest.main()
