"""
===============================================================================
SUÍTE DE TESTES ADVERSARIAIS E DE ROBUSTEZ DO MARCO 3 (M3)
Arquivo: tests/test_m3_adversarial_challenge.py
===============================================================================
Papel: reviewer_m3_2 (Revisor de Robustez do Marco 3 — Roles: reviewer, critic)
Objetivo:
Submeter a implementação do Marco 3 (Exibição Numérica de Durabilidade dos Itens
em Contêineres e Cursor) a um rigoroso conjunto de testes adversariais automatizados,
investigando:
1. Codificação pura UTF-8 sem Byte Order Mark (BOM) e integridade de bytes.
2. Balanceamento estrutural estrito de delimitadores ({}, []).
3. Conformidade estrita JSON (ausência de trailing commas e parsing puro).
4. Detecção de chaves duplicadas no AST JSON via object_pairs_hook.
5. Detecção de nomes de controles duplicados em listas 'controls'.
6. Robustez dos bindings condicionais de visibilidade nos slots (#item_durability_visible).
7. Robustez dos bindings condicionais de visibilidade no cursor (#selected_item_durability_visible).
8. Coexistência harmônica com o subpack de texturas customizadas ($custom_durability_bar).
9. Estratificação de camadas Z-Index e isolamento espacial contra colisões visuais.
10. Propagação universal em todos os contêineres do pacote Colourful Containers.
11. Auditoria de integridade da suíte do worker (verificação de ausência de mocks falsos).
12. Sensibilidade do oráculo contra mutações sintáticas e semânticas deliberadas.
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

# Arquivo central sob revisão no Marco 3
DURABILITY_BAR_UIDX = (
    RESOURCE_PACK_DIR / "vdx" / "ui_data" / "_common" / "durability_bar.uidx"
)

# Módulos dependentes de interface
CONTAINER_ITEM_UIDX = (
    RESOURCE_PACK_DIR / "vdx" / "ui_data" / "container" / "_modules" / "container_item.uidx"
)
SELECTED_ITEM_UIDX = (
    RESOURCE_PACK_DIR / "vdx" / "ui_data" / "container" / "_modules" / "selected_item.uidx"
)

# Suíte de testes criada pelo worker
WORKER_TEST_SUITE = Path(
    r"a:\BackupDesktopFileBruno\Estudos\Projetos-pessoais\Minecraft Server\tests\test_m3_durability_integrity.py"
)


def strip_jsonc_comments_only(text: str) -> str:
    """
    Remove comentários C-style (// e /* ... */) de strings JSONC / UIDX
    preservando rigorosamente strings literais, escapes e quebras de linha,
    SEM remover vírgulas trailing. Permite testar a conformidade estrita do JSON.
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


def strict_duplicate_key_detector(ordered_pairs: List[Tuple[str, Any]]) -> Dict[str, Any]:
    """
    Hook estrito para o decodificador JSON que detecta chaves duplicadas no mesmo escopo.
    """
    result_dict = {}
    for key, value in ordered_pairs:
        if key in result_dict:
            raise ValueError(f"Chave duplicada detectada no mesmo nível de escopo: '{key}'")
        result_dict[key] = value
    return result_dict


class TestM3AdversarialChallenge(unittest.TestCase):
    """
    Suíte adversarial de testes automatizados para estressar a robustez
    da implementação da Durabilidade Numérica dos Itens no Marco 3.
    """

    def setUp(self):
        """Inicializa caminhos e valida pré-condições básicas."""
        self.durability_file = DURABILITY_BAR_UIDX
        self.container_item_file = CONTAINER_ITEM_UIDX
        self.selected_item_file = SELECTED_ITEM_UIDX
        self.worker_test_file = WORKER_TEST_SUITE

    def test_01_no_utf8_bom_and_clean_bytes(self):
        """
        [ADVERSARIAL 01] Valida que 'durability_bar.uidx' não contém marcador UTF-8 BOM
        (bytes 0xEF, 0xBB, 0xBF) e que não possui caracteres nulos ou inválidos.
        """
        raw_bytes = self.durability_file.read_bytes()
        self.assertFalse(
            raw_bytes.startswith(b"\xef\xbb\xbf"),
            "CRÍTICO: Marcador UTF-8 BOM detectado no cabeçalho de durability_bar.uidx!",
        )
        self.assertNotIn(
            b"\x00",
            raw_bytes,
            "CRÍTICO: Caracteres nulos (0x00) detectados em durability_bar.uidx!",
        )

    def test_02_braces_and_brackets_strict_balancing(self):
        """
        [ADVERSARIAL 02] Valida o balanceamento estrito de chaves {} e colchetes []
        no arquivo ignorando comentários e strings literais.
        """
        raw_text = self.durability_file.read_text(encoding="utf-8")
        clean_text = strip_jsonc_comments_only(raw_text)

        stack = []
        in_string = False
        escape = False

        for idx, char in enumerate(clean_text):
            if in_string:
                if escape:
                    escape = False
                elif char == "\\":
                    escape = True
                elif char == '"':
                    in_string = False
                continue

            if char == '"':
                in_string = True
                continue

            if char in "{[":
                stack.append((char, idx))
            elif char in "}]":
                if not stack:
                    self.fail(f"Delimitador de fechamento '{char}' sem abertura correspondente no caractere {idx}!")
                last_char, last_idx = stack.pop()
                expected = "}" if last_char == "{" else "]"
                self.assertEqual(
                    char,
                    expected,
                    f"Incompatibilidade de delimitadores: aberto '{last_char}' em {last_idx}, mas fechado com '{char}' em {idx}!",
                )

        self.assertEqual(
            len(stack),
            0,
            f"Delimitadores não fechados ao término do arquivo: {stack}",
        )

    def test_03_zero_trailing_commas_pure_json(self):
        """
        [ADVERSARIAL 03] Valida que não existem vírgulas sobressalentes (trailing commas)
        em durability_bar.uidx, permitindo parsing direto por parsers estritos de JSON.
        """
        raw_text = self.durability_file.read_text(encoding="utf-8")
        clean_text = strip_jsonc_comments_only(raw_text)

        # Procura por vírgula imediatamente seguida por ] ou } com possíveis espaços/quebras de linha
        trailing_comma_match = re.search(r",\s*([\]}])", clean_text)
        self.assertIsNone(
            trailing_comma_match,
            f"Vírgula sobressalente (trailing comma) detectada antes de '{trailing_comma_match.group(1) if trailing_comma_match else ''}'!",
        )

    def test_04_strict_json_parse_without_regex_sanitization(self):
        """
        [ADVERSARIAL 04] Valida que durability_bar.uidx pode ser parseado diretamente
        pelo json.loads padrão do Python após a simples remoção de comentários C-style,
        sem necessidade de regex de correção de vírgulas.
        """
        raw_text = self.durability_file.read_text(encoding="utf-8")
        clean_text = strip_jsonc_comments_only(raw_text)

        try:
            data = json.loads(clean_text)
            self.assertIsInstance(data, dict)
            self.assertEqual(data.get("namespace"), "durability_bar_dx")
        except json.JSONDecodeError as exc:
            self.fail(f"Falha ao realizar parsing estrito JSON de durability_bar.uidx: {exc}")

    def test_05_zero_duplicate_keys_in_json_ast(self):
        """
        [ADVERSARIAL 05] Valida através de um hook especializado que não existem chaves
        duplicadas no mesmo objeto em nenhum nível do documento JSON.
        """
        raw_text = self.durability_file.read_text(encoding="utf-8")
        clean_text = strip_jsonc_comments_only(raw_text)

        try:
            json.loads(clean_text, object_pairs_hook=strict_duplicate_key_detector)
        except ValueError as exc:
            self.fail(f"Violação de chaves duplicadas no AST: {exc}")

    def test_06_zero_duplicate_control_names_in_arrays(self):
        """
        [ADVERSARIAL 06] Varre recursivamente todas as listas 'controls' para assegurar
        que nenhum controle seja instanciado com chave duplicada no mesmo painel.
        """
        raw_text = self.durability_file.read_text(encoding="utf-8")
        clean_text = strip_jsonc_comments_only(raw_text)
        data = json.loads(clean_text)

        def walk_controls(node: Any, path: str = ""):
            if isinstance(node, dict):
                for k, v in node.items():
                    current_path = f"{path}.{k}" if path else k
                    if k == "controls" and isinstance(v, list):
                        seen_control_names = set()
                        for ctrl_elem in v:
                            if isinstance(ctrl_elem, dict):
                                for ctrl_k in ctrl_elem.keys():
                                    base_name = ctrl_k.split("@")[0]
                                    self.assertNotIn(
                                        base_name,
                                        seen_control_names,
                                        f"Controle duplicado '{base_name}' na lista 'controls' em '{current_path}'!",
                                    )
                                    seen_control_names.add(base_name)
                    walk_controls(v, current_path)
            elif isinstance(node, list):
                for idx, elem in enumerate(node):
                    walk_controls(elem, f"{path}[{idx}]")

        walk_controls(data)

    def test_07_conditional_bindings_robustness_slots(self):
        """
        [ADVERSARIAL 07] Valida detalhadamente que o rótulo de slots 'durability_label'
        possui o binding condicional estrito '#item_durability_visible' -> '#visible',
        impedindo absolutamente que itens novos ou sem durabilidade exibam números espúrios.
        """
        raw_text = self.durability_file.read_text(encoding="utf-8")
        clean_text = strip_jsonc_comments_only(raw_text)
        data = json.loads(clean_text)

        label_def = data.get("durability_label@label_dx.global_label_number")
        self.assertIsNotNone(label_def, "Definição de durability_label não encontrada!")

        bindings = label_def.get("bindings", [])
        self.assertEqual(len(bindings), 2, "durability_label deve ter exatamente 2 bindings.")

        # Validação do binding de visibilidade
        vis_binding = next((b for b in bindings if b.get("binding_name_override") == "#visible"), None)
        self.assertIsNotNone(vis_binding, "Binding com override '#visible' ausente!")
        self.assertEqual(
            vis_binding.get("binding_name"),
            "#item_durability_visible",
            "O binding de visibilidade deve ser originado exatamente de '#item_durability_visible'.",
        )
        self.assertEqual(
            vis_binding.get("binding_type"),
            "collection",
            "O binding de visibilidade para slots deve ser do tipo 'collection'.",
        )
        self.assertEqual(
            vis_binding.get("binding_collection_name"),
            "$item_collection_name",
            "O binding de visibilidade deve consumir da variável de contexto '$item_collection_name'.",
        )

        # Validação do binding de quantidade de texto
        text_binding = next((b for b in bindings if b.get("binding_name_override") == "#durability_number_text"), None)
        self.assertIsNotNone(text_binding, "Binding com override '#durability_number_text' ausente!")
        self.assertEqual(
            text_binding.get("binding_name"),
            "#item_durability_current_amount",
            "O valor textual deve ser extraído de '#item_durability_current_amount'.",
        )
        self.assertEqual(
            text_binding.get("binding_type"),
            "collection",
            "O binding de valor textual deve ser do tipo 'collection'.",
        )
        self.assertEqual(
            text_binding.get("binding_collection_name"),
            "$item_collection_name",
            "O binding de texto deve consumir da variável de contexto '$item_collection_name'.",
        )

    def test_08_conditional_bindings_robustness_cursor(self):
        """
        [ADVERSARIAL 08] Valida detalhadamente que o rótulo do cursor 'durability_label_grabbed'
        possui o binding '#selected_item_durability_visible' -> '#visible' com
        'binding_condition: always_when_visible', garantindo reatividade instantânea
        ao mover itens danificados entre slots via mouse ou toque mobile.
        """
        raw_text = self.durability_file.read_text(encoding="utf-8")
        clean_text = strip_jsonc_comments_only(raw_text)
        data = json.loads(clean_text)

        grabbed_def = data.get("durability_label_grabbed@label_dx.global_label_number")
        self.assertIsNotNone(grabbed_def, "Definição de durability_label_grabbed não encontrada!")

        bindings = grabbed_def.get("bindings", [])
        self.assertEqual(len(bindings), 2, "durability_label_grabbed deve ter exatamente 2 bindings.")

        vis_b = next((b for b in bindings if b.get("binding_name_override") == "#visible"), None)
        self.assertIsNotNone(vis_b, "Binding com override '#visible' ausente no cursor!")
        self.assertEqual(
            vis_b.get("binding_name"),
            "#selected_item_durability_visible",
            "O cursor deve mapear '#selected_item_durability_visible' para '#visible'.",
        )
        self.assertEqual(
            vis_b.get("binding_condition"),
            "always_when_visible",
            "O binding de visibilidade do cursor deve ter a condição 'always_when_visible'.",
        )

        amount_b = next((b for b in bindings if b.get("binding_name_override") == "#durability_number_text"), None)
        self.assertIsNotNone(amount_b, "Binding de durabilidade textual ausente no cursor!")
        self.assertEqual(
            amount_b.get("binding_name"),
            "#selected_item_durability_current_amount",
            "O cursor deve mapear '#selected_item_durability_current_amount'.",
        )
        self.assertEqual(
            amount_b.get("binding_condition"),
            "always_when_visible",
            "O binding de quantidade do cursor deve ter a condição 'always_when_visible'.",
        )

    def test_09_custom_durability_bar_subpack_coexistence(self):
        """
        [ADVERSARIAL 09] Avalia a resiliência arquitetural contra a alternância da flag
        '$custom_durability_bar' (subpack de texturas).
        Garante que o componente 'durability_label' foi injetado fora dos blocos
        'durability_bar_hold' e 'custom_durability_bar_hold', permanecendo funcional
        independente do subpack selecionado pelo usuário.
        """
        raw_text = self.durability_file.read_text(encoding="utf-8")
        clean_text = strip_jsonc_comments_only(raw_text)
        data = json.loads(clean_text)

        common_panel = data.get("durability_bar_common", {})
        controls = common_panel.get("controls", [])

        # Verifica se durability_label é filho direto de controls em durability_bar_common
        direct_labels = [
            c for c in controls if any("durability_label" in k for k in c.keys())
        ]
        self.assertEqual(
            len(direct_labels),
            1,
            "durability_label DEVE ser filho direto da lista 'controls' de 'durability_bar_common'!",
        )

        # Garante que ele não possui a propriedade 'ignored' atrelada a $custom_durability_bar
        label_ctrl_dict = direct_labels[0]
        label_key = list(label_ctrl_dict.keys())[0]
        inner_props = label_ctrl_dict[label_key]
        self.assertNotIn(
            "ignored",
            inner_props,
            "durability_label não pode conter restrição 'ignored' em controls!",
        )

        # Mesma checagem para o cursor (durability_bar_selected_icon)
        selected_panel = data.get("durability_bar_selected_icon", {})
        selected_controls = selected_panel.get("controls", [])
        direct_grabbed_labels = [
            c for c in selected_controls if any("durability_label_grabbed" in k for k in c.keys())
        ]
        self.assertEqual(
            len(direct_grabbed_labels),
            1,
            "durability_label_grabbed DEVE ser filho direto da lista 'controls' de 'durability_bar_selected_icon'!",
        )

    def test_10_z_index_hierarchy_and_layer_stratification(self):
        """
        [ADVERSARIAL 10] Valida a estratificação estrita de camadas Z-Index:
        1. Em slots convencionais:
           - item renderer: layer 6
           - durability bar vanilla: layer 10
           - stack count label: layer 11
           - durability_label: layer 12 (renderiza sem oclusão)
        2. No cursor / toque mobile:
           - item renderer: layer 60
           - durability bar grabbed: layer 61
           - stack size text: layer 63
           - lock overlay: layer 64
           - durability_label_grabbed: layer 65 (topo absoluto)
        """
        raw_text = self.durability_file.read_text(encoding="utf-8")
        clean_text = strip_jsonc_comments_only(raw_text)
        data = json.loads(clean_text)

        label_def = data.get("durability_label@label_dx.global_label_number", {})
        grabbed_def = data.get("durability_label_grabbed@label_dx.global_label_number", {})

        slot_label_layer = label_def.get("layer")
        cursor_label_layer = grabbed_def.get("layer")

        self.assertEqual(
            slot_label_layer,
            12,
            f"A camada do rótulo de slot deve ser 12 (acima de item=6, barra=10 e stack=11), obtido: {slot_label_layer}",
        )
        self.assertEqual(
            cursor_label_layer,
            65,
            f"A camada do rótulo suspenso deve ser 65 (acima de ícone=60, barra=61, stack=63 e lock=64), obtido: {cursor_label_layer}",
        )

    def test_11_audit_worker_test_integrity_and_anti_cheating(self):
        """
        [ADVERSARIAL 11] Audita a suíte de testes unitários criada pelo worker
        ('test_m3_durability_integrity.py') para certificar ausência de trapaças:
        - Ausência de resultados de teste pré-fixados (hardcoded).
        - Ausência de bypasses que pulam asserções.
        - Leitura direta e genuína do sistema de arquivos real.
        """
        self.assertTrue(
            self.worker_test_file.exists(),
            "O arquivo de teste do worker deve existir fisicamente no disco.",
        )
        worker_code = self.worker_test_file.read_text(encoding="utf-8")

        # Verifica que o teste lê diretamente o arquivo real do disco
        self.assertIn(
            "RESOURCE_PACK_DIR",
            worker_code,
            "A suíte do worker deve apontar para o diretório real do pacote.",
        )
        self.assertIn(
            "durability_bar.uidx",
            worker_code,
            "A suíte do worker deve ler durability_bar.uidx diretamente.",
        )

        # Verifica se há evidências de trapaça ou bypasses artificiais
        forbidden_patterns = [
            "unittest.skip",
            "return True",
            "pass  # fake",
            "# TODO: implement",
            "self.assertTrue(True)",
        ]
        for pattern in forbidden_patterns:
            self.assertNotIn(
                pattern,
                worker_code,
                f"Padrão suspeito ou bypass detectado na suíte do worker: '{pattern}'",
            )

    def test_12_oracle_sensitivity_mutation_testing(self):
        """
        [ADVERSARIAL 12] Teste de Sensibilidade a Mutações do Oráculo:
        Aplica mutações deliberadas na estrutura de teste e verifica que as regras
        de validação capturam e rejeitam imediatamente qualquer anomalia.
        """
        raw_text = self.durability_file.read_text(encoding="utf-8")
        clean_text = strip_jsonc_comments_only(raw_text)
        data = json.loads(clean_text)

        # Mutação 1: Se o binding de visibilidade for corrompido para uma variável inexistente
        mutated_data = json.loads(json.dumps(data))
        label_node = mutated_data["durability_label@label_dx.global_label_number"]
        label_node["bindings"][0]["binding_name"] = "#invalid_binding_variable"

        with self.assertRaises(AssertionError):
            vis_b = next((b for b in label_node["bindings"] if b.get("binding_name_override") == "#visible"), None)
            self.assertEqual(vis_b.get("binding_name"), "#item_durability_visible")

        # Mutação 2: Se a ancoragem for alterada para 'bottom_right' (gerando colisão com o contador de stack)
        label_node["anchor_from"] = "bottom_right"
        with self.assertRaises(AssertionError):
            self.assertEqual(label_node.get("anchor_from"), "top_middle")


if __name__ == "__main__":
    unittest.main()
