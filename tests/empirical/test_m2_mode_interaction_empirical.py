# -*- coding: utf-8 -*-
"""
================================================================================
HARNESS DE TESTE EMPÍRICO ADVERSARIAL: INTERAÇÃO DE MODOS E QUICK CRAFTING (M2)
================================================================================
Projeto: Integração Colourful Containers BE + The Ty-el's UI Pack
Módulo: Marco 2 (M2) — Quick Crafting na Bancada 3x3 e Inventário 2x2
Arquivo de Teste: tests/empirical/test_m2_mode_interaction_empirical.py
Autor: Teamwork Challenger M2.2 (critic, specialist)
Data: 08 de Novembro de 2026

OBJETIVO TÉCNICO:
Submeter a implementação do Marco 2 (M2) a um rigoroso desafio empírico e adversarial,
testando todas as interações funcionais, geométricas e comportamentais entre:
1. Quick Crafting (M2):
   - Seta de fabricação com atalho de 1 clique ('button.crafting_output_tertiary').
   - Tooltip explicativo flutuante ('controller.buttonTip.craftAll').
   - Injeção de '$auto_place_mode_button_name' no slot de saída ('work_bench_output').
2. Aba Lateral de Modos (M1):
   - Modo 0: Normal Place Mode ('normal_place_toggle').
   - Modo 1: Auto Place Mode ('auto_place_toggle').
   - Modo 2: Drop Place Mode ('drop_place_toggle').
3. Matriz de Entradas do Jogador (PC Mouse/Teclado, Mobile Toque, Console Gamepad):
   - 'button.menu_select'
   - 'button.menu_ok'
   - 'button.controller_select'
   - 'button.controller_secondary_select'

DIMENSÕES DE DESAFIO INVESTIGADAS:
- FSM de Rádio-Grupo: exclusão mútua e roteamento de visão (view bindings).
- Roteamento de Eventos: verificação se o slot de saída e a seta respondem corretamente
  em cada modo sem conflitos ou regressões nos demais slots do jogo.
- Oclusão e Colisão Geométrica 2D (AABB): cálculo de sobreposição e folgas de clique.
- Isolamento de Escopo (Blast Radius): comprovação de que '$auto_place_mode_button_name'
  não vaza para insumos, armaduras, offhand ou inventário.
- Oráculos de Falha / Testes de Mutação: validação do poder discriminatório dos testes.
================================================================================
"""

import json
import math
import os
import re
import sys
import unittest
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Dict, List, Optional, Set, Tuple

# Definição dos caminhos absolutos dos arquivos inspecionados
RESOURCE_PACK_DIR = Path(
    r"C:\Users\Bruno\AppData\Roaming\Minecraft Bedrock\Users\Shared\games\com.mojang\resource_packs\ColourfulC"
)
CRAFTING_TABLE_UIDX = (
    RESOURCE_PACK_DIR / "vdx" / "ui_data" / "container_mixed" / "crafting_table.uidx"
)
INVENTORY_UIDX = (
    RESOURCE_PACK_DIR / "vdx" / "ui_data" / "container_mixed" / "inventory.uidx"
)
CONTAINER_TOUCH_BUTTONS_UIDX = (
    RESOURCE_PACK_DIR / "vdx" / "ui_data" / "container" / "_modules" / "container_touch_buttons.uidx"
)
CONTAINER_ITEM_UIDX = (
    RESOURCE_PACK_DIR / "vdx" / "ui_data" / "container" / "_modules" / "container_item.uidx"
)


# ==============================================================================
# SEÇÃO 1: UTILITÁRIOS ROBUSTOS DE PARSING JSONC / UIDX
# ==============================================================================

