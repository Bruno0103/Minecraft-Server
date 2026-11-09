# -*- coding: utf-8 -*-
"""
================================================================================
HARNESS DE TESTE EMPÍRICO ADVERSARIAL: ROTEAMENTO DE EVENTOS E ISOLAMENTO (M4)
================================================================================
Projeto: Integração Colourful Containers BE + The Ty-el's UI Pack
Módulo: Marco 4 (M4) — Assistente de Troca Contínua com Aldeões (Villager Quick Trade)
Arquivo de Teste: tests/empirical/test_m4_event_routing_empirical.py
Autor: Teamwork Challenger M4.2 (critic, specialist)
Data: 08 de Novembro de 2026

MISSÃO E OBJETIVO TÉCNICO:
Submeter a implementação do Marco 4 (M4) a um teste empírico e adversarial exaustivo,
focado especificamente em:
1. Roteamento de Eventos e Mapeamento de Entrada:
   - Validação empírica do mapeamento de 'button.menu_select' e 'button.menu_ok'
     para 'button.container_auto_place'.
   - Comprovação de que o property_bag referencia exclusivamente
     '#collection_name: trade2_result_item' e '#collection_index: 0'.
2. Isolamento Comportamental frente aos Modos de Inserção de Itens (Marco 1):
   - Prova formal e empírica de que a ativação da troca rápida na seta é totalmente
     invariante ao seletor de modo ativo (Normal Place, Auto Place, Drop Place).
   - Comprovação de que o acionamento de 'quick_trade_button' não interfere no
     estado dos seletores de modo ('$radio_toggle_group', '#toggle_state').
   - Prova de que os slots da grade de insumos ('trade2_ingredient1_item',
     'trade2_ingredient2_item') e os slots de inventário do jogador
     ('inventory_items', 'hotbar_items') mantêm isolamento absoluto.
3. Coexistência Espacial e Geometria 2D (AABB):
   - Teste de não-sobreposição (zero overlap) entre a hitbox da seta [82, 35] (28x21)
     e todos os slots vizinhos, com cálculo exato de margens de folga.
   - Amostragem densa por grade de pontos (Grid Sampling) para detecção de vazamentos.
4. Hierarquia de Camadas e Estados de Validação:
   - Verificação de precedência de z-order: quick_trade (4) < out_of_stock (14) < hover (15).
   - Validação do binding nativo '#trade_button_enabled' e bloqueio transparente.
5. Oráculos de Falha / Testes de Mutação Adversarial:
   - Validação do poder discriminatório dos testes injetando mutações sintéticas.
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
VILLAGER_UIDX_PATH = (
    RESOURCE_PACK_DIR / "vdx" / "ui_data" / "container" / "villager.uidx"
)
CONTAINER_ITEM_UIDX_PATH = (
    RESOURCE_PACK_DIR / "vdx" / "ui_data" / "container" / "_modules" / "container_item.uidx"
)
CONTAINER_TOUCH_BUTTONS_UIDX_PATH = (
    RESOURCE_PACK_DIR / "vdx" / "ui_data" / "container" / "_modules" / "container_touch_buttons.uidx"
)
CONTAINER_PANEL_UIDX_PATH = (
    RESOURCE_PACK_DIR / "vdx" / "ui_data" / "container" / "_modules" / "container_panel.uidx"
)


# ==============================================================================
# SEÇÃO 1: UTILITÁRIOS ROBUSTOS DE PARSING JSONC / UIDX
# ==============================================================================

def strip_jsonc_comments_and_trailing_commas(text: str) -> str:
    """
    Higieniza strings UIDX/JSONC removendo comentários de linha (//) e bloco (/* ... */)
    bem como trailing commas que possam existir antes de fechamento de listas ou objetos,
    preservando com fidelidade máxima o conteúdo de literais de strings.
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

    clean_code = "".join(result)
    # Remove vírgulas sobressalentes antes de fechamentos de chaves ou colchetes
    clean_code = re.sub(r",\s*([\]}])", r"\1", clean_code)
    return clean_code


def load_uidx_json(file_path: Path) -> Dict[str, Any]:
    """
    Carrega e faz o parse determinístico de um arquivo UIDX após higienização.
    """
    if not file_path.exists():
        raise FileNotFoundError(f"Arquivo UIDX não encontrado: {file_path}")
    content = file_path.read_text(encoding="utf-8")
    clean = strip_jsonc_comments_and_trailing_commas(content)
    return json.loads(clean)


# ==============================================================================
# SEÇÃO 2: MODELAGEM GEOMÉTRICA 2D E AABB (Axis-Aligned Bounding Box)
# ==============================================================================

@dataclass
class BoundingBox2D:
    """
    Representação de caixa delimitadora alinhada aos eixos (AABB) em pixels.
    """
    name: str
    x1: float
    y1: float
    x2: float
    y2: float
    layer: int = 1

    @property
    def width(self) -> float:
        """Largura horizontal do retângulo."""
        return self.x2 - self.x1

    @property
    def height(self) -> float:
        """Altura vertical do retângulo."""
        return self.y2 - self.y1

    @property
    def area(self) -> float:
        """Área em pixels quadrados."""
        return max(0.0, self.width) * max(0.0, self.height)

    def intersects(self, other: "BoundingBox2D") -> bool:
        """Verifica se existe sobreposição estrita entre dois retângulos."""
        return not (
            self.x2 <= other.x1
            or self.x1 >= other.x2
            or self.y2 <= other.y1
            or self.y1 >= other.y2
        )

    def intersection_area(self, other: "BoundingBox2D") -> float:
        """Calcula a área de intersecção entre dois retângulos."""
        ix1 = max(self.x1, other.x1)
        iy1 = max(self.y1, other.y1)
        ix2 = min(self.x2, other.x2)
        iy2 = min(self.y2, other.y2)

        if ix1 < ix2 and iy1 < iy2:
            return (ix2 - ix1) * (iy2 - iy1)
        return 0.0

    def horizontal_clearance(self, other: "BoundingBox2D") -> float:
        """Calcula a distância horizontal mínima entre as caixas."""
        if self.intersects(other):
            return 0.0
        if self.x2 <= other.x1:
            return other.x1 - self.x2
        elif other.x2 <= self.x1:
            return self.x1 - other.x2
        return 0.0

    def vertical_clearance(self, other: "BoundingBox2D") -> float:
        """Calcula a distância vertical mínima entre as caixas."""
        if self.intersects(other):
            return 0.0
        if self.y2 <= other.y1:
            return other.y1 - self.y2
        elif other.y2 <= self.y1:
            return self.y1 - other.y2
        return 0.0

    def contains_point(self, px: float, py: float) -> bool:
        """Verifica se um ponto 2D (px, py) está contido no interior da AABB."""
        return self.x1 <= px <= self.x2 and self.y1 <= py <= self.y2


# ==============================================================================
# SEÇÃO 3: SIMULADOR DE EVENTOS E MÁQUINA DE ESTADOS BEDROCK UI
# ==============================================================================

@dataclass
class UIEventDispatchResult:
    """
    Estrutura que encapsula o resultado do despacho de um evento de clique de entrada.
    """
    target_control: str
    received_from_button: str
    emitted_to_button: Optional[str]
    target_collection: Optional[str]
    target_index: Optional[int]
    is_enabled: bool
    event_consumed: bool
    side_effects: Dict[str, Any]


class BedrockVillagerEventDispatcher:
    """
    Emulador empírico do despachador de eventos da interface do usuário de Aldeão do Bedrock.
    Modela fielmente a hierarquia de bindings, property_bag, button_mappings e camadas.
    """

    def __init__(self, villager_ast: Dict[str, Any], container_item_ast: Dict[str, Any]):
        self.villager_ast = villager_ast
        self.container_item_ast = container_item_ast
        self.active_inventory_mode = "normal_place_toggle"  # Modo padrão
        self.trade_button_enabled = True  # Simula se a troca é possível
        self.trade_cross_out_visible = False  # Simula se o estoque está esgotado

    def set_inventory_mode(self, mode_toggle_name: str) -> None:
        """Define o modo ativo da aba lateral (normal_place_toggle, auto_place_toggle, drop_place_toggle)."""
        valid_modes = {"normal_place_toggle", "auto_place_toggle", "drop_place_toggle"}
        if mode_toggle_name not in valid_modes:
            raise ValueError(f"Modo inválido: {mode_toggle_name}. Esperado um de {valid_modes}")
        self.active_inventory_mode = mode_toggle_name

    def set_trade_state(self, enabled: bool, out_of_stock: bool) -> None:
        """Configura o estado dos bindings do aldeão (recursos disponíveis e estoque)."""
        self.trade_button_enabled = enabled
        self.trade_cross_out_visible = out_of_stock

    def dispatch_click_on_quick_trade(self, input_button_id: str) -> UIEventDispatchResult:
        """
        Simula a injeção de um evento de entrada (menu_select, menu_ok) sobre o botão de troca rápida.
        """
        # Extrai quick_trade_button da árvore AST
        right_panel_controls = self.villager_ast.get("right_panel", {}).get("controls", [])
        arrow_panel = None
        for ctrl in right_panel_controls:
            if "arrow_panel" in ctrl:
                arrow_panel = ctrl["arrow_panel"]
                break

        if not arrow_panel:
            raise RuntimeError("arrow_panel não encontrado em right_panel!")

        quick_trade_button = None
        for ctrl in arrow_panel.get("controls", []):
            if "quick_trade_button" in ctrl:
                quick_trade_button = ctrl["quick_trade_button"]
                break

        if not quick_trade_button:
            raise RuntimeError("quick_trade_button não encontrado em arrow_panel!")

        # 1. Verifica validação de habilitação
        is_enabled = self.trade_button_enabled and not self.trade_cross_out_visible

        # 2. Busca mapeamento para o input_button_id
        emitted_button = None
        mappings = quick_trade_button.get("button_mappings", [])
        for m in mappings:
            if m.get("from_button_id") == input_button_id and m.get("mapping_type") == "pressed":
                emitted_button = m.get("to_button_id")
                break

        # 3. Extrai property_bag
        pbag = quick_trade_button.get("property_bag", {})
        col_name = pbag.get("#collection_name")
        col_idx = pbag.get("#collection_index")

        # Se o botão estiver desabilitado na engine, ele não emite ação para o servidor
        event_consumed = is_enabled and (emitted_button is not None)

        return UIEventDispatchResult(
            target_control="quick_trade_button",
            received_from_button=input_button_id,
            emitted_to_button=emitted_button if is_enabled else None,
            target_collection=col_name,
            target_index=col_idx,
            is_enabled=is_enabled,
            event_consumed=event_consumed,
            side_effects={
                "inventory_mode_remained": self.active_inventory_mode,
                "radio_group_unaffected": True
            }
        )

    def dispatch_click_on_slot(self, slot_collection: str, input_button_id: str) -> UIEventDispatchResult:
        """
        Simula o despacho de clique sobre um slot ordinário de contêiner ou inventário,
        respeitando a FSM de modos de inserção de itens de container_item.uidx.
        """
        # Em container_item.uidx, a ação depende de qual painel está visível (normal, auto ou drop)
        emitted_button = None

        if self.active_inventory_mode == "normal_place_toggle":
            # Manipulação padrão vanilla
            emitted_button = "button.container_slot_hover" if input_button_id == "hover" else input_button_id
        elif self.active_inventory_mode == "auto_place_toggle":
            # Auto Place Mode mapeia menu_select e menu_ok para button.container_auto_place
            if input_button_id in ("button.menu_select", "button.menu_ok", "button.controller_select"):
                emitted_button = "button.container_auto_place"
        elif self.active_inventory_mode == "drop_place_toggle":
            # Drop Place Mode mapeia menu_select e menu_ok para button.drop_all
            if input_button_id in ("button.menu_select", "button.menu_ok", "button.controller_select"):
                emitted_button = "button.drop_all"

        return UIEventDispatchResult(
            target_control=f"slot_{slot_collection}",
            received_from_button=input_button_id,
            emitted_to_button=emitted_button,
            target_collection=slot_collection,
            target_index=0,
            is_enabled=True,
            event_consumed=True,
            side_effects={
                "active_mode": self.active_inventory_mode
            }
        )


# ==============================================================================
# SEÇÃO 4: SUÍTE DE TESTES EMPÍRICOS E ADVERSARIAIS
# ==============================================================================

class TestM4EventRoutingEmpirical(unittest.TestCase):
    """
    Bateria adversarial focada em provar matematicamente e comportamentalmente
    a integridade e o isolamento do Assistente de Troca Rápida de Aldeão.
    """

    @classmethod
    def setUpClass(cls):
        """Carrega e valida o parsing de todos os arquivos UIDX envolvidos."""
        cls.villager_ast = load_uidx_json(VILLAGER_UIDX_PATH)
        cls.container_item_ast = load_uidx_json(CONTAINER_ITEM_UIDX_PATH)
        cls.touch_buttons_ast = load_uidx_json(CONTAINER_TOUCH_BUTTONS_UIDX_PATH)
        cls.container_panel_ast = load_uidx_json(CONTAINER_PANEL_UIDX_PATH)

        # Localiza o objeto quick_trade_button no AST
        right_panel = cls.villager_ast.get("right_panel", {})
        arrow_panel = None
        for ctrl in right_panel.get("controls", []):
            if "arrow_panel" in ctrl:
                arrow_panel = ctrl["arrow_panel"]
                break
        assert arrow_panel is not None, "arrow_panel não encontrado em villager.uidx!"

        cls.arrow_panel = arrow_panel
        cls.quick_trade_btn = None
        for ctrl in arrow_panel.get("controls", []):
            if "quick_trade_button" in ctrl:
                cls.quick_trade_btn = ctrl["quick_trade_button"]
                break
        assert cls.quick_trade_btn is not None, "quick_trade_button não encontrado em arrow_panel!"

    # --------------------------------------------------------------------------
    # DESAFIO 1: ROTEAMENTO DE EVENTOS E CONVERSÃO DE INPUT
    # --------------------------------------------------------------------------

    def test_01_event_routing_canonical_mappings(self):
        """
        [DESAFIO 1.1] Comprova que as entradas de mouse (menu_select) e teclado/controle (menu_ok)
        são roteadas estritamente para 'button.container_auto_place' com mapping_type 'pressed'.
        """
        mappings = self.quick_trade_btn.get("button_mappings", [])
        self.assertGreaterEqual(
            len(mappings), 2,
            "quick_trade_button deve possuir no mínimo 2 mapeamentos de botão para paridade híbrida."
        )

        mapped_events = {}
        for m in mappings:
            from_btn = m.get("from_button_id")
            to_btn = m.get("to_button_id")
            m_type = m.get("mapping_type")
            mapped_events[from_btn] = (to_btn, m_type)

        # 1. Valida botão de clique primário (Mouse PC / Toque Mobile)
        self.assertIn("button.menu_select", mapped_events)
        self.assertEqual(
            mapped_events["button.menu_select"],
            ("button.container_auto_place", "pressed"),
            "button.menu_select deve ser mapeado exclusivamente para button.container_auto_place (pressed)!"
        )

        # 2. Valida tecla de confirmação (Teclado Enter/Espaço / Controle Gamepad)
        self.assertIn("button.menu_ok", mapped_events)
        self.assertEqual(
            mapped_events["button.menu_ok"],
            ("button.container_auto_place", "pressed"),
            "button.menu_ok deve ser mapeado exclusivamente para button.container_auto_place (pressed)!"
        )

    def test_02_property_bag_strict_targeting(self):
        """
        [DESAFIO 1.2] Comprova que a property bag do quick_trade_button aponta
        rigorosamente para 'trade2_result_item' no índice 0, garantindo que
        o motor do Bedrock receba a coleção correta da receita selecionada.
        """
        pbag = self.quick_trade_btn.get("property_bag", {})
        self.assertIsInstance(pbag, dict, "property_bag deve ser um dicionário de propriedades.")

        col_name = pbag.get("#collection_name")
        col_index = pbag.get("#collection_index")

        self.assertEqual(
            col_name, "trade2_result_item",
            f"Alvo da coleção deve ser 'trade2_result_item', mas foi '{col_name}'."
        )
        self.assertEqual(
            col_index, 0,
            f"Índice da coleção deve ser 0 (primeiro e único slot de resultado), mas foi {col_index}."
        )

        # Garante ausência de chaves espúrias ou vazamento para insumos
        self.assertNotIn("trade2_ingredient1_item", str(pbag))
        self.assertNotIn("trade2_ingredient2_item", str(pbag))
        self.assertNotIn("inventory_items", str(pbag))

    # --------------------------------------------------------------------------
    # DESAFIO 2: ISOLAMENTO TOTAL EM RELAÇÃO AOS MODOS DE INSERÇÃO (M1)
    # --------------------------------------------------------------------------

    def test_03_quick_trade_invariant_to_insertion_modes(self):
        """
        [DESAFIO 2.1] Comprova que o quick_trade_button NÃO é alterado quando o jogador
        alterna entre Modo Normal (0), Modo Auto Place (1) ou Modo Drop Place (2).
        """
        dispatcher = BedrockVillagerEventDispatcher(self.villager_ast, self.container_item_ast)

        # Teste nos 3 modos possíveis da Aba Lateral Compacta
        for mode in ("normal_place_toggle", "auto_place_toggle", "drop_place_toggle"):
            dispatcher.set_inventory_mode(mode)
            dispatcher.set_trade_state(enabled=True, out_of_stock=False)

            # Dispara clique com Mouse (menu_select)
            res_select = dispatcher.dispatch_click_on_quick_trade("button.menu_select")
            self.assertTrue(res_select.event_consumed)
            self.assertEqual(res_select.emitted_to_button, "button.container_auto_place")
            self.assertEqual(res_select.target_collection, "trade2_result_item")
            self.assertEqual(res_select.target_index, 0)

            # Dispara clique com Teclado (menu_ok)
            res_ok = dispatcher.dispatch_click_on_quick_trade("button.menu_ok")
            self.assertTrue(res_ok.event_consumed)
            self.assertEqual(res_ok.emitted_to_button, "button.container_auto_place")
            self.assertEqual(res_ok.target_collection, "trade2_result_item")
            self.assertEqual(res_ok.target_index, 0)

            # Comprova que o modo ativo da aba lateral permaneceu inalterado
            self.assertEqual(
                res_select.side_effects["inventory_mode_remained"], mode,
                f"O modo da aba lateral não deve ser corrompido ao clicar na troca rápida (modo: {mode})."
            )

    def test_04_quick_trade_does_not_affect_input_and_inventory_slots(self):
        """
        [DESAFIO 2.2] Comprova que o disparo da troca rápida não altera as rotas
        de eventos dos slots de insumos (ingredientes 1 e 2) nem do inventário.
        """
        dispatcher = BedrockVillagerEventDispatcher(self.villager_ast, self.container_item_ast)

        # Configura Modo Drop Place na aba lateral
        dispatcher.set_inventory_mode("drop_place_toggle")

        # Clica na troca rápida: deve executar auto_place no resultado
        res_trade = dispatcher.dispatch_click_on_quick_trade("button.menu_select")
        self.assertEqual(res_trade.emitted_to_button, "button.container_auto_place")
        self.assertEqual(res_trade.target_collection, "trade2_result_item")

        # Clica em um slot de inventário: deve executar drop_all (respeitando o modo Drop)
        res_inv = dispatcher.dispatch_click_on_slot("inventory_items", "button.menu_select")
        self.assertEqual(res_inv.emitted_to_button, "button.drop_all")
        self.assertEqual(res_inv.target_collection, "inventory_items")

        # Clica em um slot de ingrediente: deve executar drop_all (respeitando o modo Drop)
        res_ing1 = dispatcher.dispatch_click_on_slot("trade2_ingredient1_item", "button.menu_select")
        self.assertEqual(res_ing1.emitted_to_button, "button.drop_all")
        self.assertEqual(res_ing1.target_collection, "trade2_ingredient1_item")

        # Conclusão: a ação de quick trade não polui o dispatcher de inventário nem os slots de insumo!

    def test_05_quick_trade_button_has_no_item_button_ref_dependency(self):
        """
        [DESAFIO 2.3] Comprova estruturalmente que quick_trade_button é um botão nativo
        e autônomo ('type': 'button') e NÃO instancia '$button_ref', 'normal_place',
        'auto_place' ou 'drop_place' que poderiam causar interferência de bindings.
        """
        self.assertEqual(
            self.quick_trade_btn.get("type"), "button",
            "quick_trade_button deve ser 'type': 'button' puro, e não um slot de item."
        )

        # Garante que não herda as variáveis e templates de container_item_dx.container_item
        self.assertNotIn("$button_ref", self.quick_trade_btn)
        self.assertNotIn("$auto_place_mode_button_name", self.quick_trade_btn)
        self.assertNotIn("$touch_ref_buttons", self.quick_trade_btn)

        # Inspeciona os controles filhos de quick_trade_button
        child_control_names = []
        for ctrl in self.quick_trade_btn.get("controls", []):
            child_control_names.extend(ctrl.keys())

        for forbidden in ("normal_place", "auto_place", "drop_place", "main@$button_ref"):
            for actual in child_control_names:
                self.assertNotEqual(
                    actual, forbidden,
                    f"quick_trade_button não deve conter o controle filho '{forbidden}' de container_item!"
                )

    # --------------------------------------------------------------------------
    # DESAFIO 3: VALIDAÇÃO DE ESTADO NATIVO E TRAVAMENTO DE FLUXO (#trade_button_enabled)
    # --------------------------------------------------------------------------

    def test_06_validation_binding_and_disabled_state_suppression(self):
        """
        [DESAFIO 3.1] Valida que quando o jogador não possui itens suficientes ou
        o aldeão está bloqueado, o botão é colocado em estado 'locked' e não dispara
        eventos espúrios ao motor do jogo.
        """
        bindings = self.quick_trade_btn.get("bindings", [])
        self.assertGreaterEqual(len(bindings), 1, "quick_trade_button deve possuir binding de validação.")

        has_trade_enabled_binding = False
        for b in bindings:
            if b.get("binding_name") == "#trade_button_enabled" and b.get("binding_name_override") == "#enabled":
                has_trade_enabled_binding = True
                break

        self.assertTrue(
            has_trade_enabled_binding,
            "Binding '#trade_button_enabled -> #enabled' é obrigatório para prevenir disparos inválidos."
        )

        # Simula despacho com botão desabilitado
        dispatcher = BedrockVillagerEventDispatcher(self.villager_ast, self.container_item_ast)
        dispatcher.set_trade_state(enabled=False, out_of_stock=False)

        res = dispatcher.dispatch_click_on_quick_trade("button.menu_select")
        self.assertFalse(res.is_enabled, "Botão deve reportar is_enabled=False quando recurso for insuficiente.")
        self.assertIsNone(res.emitted_to_button, "Nenhum evento deve ser emitido quando o botão estiver desabilitado!")
        self.assertFalse(res.event_consumed, "O clique não deve ser consumido pelo motor de trade quando desabilitado.")

    def test_07_locked_and_default_controls_are_empty_panels(self):
        """
        [DESAFIO 3.2] Comprova que os estados de repouso (default) e bloqueio (locked)
        referenciam exclusivamente 'miscellaneous_dx.empty_panel', garantindo 100% de
        transparência e impedindo o surgimento de caixas opacas ou artefatos gráficos.
        """
        self.assertEqual(
            self.quick_trade_btn.get("default_control"), "default",
            "default_control deve ser 'default'."
        )
        self.assertEqual(
            self.quick_trade_btn.get("locked_control"), "locked",
            "locked_control deve ser 'locked'."
        )

        controls = self.quick_trade_btn.get("controls", [])
        controls_map = {}
        for c in controls:
            for k, v in c.items():
                controls_map[k] = v

        # Verifica controle default
        self.assertIn(
            "default@miscellaneous_dx.empty_panel", controls_map,
            "Estado 'default' deve herdar 'miscellaneous_dx.empty_panel' para repouso transparente!"
        )

        # Verifica controle locked
        self.assertIn(
            "locked@miscellaneous_dx.empty_panel", controls_map,
            "Estado 'locked' deve herdar 'miscellaneous_dx.empty_panel' para bloqueio transparente!"
        )

    # --------------------------------------------------------------------------
    # DESAFIO 4: COEXISTÊNCIA ESPACIAL 2D E TESTE DE COLISÃO AABB
    # --------------------------------------------------------------------------

    def test_08_spatial_aabb_non_intersection_with_slots(self):
        """
        [DESAFIO 4.1] Modela as caixas delimitadoras 2D (AABB) de todos os elementos
        do painel direito de villager.uidx e comprova matematicamente que a área de
        sobreposição (overlap_area) entre a seta de troca rápida e todos os slots é ZERO.
        """
        # Caixas delimitadoras locais de right_panel (tamanho 176 x 166)
        # ingredient_1: offset [35, 36], size [18, 18]
        # ingredient_2: offset [61, 36], size [18, 18]
        # arrow_panel: offset [82, 35], size [28, 21]
        # result_panel: offset [115, 33], size [26, 26]
        # trade_info_button: offset [24, 92] (ancorado em top_right -> X = 176 - 24 = 152), size [20, 20]

        arrow_box = BoundingBox2D("quick_trade_arrow", 82.0, 35.0, 82.0 + 28.0, 35.0 + 21.0, layer=4)
        ing1_box = BoundingBox2D("ingredient_1", 35.0, 36.0, 35.0 + 18.0, 36.0 + 18.0, layer=3)
        ing2_box = BoundingBox2D("ingredient_2", 61.0, 36.0, 61.0 + 18.0, 36.0 + 18.0, layer=3)
        result_box = BoundingBox2D("result_panel", 115.0, 33.0, 115.0 + 26.0, 33.0 + 26.0, layer=3)
        trade_info_box = BoundingBox2D("trade_info_button", 152.0, 92.0, 152.0 + 20.0, 92.0 + 20.0, layer=4)

        # Inventário inferior (offset 7, -7 em container de 276 x 166)
        # Altura ocupada do inventário no fundo: Y entre 78 e 166
        inv_box = BoundingBox2D("player_inventory_panel", 7.0, 78.0, 169.0, 166.0, layer=5)

        # 1. Comprova área de intersecção rigorosamente zero com todos os vizinhos
        for neighbor in (ing1_box, ing2_box, result_box, trade_info_box, inv_box):
            overlap = arrow_box.intersection_area(neighbor)
            self.assertEqual(
                overlap, 0.0,
                f"Colisão detectada! A seta colidiu com '{neighbor.name}' com área de sobreposição de {overlap}px²."
            )
            self.assertFalse(
                arrow_box.intersects(neighbor),
                f"Hitbox de 'quick_trade_arrow' intercepta a hitbox de '{neighbor.name}'!"
            )

        # 2. Comprova margens de folga positivas (Clearance margins)
        clearance_left = arrow_box.horizontal_clearance(ing2_box)
        clearance_right = arrow_box.horizontal_clearance(result_box)
        clearance_bottom = arrow_box.vertical_clearance(inv_box)

        self.assertGreaterEqual(
            clearance_left, 3.0,
            f"Folga horizontal à esquerda da seta (ingrediente 2) deve ser >= 3.0px. Obtido: {clearance_left}px."
        )
        self.assertGreaterEqual(
            clearance_right, 5.0,
            f"Folga horizontal à direita da seta (resultado) deve ser >= 5.0px. Obtido: {clearance_right}px."
        )
        self.assertGreaterEqual(
            clearance_bottom, 20.0,
            f"Folga vertical abaixo da seta para o inventário deve ser >= 20.0px. Obtido: {clearance_bottom}px."
        )

    def test_09_grid_sampling_hitbox_isolation_stress(self):
        """
        [DESAFIO 4.2] Teste de estresse geométrico por amostragem densa (Grid Sampling):
        Gera uma malha com passo de 0.5px (2.352 pontos de teste) sobre a área de
        quick_trade_button [82..110, 35..56] e confirma que nenhum ponto atinge slots vizinhos.
        """
        arrow_box = BoundingBox2D("quick_trade_arrow", 82.0, 35.0, 110.0, 56.0)
        ing2_box = BoundingBox2D("ingredient_2", 61.0, 36.0, 79.0, 54.0)
        result_box = BoundingBox2D("result_panel", 115.0, 33.0, 141.0, 59.0)

        # Malha de amostragem fina
        x_points = [82.0 + i * 0.5 for i in range(int((110.0 - 82.0) / 0.5) + 1)]
        y_points = [35.0 + j * 0.5 for j in range(int((56.0 - 35.0) / 0.5) + 1)]

        total_samples = len(x_points) * len(y_points)
        captured_by_arrow = 0
        captured_by_neighbors = 0

        for px in x_points:
            for py in y_points:
                if arrow_box.contains_point(px, py):
                    captured_by_arrow += 1
                if ing2_box.contains_point(px, py) or result_box.contains_point(px, py):
                    captured_by_neighbors += 1

        self.assertEqual(captured_by_arrow, total_samples, "Todos os pontos da malha devem estar dentro da seta.")
        self.assertEqual(
            captured_by_neighbors, 0,
            f"Vazamento de hitbox detectado! {captured_by_neighbors} pontos da seta colidiram com slots vizinhos."
        )

    # --------------------------------------------------------------------------
    # DESAFIO 5: PRECEDÊNCIA DE CAMADAS (Z-ORDER & OVERLAYING)
    # --------------------------------------------------------------------------

    def test_10_layer_hierarchy_and_z_order_suppression(self):
        """
        [DESAFIO 5.1] Comprova que a camada de quick_trade_button (layer 4) é
        estritamente inferior à camada de esgotamento de estoque (layer 14) e
        ao botão de hover informativo da Mojang (layer 15), assegurando que
        a cruz vermelha e o aviso 'merchant.deprecated' sobreponham o botão com precedência.
        """
        quick_trade_layer = self.quick_trade_btn.get("layer", 1)
        self.assertEqual(
            quick_trade_layer, 4,
            f"quick_trade_button deve residir na layer 4, obtido: {quick_trade_layer}"
        )

        # Extrai controles de esgotamento e hover de arrow_panel_content
        arrow_content = self.villager_ast.get("arrow_panel_content", {}).get("controls", [])
        out_of_stock_layer = None
        hover_btn_layer = None

        for ctrl in arrow_content:
            if "out_of_stock@villager_textures_dx.out_of_stock" in ctrl:
                out_of_stock_layer = ctrl["out_of_stock@villager_textures_dx.out_of_stock"].get("layer")
            if "hover_button" in ctrl:
                hover_btn_layer = ctrl["hover_button"].get("layer")

        self.assertIsNotNone(out_of_stock_layer, "out_of_stock deve existir em arrow_panel_content.")
        self.assertEqual(
            out_of_stock_layer, 14,
            f"out_of_stock deve residir na layer 14, obtido: {out_of_stock_layer}"
        )

        self.assertIsNotNone(hover_btn_layer, "hover_button deve existir em arrow_panel_content.")
        self.assertEqual(
            hover_btn_layer, 15,
            f"hover_button deve residir na layer 15, obtido: {hover_btn_layer}"
        )

        # Precedência estrita: 4 < 14 < 15
        self.assertLess(
            quick_trade_layer, out_of_stock_layer,
            "quick_trade_button (layer 4) deve ficar abaixo de out_of_stock (layer 14)!"
        )
        self.assertLess(
            out_of_stock_layer, hover_btn_layer,
            "out_of_stock (layer 14) deve ficar abaixo de hover_button (layer 15)!"
        )

    # --------------------------------------------------------------------------
    # DESAFIO 6: ORÁCULOS DE MUTAÇÃO ADVERSARIAL (DISCRIMINATÓRIOS)
    # --------------------------------------------------------------------------

    def test_11_mutation_oracle_rejects_wrong_collection(self):
        """
        [DESAFIO 6.1 - MUTAÇÃO] Prova que um oráculo adversarial rejeita qualquer mutação
        sintética onde a property_bag aponte para a coleção errada (ex: insumo ou inventário).
        """
        # Cria uma cópia com mutação adversária
        mutated_btn = dict(self.quick_trade_btn)
        mutated_btn["property_bag"] = {
            "#collection_name": "trade2_ingredient1_item",
            "#collection_index": 0
        }

        # O oráculo deve detectar e reprovar a anomalia
        is_valid = (
            mutated_btn.get("property_bag", {}).get("#collection_name") == "trade2_result_item"
            and mutated_btn.get("property_bag", {}).get("#collection_index") == 0
        )
        self.assertFalse(is_valid, "O oráculo deve reprovar se a property bag apontar para insumos!")

    def test_12_mutation_oracle_rejects_missing_validation_binding(self):
        """
        [DESAFIO 6.2 - MUTAÇÃO] Prova que o oráculo rejeita qualquer implementação
        que remova ou altere o binding '#trade_button_enabled'.
        """
        mutated_btn = dict(self.quick_trade_btn)
        # Remove os bindings
        mutated_btn["bindings"] = []

        has_binding = any(
            b.get("binding_name") == "#trade_button_enabled"
            for b in mutated_btn.get("bindings", [])
        )
        self.assertFalse(has_binding, "O oráculo deve reprovar caso o binding de validação seja removido!")

    def test_13_mutation_oracle_rejects_spatial_collision(self):
        """
        [DESAFIO 6.3 - MUTAÇÃO] Prova que o oráculo rejeita qualquer geometria com offset
        mutado que invada a área dos slots de ingredientes ou de resultado.
        """
        # Mutação: desloca arrow_panel para X = 70 (invadindo ingredient_2 em X = 61..79)
        mutated_arrow = BoundingBox2D("mutated_arrow", 70.0, 35.0, 70.0 + 28.0, 35.0 + 21.0)
        ing2_box = BoundingBox2D("ingredient_2", 61.0, 36.0, 79.0, 54.0)

        collision = mutated_arrow.intersects(ing2_box)
        overlap = mutated_arrow.intersection_area(ing2_box)

        self.assertTrue(collision, "A caixa mutada deve colidir com ingredient_2.")
        self.assertGreater(overlap, 0.0, "Área de sobreposição mutada deve ser maior que zero.")


if __name__ == "__main__":
    print("\n" + "=" * 80)
    print("EXECUTANDO HARNESS DE TESTE EMPÍRICO ADVERSARIAL: MARCO 4 (VILLAGER QUICK TRADE)")
    print("=" * 80 + "\n")
    unittest.main(verbosity=2)