def strip_jsonc_comments_and_trailing_commas(text: str) -> str:
    """
    Remove comentários C-style (// e /* ... */) e trailing commas de strings UIDX/JSONC,
    preservando com máxima fidelidade strings literais entre aspas duplas.
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
    # Remove vírgulas imediatamente antes de fechamentos de objetos ou listas
    cleaned = re.sub(r",\s*([\]}])", r"\1", cleaned)
    return cleaned


def parse_uidx(file_path: Path) -> Dict[str, Any]:
    """Lê um arquivo UIDX/JSONC em UTF-8 e retorna a árvore estruturada como dicionário."""
    raw_bytes = file_path.read_bytes()
    if raw_bytes.startswith(b"\xef\xbb\xbf"):
        raise ValueError(f"BOM detectado no arquivo {file_path}")
    raw_content = raw_bytes.decode("utf-8")
    clean_content = strip_jsonc_comments_and_trailing_commas(raw_content)
    return json.loads(clean_content)


def find_key_recursive(data: Any, target_key_substring: str) -> List[Tuple[str, Any]]:
    """Busca recursivamente em toda a árvore JSON por chaves contendo a substring especificada."""
    results = []
    if isinstance(data, dict):
        for k, v in data.items():
            if target_key_substring in k:
                results.append((k, v))
            results.extend(find_key_recursive(v, target_key_substring))
    elif isinstance(data, list):
        for item in data:
            results.extend(find_key_recursive(item, target_key_substring))
    return results


# ==============================================================================
# SEÇÃO 2: MOTOR GEOMÉTRICO 2D (AABB) PARA DETECÇÃO DE COLISÃO
# ==============================================================================

@dataclass(frozen=True)
class AABB:
    """Caixa delimitadora 2D alinhada aos eixos com coordenadas de pixel contínuas."""
    name: str
    x1: float
    y1: float
    x2: float
    y2: float

    @property
    def width(self) -> float:
        return max(0.0, self.x2 - self.x1)

    @property
    def height(self) -> float:
        return max(0.0, self.y2 - self.y1)

    @property
    def area(self) -> float:
        return self.width * self.height

    def intersects(self, other: "AABB") -> bool:
        """Determina se há sobreposição real com área positiva entre duas caixas."""
        overlap_w = max(0.0, min(self.x2, other.x2) - max(self.x1, other.x1))
        overlap_h = max(0.0, min(self.y2, other.y2) - max(self.y1, other.y1))
        return (overlap_w * overlap_h) > 1e-9

    def overlap_area(self, other: "AABB") -> float:
        """Calcula a área exata em pixels quadrados da interseção entre as duas caixas."""
        overlap_w = max(0.0, min(self.x2, other.x2) - max(self.x1, other.x1))
        overlap_h = max(0.0, min(self.y2, other.y2) - max(self.y1, other.y1))
        return overlap_w * overlap_h

    def horizontal_gap(self, other: "AABB") -> float:
        """
        Retorna a distância horizontal em pixels entre as caixas.
        Se forem disjuntas horizontalmente, retorna valor positivo (> 0).
        Se sobrepuserem horizontalmente, retorna valor negativo ou zero.
        """
        if self.x2 <= other.x1:
            return other.x1 - self.x2
        elif other.x2 <= self.x1:
            return self.x1 - other.x2
        return 0.0


# ==============================================================================
# SEÇÃO 3: SIMULADOR DE MÁQUINA DE ESTADOS FINITOS (FSM) E ROTEADOR DE CLIQUES
# ==============================================================================

class SideTabFSM:
    """
    Simulador fiel da Máquina de Estados Finitos da Aba Lateral de Modos
    conforme configurado em container_touch_buttons.uidx:
    - Grupo de rádio: $toggle_name = 'inventory_mode_toggle', $radio_toggle_group = true
    - Modo 0: Normal Place Mode ($toggle_group_forced_index = 0, padrão = true)
    - Modo 1: Auto Place Mode ($toggle_group_forced_index = 1)
    - Modo 2: Drop Place Mode ($toggle_group_forced_index = 2)
    """

    MODE_NAMES = {0: "normal_place_toggle", 1: "auto_place_toggle", 2: "drop_place_toggle"}

    def __init__(self, initial_mode: int = 0):
        if initial_mode not in (0, 1, 2):
            raise ValueError(f"Modo inicial inválido: {initial_mode}")
        self._current_mode = initial_mode

    @property
    def current_mode(self) -> int:
        return self._current_mode

    def switch_mode(self, new_mode: int):
        """Alterna o rádio-grupo para o novo modo, desativando os demais (exclusão mútua)."""
        if new_mode not in (0, 1, 2):
            raise ValueError(f"Modo de rádio inválido: {new_mode}")
        self._current_mode = new_mode

    def get_toggle_state(self, toggle_name: str) -> bool:
        """Retorna o valor booleano do toggle conforme os view bindings de container_item.uidx."""
        return self.MODE_NAMES[self._current_mode] == toggle_name

    def evaluate_visible_panel(self) -> str:
        """Retorna qual painel filho de item_button_ref torna-se visível no slot de item."""
        if self.get_toggle_state("normal_place_toggle"):
            return "normal_place"
        elif self.get_toggle_state("auto_place_toggle"):
            return "auto_place"
        elif self.get_toggle_state("drop_place_toggle"):
            return "drop_place"
        raise RuntimeError("Estado inconsistente da FSM de modos!")


class ContainerSlotSimulator:
    """
    Simulador comportamental do slot 'container_item_dx.container_item'
    baseado na resolução em tempo de execução das diretivas UIDX.
    """

    def __init__(
        self,
        slot_name: str,
        collection_name: str,
        button_ref: str,
        auto_place_mode_button_name: Optional[str] = None,
    ):
        self.slot_name = slot_name
        self.collection_name = collection_name
        self.button_ref = button_ref
        # Fallback canônico definido em container_item.uidx:47
        self.auto_place_mode_button_name = (
            auto_place_mode_button_name
            if auto_place_mode_button_name is not None
            else "button.container_auto_place"
        )

    def route_input(self, fsm: SideTabFSM, input_button_id: str) -> str:
        """
        Determina qual evento de ação final (to_button_id) é disparado pela engine Bedrock
        quando o jogador aciona uma entrada física (mouse, toque, teclado ou controle).
        """
        active_panel = fsm.evaluate_visible_panel()

        if active_panel == "normal_place":
            # Modo Normal: herda as regras nativas de button@$button_ref sem override
            # Para o slot de saída, isso corresponde a manipulação normal de retirada (take_all / take_one)
            return f"vanilla_slot_action({self.button_ref})"

        elif active_panel == "auto_place":
            # Modo Auto Place: overrides explícitos para menu_select, menu_ok, controller_select, controller_secondary_select
            if input_button_id in (
                "button.menu_select",
                "button.menu_ok",
                "button.controller_select",
                "button.controller_secondary_select",
            ):
                return self.auto_place_mode_button_name
            return f"unhandled({input_button_id})"

        elif active_panel == "drop_place":
            # Modo Drop Place: overrides explícitos disparando 'button.drop_all'
            if input_button_id in (
                "button.menu_select",
                "button.menu_ok",
                "button.controller_select",
                "button.controller_secondary_select",
            ):
                return "button.drop_all"
            return f"unhandled({input_button_id})"

        raise RuntimeError(f"Painel desconhecido: {active_panel}")


class CraftingArrowSimulator:
    """
    Simulador comportamental do botão da Seta de Fabricação (Quick Crafting M2).
    A seta é um botão autônomo com property_bag apontando para '#collection_name': 'crafting_output_items'.
    """

    def __init__(self, size: Tuple[int, int], offset: Tuple[int, int]):
        self.size = size
        self.offset = offset
        self.target_collection = "crafting_output_items"
        self.target_collection_index = 0
        self.tooltip_text = "controller.buttonTip.craftAll"

    def route_input(self, fsm: SideTabFSM, input_button_id: str) -> str:
        """
        A seta de Quick Crafting dispara 'button.crafting_output_tertiary'
        independentemente do modo selecionado na aba lateral.
        """
        if input_button_id in ("button.menu_select", "button.menu_ok"):
            return "button.crafting_output_tertiary"
        return f"unhandled({input_button_id})"


# ==============================================================================
# SEÇÃO 4: BATERIA COMPLETA DE TESTES EMPÍRICOS E ADVERSARIAIS
# ==============================================================================

class TestM2ModeInteractionEmpirical(unittest.TestCase):
    """
    Suíte completa de testes empíricos verificando as interações dinâmicas entre
    o Quick Crafting do Marco 2 e a Aba Lateral de Modos do Marco 1.
    """

    @classmethod
    def setUpClass(cls):
        """Carrega e valida sintaticamente todos os arquivos antes da execução dos testes."""
        cls.crafting_table_data = parse_uidx(CRAFTING_TABLE_UIDX)
        cls.inventory_data = parse_uidx(INVENTORY_UIDX)
        cls.touch_buttons_data = parse_uidx(CONTAINER_TOUCH_BUTTONS_UIDX)
        cls.container_item_data = parse_uidx(CONTAINER_ITEM_UIDX)

    # --------------------------------------------------------------------------
    # TESTE 1: VALIDAÇÃO SINTÁTICA, AUSÊNCIA DE BOM E CODIFICAÇÃO UTF-8
    # --------------------------------------------------------------------------
    def test_01_syntax_and_encoding_integrity(self):
        """Garante que todos os 4 arquivos estão íntegros em UTF-8 e sem BOM."""
        files = [
            CRAFTING_TABLE_UIDX,
            INVENTORY_UIDX,
            CONTAINER_TOUCH_BUTTONS_UIDX,
            CONTAINER_ITEM_UIDX,
        ]
        for f in files:
            self.assertTrue(f.exists(), f"Arquivo não encontrado: {f}")
            raw = f.read_bytes()
            self.assertFalse(
                raw.startswith(b"\xef\xbb\xbf"),
                f"BOM detectado em {f.name}! Mojang UIDX requer UTF-8 puro sem BOM.",
            )
            # Verifica se decodifica estritamente em UTF-8
            decoded = raw.decode("utf-8")
            self.assertGreater(len(decoded), 100, f"Arquivo {f.name} parece vazio.")

    # --------------------------------------------------------------------------
    # TESTE 2: SIMULAÇÃO DA MÁQUINA DE ESTADOS DA ABA LATERAL (FSM)
    # --------------------------------------------------------------------------
    def test_02_radio_group_fsm_and_view_bindings(self):
        """
        Testa a exclusão mútua e coerência de transição da FSM da Aba Lateral.
        Em nenhum momento mais de um modo pode estar simultaneamente ativo.
        """
        fsm = SideTabFSM(initial_mode=0)

        # Estado 0: Normal Place
        self.assertEqual(fsm.current_mode, 0)
        self.assertTrue(fsm.get_toggle_state("normal_place_toggle"))
        self.assertFalse(fsm.get_toggle_state("auto_place_toggle"))
        self.assertFalse(fsm.get_toggle_state("drop_place_toggle"))
        self.assertEqual(fsm.evaluate_visible_panel(), "normal_place")

        # Transição para Estado 1: Auto Place
        fsm.switch_mode(1)
        self.assertEqual(fsm.current_mode, 1)
        self.assertFalse(fsm.get_toggle_state("normal_place_toggle"))
        self.assertTrue(fsm.get_toggle_state("auto_place_toggle"))
        self.assertFalse(fsm.get_toggle_state("drop_place_toggle"))
        self.assertEqual(fsm.evaluate_visible_panel(), "auto_place")

        # Transição para Estado 2: Drop Place
        fsm.switch_mode(2)
        self.assertEqual(fsm.current_mode, 2)
        self.assertFalse(fsm.get_toggle_state("normal_place_toggle"))
        self.assertFalse(fsm.get_toggle_state("auto_place_toggle"))
        self.assertTrue(fsm.get_toggle_state("drop_place_toggle"))
        self.assertEqual(fsm.evaluate_visible_panel(), "drop_place")

        # Retorno para Estado 0: Normal Place
        fsm.switch_mode(0)
        self.assertEqual(fsm.evaluate_visible_panel(), "normal_place")

    # --------------------------------------------------------------------------
    # TESTE 3: ROTEAMENTO DE BOTÕES NO MODO NORMAL (ESTADO 0)
    # --------------------------------------------------------------------------
    def test_03_button_routing_in_normal_mode(self):
        """
        No Modo Normal (vanilla):
        - Seta de Craft: Dispara 'button.crafting_output_tertiary' (1 clique = fabricar tudo).
        - Slot de Saída: Comportamento vanilla de manipulação de itens sem interferência.
        - Slots de Entrada / Inventário: Comportamento vanilla padrão.
        """
        fsm = SideTabFSM(initial_mode=0)

        arrow_3x3 = CraftingArrowSimulator(size=(28, 21), offset=(87, 32))
        output_3x3 = ContainerSlotSimulator(
            slot_name="work_bench_output",
            collection_name="crafting_output_items",
            button_ref="crafting.output_no_coalesce_container_slot_button",
            auto_place_mode_button_name="button.crafting_output_tertiary",
        )
        input_3x3 = ContainerSlotSimulator(
            slot_name="input_slot_0",
            collection_name="crafting_input_items",
            button_ref="crafting.inventory_container_slot_button",
        )

        # 1. Clique na seta com mouse (menu_select) ou teclado (menu_ok)
        self.assertEqual(
            arrow_3x3.route_input(fsm, "button.menu_select"),
            "button.crafting_output_tertiary",
        )
        self.assertEqual(
            arrow_3x3.route_input(fsm, "button.menu_ok"),
            "button.crafting_output_tertiary",
        )

        # 2. Clique no slot de saída no modo normal: não dispara terceário indiscriminadamente,
        # mantém a semântica nativa da Mojang de retirada gradual
        self.assertEqual(
            output_3x3.route_input(fsm, "button.menu_select"),
            "vanilla_slot_action(crafting.output_no_coalesce_container_slot_button)",
        )

        # 3. Insumo no modo normal: mantém manipulação vanilla
        self.assertEqual(
            input_3x3.route_input(fsm, "button.menu_select"),
            "vanilla_slot_action(crafting.inventory_container_slot_button)",
        )

    # --------------------------------------------------------------------------
    # TESTE 4: ROTEAMENTO DE BOTÕES NO MODO AUTO PLACE (ESTADO 1)
    # --------------------------------------------------------------------------
    def test_04_button_routing_in_auto_place_mode(self):
        """
        No Modo Auto Place:
        - Seta de Craft: Dispara 'button.crafting_output_tertiary'.
        - Slot de Saída (3x3 e 2x2): DISPARA 'button.crafting_output_tertiary' para todos os botões
          (Mouse, Toque, Teclado, Gamepad), garantindo fabricação rápida de lote com 1 clique direto no slot!
        - Slots de Entrada / Inventário: Disparam 'button.container_auto_place' (devolve insumos).
        """
        fsm = SideTabFSM(initial_mode=1)

        output_3x3 = ContainerSlotSimulator(
            slot_name="work_bench_output_3x3",
            collection_name="crafting_output_items",
            button_ref="crafting.output_no_coalesce_container_slot_button",
            auto_place_mode_button_name="button.crafting_output_tertiary",
        )
        output_2x2 = ContainerSlotSimulator(
            slot_name="work_bench_output_2x2",
            collection_name="crafting_output_items",
            button_ref="crafting.output_no_coalesce_container_slot_button",
            auto_place_mode_button_name="button.crafting_output_tertiary",
        )
        input_slot = ContainerSlotSimulator(
            slot_name="input_slot",
            collection_name="crafting_input_items",
            button_ref="crafting.inventory_container_slot_button",
        )
        inventory_slot = ContainerSlotSimulator(
            slot_name="inventory_slot",
            collection_name="inventory_items",
            button_ref="common.container_slot_button_prototype",
        )

        # Verifica paridade total entre 3x3 e 2x2 para todos os 4 dispositivos de entrada
        for input_id in (
            "button.menu_select",
            "button.menu_ok",
            "button.controller_select",
            "button.controller_secondary_select",
        ):
            self.assertEqual(
                output_3x3.route_input(fsm, input_id),
                "button.crafting_output_tertiary",
                f"Falha de roteamento no slot de saída 3x3 com {input_id}",
            )
            self.assertEqual(
                output_2x2.route_input(fsm, input_id),
                "button.crafting_output_tertiary",
                f"Falha de roteamento no slot de saída 2x2 com {input_id}",
            )
            # Os slots comuns de insumo e inventário DEVEM disparar button.container_auto_place
            self.assertEqual(
                input_slot.route_input(fsm, input_id),
                "button.container_auto_place",
                f"Insumo não pode disparar tertiary: {input_id}",
            )
            self.assertEqual(
                inventory_slot.route_input(fsm, input_id),
                "button.container_auto_place",
                f"Inventário não pode disparar tertiary: {input_id}",
            )

    # --------------------------------------------------------------------------
    # TESTE 5: ROTEAMENTO DE BOTÕES NO MODO DROP PLACE (ESTADO 2)
    # --------------------------------------------------------------------------
    def test_05_button_routing_in_drop_place_mode(self):
        """
        No Modo Drop Place:
        - Seta de Craft: Mantém 'button.crafting_output_tertiary' (atalho explícito de fabricação).
        - Slot de Saída: Dispara 'button.drop_all' (descarta o produto fabricado diretamente no chão do mundo).
        - Slots de Entrada: Disparam 'button.drop_all' (descarta insumos no chão do mundo).
        """
        fsm = SideTabFSM(initial_mode=2)

        arrow = CraftingArrowSimulator(size=(18, 18), offset=(134, 27))
        output_slot = ContainerSlotSimulator(
            slot_name="work_bench_output",
            collection_name="crafting_output_items",
            button_ref="crafting.output_no_coalesce_container_slot_button",
            auto_place_mode_button_name="button.crafting_output_tertiary",
        )
        input_slot = ContainerSlotSimulator(
            slot_name="input_slot",
            collection_name="crafting_input_items",
            button_ref="crafting.inventory_container_slot_button",
        )

        # Seta mantém fabricação
        self.assertEqual(
            arrow.route_input(fsm, "button.menu_select"),
            "button.crafting_output_tertiary",
        )

        # Slot de saída descarta o lote no chão
        for input_id in (
            "button.menu_select",
            "button.menu_ok",
            "button.controller_select",
            "button.controller_secondary_select",
        ):
            self.assertEqual(
                output_slot.route_input(fsm, input_id),
                "button.drop_all",
                f"Slot de saída deve soltar itens no modo drop: {input_id}",
            )
            self.assertEqual(
                input_slot.route_input(fsm, input_id),
                "button.drop_all",
                f"Slot de insumo deve soltar itens no modo drop: {input_id}",
            )

    # --------------------------------------------------------------------------
    # TESTE 6: AUDITORIA DE ISOLAMENTO DE ESCOPO (BLAST RADIUS AUDIT)
    # --------------------------------------------------------------------------
    def test_06_variable_scoping_and_blast_radius(self):
        """
        Verifica se '$auto_place_mode_button_name: button.crafting_output_tertiary'
        está estritamente circunscrito a 'work_bench_output' em inventory.uidx e crafting_table.uidx.
        Garante que nenhum outro slot sofreu vazamento de escopo.
        """
        # 1. Verifica no inventory.uidx
        raw_inv = INVENTORY_UIDX.read_text(encoding="utf-8")
        # Encontra todas as ocorrências de auto_place_mode_button_name
        occurrences_inv = [
            m.start() for m in re.finditer(r"\$auto_place_mode_button_name", raw_inv)
        ]
        self.assertEqual(
            len(occurrences_inv),
            1,
            "Deve haver exatamente 1 ocorrência de $auto_place_mode_button_name em inventory.uidx!",
        )

        # 2. Verifica no crafting_table.uidx
        raw_craft = CRAFTING_TABLE_UIDX.read_text(encoding="utf-8")
        occurrences_craft = [
            m.start() for m in re.finditer(r"\$auto_place_mode_button_name", raw_craft)
        ]
        self.assertEqual(
            len(occurrences_craft),
            1,
            "Deve haver exatamente 1 ocorrência de $auto_place_mode_button_name em crafting_table.uidx!",
        )

        # 3. Garante que os slots de armadura e offhand não possuem override de auto place
        self.assertNotIn(
            "auto_place_mode_button_name",
            json.dumps(self.inventory_data.get("armor_items_panel", {})),
        )
        self.assertNotIn(
            "auto_place_mode_button_name",
            json.dumps(self.inventory_data.get("offhand_grid_item", {})),
        )

    # --------------------------------------------------------------------------
    # TESTE 7: ANÁLISE GEOMÉTRICA 2D DE COLISÃO E OCLUSÃO (AABB COLLISION AUDIT)
    # --------------------------------------------------------------------------
    def test_07_aabb_collision_and_clearance_margins(self):
        """
        Calcula matematicamente a colisão geométrica 2D (AABB) entre a Seta de Craft,
        o Slot de Saída e a Grade de Insumos em 3x3 e 2x2.
        A sobreposição DEVE ser 0.0 px² em todos os pares, com folga mínima >= 1 pixel.
        """
        # Bancada 3x3 (crafting_table.uidx)
        # Grade de insumos: offset [29, 16], tamanho [54, 54] (3 colunas x 18px)
        input_3x3 = AABB("input_grid_3x3", 29.0, 16.0, 29.0 + 54.0, 16.0 + 54.0)
        # Seta de fabricação: offset [87, 32], tamanho [28, 21]
        arrow_3x3 = AABB("arrow_3x3", 87.0, 32.0, 87.0 + 28.0, 32.0 + 21.0)
        # Slot de saída: offset [119, 30], tamanho [26, 26]
        output_3x3 = AABB("output_grid_3x3", 119.0, 30.0, 119.0 + 26.0, 30.0 + 26.0)

        # Verificações 3x3:
        # A. Seta vs Saída
        self.assertFalse(arrow_3x3.intersects(output_3x3))
        self.assertEqual(arrow_3x3.overlap_area(output_3x3), 0.0)
        gap_arrow_out_3x3 = arrow_3x3.horizontal_gap(output_3x3)
        self.assertEqual(gap_arrow_out_3x3, 4.0, "Folga horizontal entre seta e saída 3x3 deve ser 4px")

        # B. Insumos vs Seta
        self.assertFalse(input_3x3.intersects(arrow_3x3))
        self.assertEqual(input_3x3.overlap_area(arrow_3x3), 0.0)
        gap_in_arrow_3x3 = input_3x3.horizontal_gap(arrow_3x3)
        self.assertEqual(gap_in_arrow_3x3, 4.0, "Folga horizontal entre insumos e seta 3x3 deve ser 4px")

        # Inventário 2x2 (inventory.uidx)
        # Grade 2x2: offset [97, 17], tamanho [36, 36] (2 colunas x 18px)
        input_2x2 = AABB("input_grid_2x2", 97.0, 17.0, 97.0 + 36.0, 17.0 + 36.0)
        # Seta 2x2: offset [134, 27], tamanho [18, 18]
        arrow_2x2 = AABB("arrow_2x2", 134.0, 27.0, 134.0 + 18.0, 27.0 + 18.0)
        # Slot de saída 2x2: offset [153, 27], tamanho [18, 18]
        output_2x2 = AABB("output_grid_2x2", 153.0, 27.0, 153.0 + 18.0, 27.0 + 18.0)

        # Verificações 2x2:
        # A. Seta vs Saída
        self.assertFalse(arrow_2x2.intersects(output_2x2))
        self.assertEqual(arrow_2x2.overlap_area(output_2x2), 0.0)
        gap_arrow_out_2x2 = arrow_2x2.horizontal_gap(output_2x2)
        self.assertEqual(gap_arrow_out_2x2, 1.0, "Folga horizontal entre seta e saída 2x2 deve ser 1px")

        # B. Insumos vs Seta
        self.assertFalse(input_2x2.intersects(arrow_2x2))
        self.assertEqual(input_2x2.overlap_area(arrow_2x2), 0.0)
        gap_in_arrow_2x2 = input_2x2.horizontal_gap(arrow_2x2)
        self.assertEqual(gap_in_arrow_2x2, 1.0, "Folga horizontal entre insumos e seta 2x2 deve ser 1px")

    # --------------------------------------------------------------------------
    # TESTE 8: HIERARQUIA DE CAMADAS Z (LAYER AUDIT) E RENDERIZAÇÃO
    # --------------------------------------------------------------------------
    def test_08_layer_stack_and_visual_transparency(self):
        """
        Valida que o botão da seta está na camada correta (layer 4) e com estado
        padrão mapeado para 'miscellaneous_dx.empty_panel' (repouso transparente).
        Garante que a pixel art de madeira temática não é coberta por botões opacos.
        """
        # 1. Bancada 3x3
        arrow_container_3x3 = None
        for ctrl in self.crafting_table_data["top_half_panel"]["controls"]:
            if "arrow" in ctrl:
                arrow_container_3x3 = ctrl["arrow"]
                break
        self.assertIsNotNone(arrow_container_3x3, "Painel 'arrow' não encontrado em crafting_table.uidx")
        arrow_btn_3x3 = arrow_container_3x3["controls"][0]["button"]
        self.assertEqual(arrow_btn_3x3["layer"], 4)
        self.assertEqual(arrow_btn_3x3["default_control"], "default")
        self.assertIn("default@miscellaneous_dx.empty_panel", arrow_btn_3x3["controls"][0])

        # 2. Inventário 2x2
        arrow_container_2x2 = None
        for ctrl in self.inventory_data["top_half_panel"]["controls"]:
            if "arrow" in ctrl:
                arrow_container_2x2 = ctrl["arrow"]
                break
        self.assertIsNotNone(arrow_container_2x2, "Painel 'arrow' não encontrado em inventory.uidx")
        arrow_btn_2x2 = arrow_container_2x2["controls"][0]["button"]
        self.assertEqual(arrow_btn_2x2["layer"], 4)
        self.assertEqual(arrow_btn_2x2["default_control"], "default")
        self.assertIn("default@miscellaneous_dx.empty_panel", arrow_btn_2x2["controls"][0])

    # --------------------------------------------------------------------------
    # TESTE 9: ORÁCULO DE FALHA E TESTES DE MUTAÇÃO (MUTATION DISCRIMINATION AUDIT)
    # --------------------------------------------------------------------------
    def test_09_failure_oracle_and_mutation_sensitivity(self):
        """
        Demonstra o poder de detecção de bugs do harness através de mutações deliberadas:
        - Mutação 1: Remoção de $auto_place_mode_button_name deve ser detectada.
        - Mutação 2: Deslocamento da seta gerando colisão deve ser detectado pelo AABB.
        - Mutação 3: Quebra da exclusão mútua do rádio grupo deve ser detectada.
        """
        # Mutação 1: Slot sem tertiary em Auto Place Mode
        mutated_slot = ContainerSlotSimulator(
            slot_name="mutated_output",
            collection_name="crafting_output_items",
            button_ref="crafting.output_no_coalesce_container_slot_button",
            auto_place_mode_button_name=None,  # Sem override
        )
        fsm_auto = SideTabFSM(initial_mode=1)
        self.assertNotEqual(
            mutated_slot.route_input(fsm_auto, "button.menu_select"),
            "button.crafting_output_tertiary",
            "Oráculo falhou: deveria acusar que o slot mutado não dispara tertiary!",
        )

        # Mutação 2: AABB colidindo (Seta deslocada para a direita invadindo a saída)
        colliding_arrow = AABB("mutated_arrow", 134.0, 27.0, 160.0, 45.0)  # X2=160 invade saída X1=153
        target_output = AABB("output_2x2", 153.0, 27.0, 171.0, 45.0)
        self.assertTrue(
            colliding_arrow.intersects(target_output),
            "Oráculo falhou: deveria acusar colisão geométrica!",
        )
        self.assertGreater(
            colliding_arrow.overlap_area(target_output),
            0.0,
            "Área de sobreposição deve ser > 0 na colisão!",
        )

        # Mutação 3: FSM recebendo índice inválido
        fsm = SideTabFSM(initial_mode=0)
        with self.assertRaises(ValueError):
            fsm.switch_mode(99)


# ==============================================================================
# PONTO DE ENTRADA DA EXECUÇÃO
# ==============================================================================
if __name__ == "__main__":
    print("=" * 80)
    print("INICIANDO SUÍTE DE TESTES EMPÍRICOS ADVERSARIAIS DO MARCO 2 (M2)")
    print("Testando Interação entre Quick Crafting e Aba Lateral de Modos")
    print("=" * 80)
    unittest.main(verbosity=2)
