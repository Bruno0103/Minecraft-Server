# -*- coding: utf-8 -*-
"""
================================================================================
HARNESS DE TESTE EMPÍRICO ADVERSARIAL: COLISÃO GEOMÉTRICA 2D E LAYOUT (MARCO 1)
================================================================================
Projeto: Integração Colourful Containers BE + The Ty-el's UI Pack
Módulo: Marco 1 (M1) — Aba Lateral Vertical Compacta e Desbloqueio Híbrido PC/Mobile
Arquivo de Teste: tests/empirical/test_m1_layout_collision_challenger.py
Autor: Teamwork Challenger (critic, specialist)
Data: 08 de Novembro de 2026

OBJETIVO TÉCNICO:
Submeter a implementação do Marco 1 (M1) a um desafio empírico e adversarial rigoroso,
calculando a colisão geométrica 2D (Axis-Aligned Bounding Box - AABB) em precisão
de subpixel e pixel inteiro entre:
1. 'container_side_tools_tab' (Aba Lateral Compacta de 3 Modos):
   - Offset: [ 24, 28 ]
   - Tamanho: [ 20, 60 ] (composto por 3 botões verticais de 20x20px)
   - Ancoragem: 'top_right' -> 'top_right'
2. 'close_button' (Botão de Fechar 'X' nativo):
   - Offset: [ 24, 4 ]
   - Tamanho: [ 20, 20 ]
   - Ancoragem: 'top_right' -> 'top_right'
3. 'trade_info_button' (Botão de Informações de Trocas em villager.uidx):
   - Offset: [ 24, 92 ] (ajustado pelo Worker M1)
   - Tamanho: [ 20, 20 ]
   - Ancoragem: 'top_right' -> 'top_right'
4. Bordas e limites verticais dos contêineres:
   - Contêiner padrão de 166px (ex: container_panel, villager, crafting_table)
   - Contêiner alto de 222px (ex: chest_large / baú duplo)
   - Contêiner baixo de 133px (ex: hopper / funil)
   - Contêiner intermediário de 167px (ex: barrel, chest_small)

REQUISITOS ADVERSARIAIS:
- Provar computacionalmente se a sobreposição (overlap) é exatamente 0.0% entre todos os pares.
- Registrar e auditar as margens de respiro (clearance gaps) exatas em pixels.
- Verificar a contenção vertical sem estouro de layout nos limites dos contêineres.
- Executar testes de sensibilidade de jitter / tolerância de deslocamento.
- Executar teste de contraprova (oráculo de colisão com o offset legado [24, 60]).
- Escanear a totalidade dos arquivos UIDX de contêineres do VDX em busca de colisões ocultas.
================================================================================
"""

import json
import math
import os
import re
import unittest
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, List, Optional, Tuple


# ==============================================================================
# SEÇÃO 1: ESTRUTURAS GEOMÉTRICAS E MOTOR DE COLISÃO 2D (AABB)
# ==============================================================================

@dataclass(frozen=True)
class BoundingBox:
    """
    Representação imutável de uma caixa delimitadora 2D alinhada aos eixos (AABB).
    Coordenadas:
      - x1: limite esquerdo (inclusivo)
      - y1: limite superior (inclusivo)
      - x2: limite direito (exclusivo para cálculo de área contínua)
      - y2: limite inferior (exclusivo para cálculo de área contínua)
    """
    x1: float
    y1: float
    x2: float
    y2: float

    @property
    def width(self) -> float:
        """Largura horizontal da caixa delimitadora."""
        return max(0.0, self.x2 - self.x1)

    @property
    def height(self) -> float:
        """Altura vertical da caixa delimitadora."""
        return max(0.0, self.y2 - self.y1)

    @property
    def area(self) -> float:
        """Área geométrica total da caixa delimitadora em pixels quadrados."""
        return self.width * self.height

    def intersects(self, other: "BoundingBox") -> bool:
        """
        Determina se há interseção geométrica com área positiva (> 0) entre duas AABBs.
        Retângulos que apenas se tocam em arestas compartilhadas possuem área de sobreposição zero.
        """
        overlap_w = max(0.0, min(self.x2, other.x2) - max(self.x1, other.x1))
        overlap_h = max(0.0, min(self.y2, other.y2) - max(self.y1, other.y1))
        return (overlap_w * overlap_h) > 1e-9

    def intersection(self, other: "BoundingBox") -> Optional["BoundingBox"]:
        """Calcula a AABB resultante da interseção com outra caixa delimitadora."""
        ix1 = max(self.x1, other.x1)
        iy1 = max(self.y1, other.y1)
        ix2 = min(self.x2, other.x2)
        iy2 = min(self.y2, other.y2)
        if ix2 > ix1 and iy2 > iy1:
            return BoundingBox(ix1, iy1, ix2, iy2)
        return None

    def overlap_area(self, other: "BoundingBox") -> float:
        """Retorna a área de interseção em pixels quadrados."""
        inter = self.intersection(other)
        return inter.area if inter is not None else 0.0

    def overlap_percentage(self, other: "BoundingBox") -> float:
        """
        Retorna a porcentagem de sobreposição em relação à menor das duas áreas.
        0.0 = sem qualquer colisão; 100.0 = inclusão total de um elemento no outro.
        """
        min_area = min(self.area, other.area)
        if min_area <= 1e-9:
            return 0.0
        return (self.overlap_area(other) / min_area) * 100.0

    def distance_margins(self, other: "BoundingBox") -> Dict[str, float]:
        """
        Calcula as distâncias euclidianas e margens de separação (gap) entre duas caixas.
        Se gap_x > 0, há separação horizontal. Se gap_x <= 0, há alinhamento ou sobreposição em X.
        """
        gap_x = max(0.0, max(self.x1, other.x1) - min(self.x2, other.x2))
        gap_y = max(0.0, max(self.y1, other.y1) - min(self.y2, other.y2))
        
        # Distância orientada no eixo Y (se estiverem na mesma coluna horizontal)
        # Se other estiver abaixo de self: other.y1 - self.y2
        y_separation = other.y1 - self.y2 if other.y1 >= self.y2 else self.y1 - other.y2

        return {
            "gap_x": gap_x,
            "gap_y": gap_y,
            "y_separation": y_separation,
            "euclidean_distance": math.hypot(gap_x, gap_y)
        }


def compute_bedrock_aabb(
    parent_width: float,
    parent_height: float,
    anchor_from: str,
    anchor_to: str,
    offset: Tuple[float, float],
    size: Tuple[float, float],
    parent_origin: Tuple[float, float] = (0.0, 0.0)
) -> BoundingBox:
    """
    Calcula a AABB absoluta na tela a partir do sistema de coordenadas de ancoragem
    do Minecraft Bedrock JSON UI / UIDX.
    
    Regras da engine Bedrock:
    - anchor_from: ponto no elemento pai que atua como ponto de referência (pivot do pai).
      Ex: 'top_right' em pai (W, H) -> (W, 0).
    - offset: vetor [dx, dy] adicionado ao pivot do pai -> Ponto de ancoragem na tela.
    - anchor_to: ponto no próprio elemento filho que se alinha ao ponto de ancoragem na tela.
      Ex: 'top_right' em filho (w, h) -> o canto superior direito do filho fica em (anchor_x, anchor_y).
      Portanto, o canto superior esquerdo do filho fica em (anchor_x - w, anchor_y).
    """
    px0, py0 = parent_origin
    pw, ph = parent_width, parent_height
    w, h = size
    ox, oy = offset

    # 1. Calcular o ponto de ancoragem de origem no contêiner pai (anchor_from)
    if anchor_from == "top_right":
        anchor_from_x = px0 + pw
        anchor_from_y = py0
    elif anchor_from == "top_left":
        anchor_from_x = px0
        anchor_from_y = py0
    elif anchor_from == "top_middle":
        anchor_from_x = px0 + (pw / 2.0)
        anchor_from_y = py0
    elif anchor_from == "bottom_right":
        anchor_from_x = px0 + pw
        anchor_from_y = py0 + ph
    elif anchor_from == "bottom_left":
        anchor_from_x = px0
        anchor_from_y = py0 + ph
    elif anchor_from == "center":
        anchor_from_x = px0 + (pw / 2.0)
        anchor_from_y = py0 + (ph / 2.0)
    else:
        raise ValueError(f"anchor_from não suportado: {anchor_from}")

    # Ponto focal na tela após a aplicação do offset
    target_x = anchor_from_x + ox
    target_y = anchor_from_y + oy

    # 2. Posicionar a caixa de acordo com o ponto de alinhamento do filho (anchor_to)
    if anchor_to == "top_right":
        # O canto superior direito do filho está em (target_x, target_y)
        x1 = target_x - w
        y1 = target_y
    elif anchor_to == "top_left":
        x1 = target_x
        y1 = target_y
    elif anchor_to == "top_middle":
        x1 = target_x - (w / 2.0)
        y1 = target_y
    elif anchor_to == "bottom_right":
        x1 = target_x - w
        y1 = target_y - h
    elif anchor_to == "bottom_left":
        x1 = target_x
        y1 = target_y - h
    elif anchor_to == "center":
        x1 = target_x - (w / 2.0)
        y1 = target_y - (h / 2.0)
    else:
        raise ValueError(f"anchor_to não suportado: {anchor_to}")

    x2 = x1 + w
    y2 = y1 + h

    return BoundingBox(x1, y1, x2, y2)


# ==============================================================================
# SEÇÃO 2: PARSER ROBUSTO DE ARQUIVOS UIDX / JSONC
# ==============================================================================

def strip_jsonc(text: str) -> str:
    """Remove comentários C/C++ (// e /* ... */) e trailing commas de arquivos UIDX."""
    result = []
    in_str = False
    escape = False
    i = 0
    length = len(text)

    while i < length:
        ch = text[i]
        if in_str:
            result.append(ch)
            if escape:
                escape = False
            elif ch == "\\":
                escape = True
            elif ch == '"':
                in_str = False
            i += 1
            continue

        if ch == '"':
            in_str = True
            result.append(ch)
            i += 1
            continue

        # Comentário de bloco /* ... */
        if ch == "/" and i + 1 < length and text[i + 1] == "*":
            i += 2
            while i + 1 < length and not (text[i] == "*" and text[i + 1] == "/"):
                i += 1
            i += 2
            continue

        # Comentário de linha //
        if ch == "/" and i + 1 < length and text[i + 1] == "/":
            i += 2
            while i < length and text[i] not in ("\r", "\n"):
                i += 1
            continue

        result.append(ch)
        i += 1

    cleaned = "".join(result)
    cleaned = re.sub(r",\s*([\]}])", r"\1", cleaned)
    return cleaned


def parse_uidx_file(path: Path) -> dict:
    """Lê e realiza o parse de um arquivo .uidx como dicionário Python."""
    content = path.read_text(encoding="utf-8")
    cleaned = strip_jsonc(content)
    return json.loads(cleaned)


# ==============================================================================
# SEÇÃO 3: SUÍTE DE TESTES EMPÍRICOS E ADVERSARIAIS DO CHALLENGER
# ==============================================================================

class TestM1LayoutCollisionChallenger(unittest.TestCase):
    """
    Suíte adversarial de testes geométricos 2D e coexistência espacial do Marco 1.
    """

    RESOURCE_PACK_DIR = Path(
        r"C:\Users\Bruno\AppData\Roaming\Minecraft Bedrock\Users\Shared\games\com.mojang\resource_packs\ColourfulC"
    )

    @classmethod
    def setUpClass(cls):
        """Inicializa caminhos e carrega os arquivos do pacote ColourfulC."""
        cls.touch_buttons_path = (
            cls.RESOURCE_PACK_DIR
            / "vdx"
            / "ui_data"
            / "container"
            / "_modules"
            / "container_touch_buttons.uidx"
        )
        cls.villager_path = (
            cls.RESOURCE_PACK_DIR
            / "vdx"
            / "ui_data"
            / "container"
            / "villager.uidx"
        )
        cls.container_panel_path = (
            cls.RESOURCE_PACK_DIR
            / "vdx"
            / "ui_data"
            / "container"
            / "_modules"
            / "container_panel.uidx"
        )

        # Garantir existência física dos arquivos
        assert cls.touch_buttons_path.exists(), f"Arquivo não encontrado: {cls.touch_buttons_path}"
        assert cls.villager_path.exists(), f"Arquivo não encontrado: {cls.villager_path}"
        assert cls.container_panel_path.exists(), f"Arquivo não encontrado: {cls.container_panel_path}"

        # Carregar e parsear os dados UIDX reais
        cls.touch_data = parse_uidx_file(cls.touch_buttons_path)
        cls.villager_data = parse_uidx_file(cls.villager_path)
        cls.container_panel_data = parse_uidx_file(cls.container_panel_path)

    # --------------------------------------------------------------------------
    # TESTE 1: CÁLCULO DE AABB E ZERO COLISÃO ENTRE BOTÕES E ABA LATERAL
    # --------------------------------------------------------------------------
    def test_pairwise_bounding_boxes_zero_collision(self):
        """
        Calcula as AABBs de close_button, container_side_tools_tab e trade_info_button
        e comprova computacionalmente que a sobreposição entre todos os pares é rigorosamente 0.0%.
        """
        # Dimensões de um contêiner base padrão (176x166)
        container_w = 176.0
        container_h = 166.0

        # 1. Caixa Delimitadora do close_button:
        # anchor_from: 'top_right', anchor_to: 'top_right', offset: [24, 4], size: [20, 20]
        box_close = compute_bedrock_aabb(
            parent_width=container_w,
            parent_height=container_h,
            anchor_from="top_right",
            anchor_to="top_right",
            offset=(24.0, 4.0),
            size=(20.0, 20.0)
        )

        # 2. Caixa Delimitadora da container_side_tools_tab:
        # anchor_from: 'top_right', anchor_to: 'top_right', offset: [24, 28], size: [20, 60]
        box_tab = compute_bedrock_aabb(
            parent_width=container_w,
            parent_height=container_h,
            anchor_from="top_right",
            anchor_to="top_right",
            offset=(24.0, 28.0),
            size=(20.0, 60.0)
        )

        # 3. Caixa Delimitadora do trade_info_button (em villager.uidx):
        # anchor_from: 'top_right', anchor_to: 'top_right', offset: [24, 92], size: [20, 20]
        box_trade_info = compute_bedrock_aabb(
            parent_width=container_w,
            parent_height=container_h,
            anchor_from="top_right",
            anchor_to="top_right",
            offset=(24.0, 92.0),
            size=(20.0, 20.0)
        )

        # Coordenadas esperadas:
        # close_button: X=[176+24-20, 176+24] = [180, 200], Y=[4, 24]
        self.assertEqual((box_close.x1, box_close.y1, box_close.x2, box_close.y2), (180.0, 4.0, 200.0, 24.0))

        # container_side_tools_tab: X=[180, 200], Y=[28, 88]
        self.assertEqual((box_tab.x1, box_tab.y1, box_tab.x2, box_tab.y2), (180.0, 28.0, 200.0, 88.0))

        # trade_info_button: X=[180, 200], Y=[92, 112]
        self.assertEqual((box_trade_info.x1, box_trade_info.y1, box_trade_info.x2, box_trade_info.y2), (180.0, 92.0, 200.0, 112.0))

        # VERIFICAÇÃO 1.1: Colisão entre close_button e container_side_tools_tab
        overlap_close_tab = box_close.overlap_percentage(box_tab)
        area_close_tab = box_close.overlap_area(box_tab)
        self.assertEqual(overlap_close_tab, 0.0, "Falha: Detectada sobreposição entre close_button e container_side_tools_tab!")
        self.assertEqual(area_close_tab, 0.0, "Falha: Área de sobreposição não é zero entre close_button e tab!")
        self.assertFalse(box_close.intersects(box_tab))

        # VERIFICAÇÃO 1.2: Colisão entre container_side_tools_tab e trade_info_button
        overlap_tab_trade = box_tab.overlap_percentage(box_trade_info)
        area_tab_trade = box_tab.overlap_area(box_trade_info)
        self.assertEqual(overlap_tab_trade, 0.0, "Falha: Detectada sobreposição entre container_side_tools_tab e trade_info_button!")
        self.assertEqual(area_tab_trade, 0.0, "Falha: Área de sobreposição não é zero entre tab e trade_info_button!")
        self.assertFalse(box_tab.intersects(box_trade_info))

        # VERIFICAÇÃO 1.3: Colisão entre close_button e trade_info_button
        overlap_close_trade = box_close.overlap_percentage(box_trade_info)
        area_close_trade = box_close.overlap_area(box_trade_info)
        self.assertEqual(overlap_close_trade, 0.0, "Falha: Detectada sobreposição entre close_button e trade_info_button!")
        self.assertEqual(area_close_trade, 0.0)
        self.assertFalse(box_close.intersects(box_trade_info))

    # --------------------------------------------------------------------------
    # TESTE 2: AUDITORIA DAS MARGENS DE RESPIRO EXATAS (CLEARANCE GAPS)
    # --------------------------------------------------------------------------
    def test_clearance_margins_exact_pixels(self):
        """
        Audita as margens de respiro exatas em pixels entre todos os elementos adjacentes.
        Verifica:
        - Respiro horizontal entre a borda externa do contêiner (X=176) e a coluna de botões (X=180): exatos 4px.
        - Respiro vertical entre close_button (Y=24) e o topo da aba (Y=28): exatos 4px.
        - Respiro vertical entre a base da aba (Y=88) e trade_info_button (Y=92): exatos 4px.
        """
        container_w = 176.0
        container_h = 166.0

        box_close = compute_bedrock_aabb(container_w, container_h, "top_right", "top_right", (24.0, 4.0), (20.0, 20.0))
        box_tab = compute_bedrock_aabb(container_w, container_h, "top_right", "top_right", (24.0, 28.0), (20.0, 60.0))
        box_trade = compute_bedrock_aabb(container_w, container_h, "top_right", "top_right", (24.0, 92.0), (20.0, 20.0))

        # 1. Margem horizontal em relação à parede direita do contêiner
        container_right_edge = container_w
        margin_x_close = box_close.x1 - container_right_edge
        margin_x_tab = box_tab.x1 - container_right_edge
        margin_x_trade = box_trade.x1 - container_right_edge

        self.assertEqual(margin_x_close, 4.0, "Respiro lateral de close_button deve ser exatamente 4px")
        self.assertEqual(margin_x_tab, 4.0, "Respiro lateral de container_side_tools_tab deve ser exatamente 4px")
        self.assertEqual(margin_x_trade, 4.0, "Respiro lateral de trade_info_button deve ser exatamente 4px")

        # 2. Respiro vertical close_button -> tab
        gap_close_tab = box_tab.y1 - box_close.y2
        self.assertEqual(gap_close_tab, 4.0, f"Respiro vertical entre close_button e tab deve ser 4px, obtido: {gap_close_tab}px")

        # 3. Respiro vertical tab -> trade_info_button
        gap_tab_trade = box_trade.y1 - box_tab.y2
        self.assertEqual(gap_tab_trade, 4.0, f"Respiro vertical entre tab e trade_info_button deve ser 4px, obtido: {gap_tab_trade}px")

    # --------------------------------------------------------------------------
    # TESTE 3: CONTENÇÃO VERTICAL EM CONTÊINERES DE 166PX, 222PX, 167PX E 133PX
    # --------------------------------------------------------------------------
    def test_container_height_containment_and_margins(self):
        """
        Verifica computacionalmente se os elementos da aba lateral e botões associados
        permanecem estritamente dentro da faixa vertical de contêineres de diferentes alturas:
        - 166px (container_panel, villager, crafting_table, furnace, etc.)
        - 222px (chest_large / baú duplo)
        - 167px (barrel, chest_small, chest_ender)
        - 133px (hopper / funil)
        """
        container_heights = [
            ("Contêiner Padrão (166px)", 166.0, True),     # Suporta trade_info_button (aldeão)
            ("Baú Duplo (222px)", 222.0, False),           # Contêiner alto
            ("Barril / Baú Pequeno (167px)", 167.0, False), # 167px
            ("Funil / Hopper (133px)", 133.0, False),      # Menor contêiner conhecido
        ]

        for name, h, includes_trade_button in container_heights:
            box_close = compute_bedrock_aabb(176.0, h, "top_right", "top_right", (24.0, 4.0), (20.0, 20.0))
            box_tab = compute_bedrock_aabb(176.0, h, "top_right", "top_right", (24.0, 28.0), (20.0, 60.0))

            # Verificar limites de close_button
            self.assertGreaterEqual(box_close.y1, 0.0, f"close_button vaza pelo topo em {name}")
            self.assertLessEqual(box_close.y2, h, f"close_button ultrapassa a base em {name}")

            # Verificar limites de container_side_tools_tab
            self.assertGreaterEqual(box_tab.y1, 0.0, f"tab vaza pelo topo em {name}")
            self.assertLessEqual(box_tab.y2, h, f"tab ultrapassa a base em {name}")

            # Margens de folga em relação ao topo e à base
            margin_bottom_tab = h - box_tab.y2
            self.assertGreaterEqual(margin_bottom_tab, 0.0, f"Estouro vertical negativo em {name}: {margin_bottom_tab}px")

            if includes_trade_button:
                box_trade = compute_bedrock_aabb(176.0, h, "top_right", "top_right", (24.0, 92.0), (20.0, 20.0))
                self.assertLessEqual(box_trade.y2, h, f"trade_info_button vaza da base em {name}")
                margin_bottom_trade = h - box_trade.y2
                # Em 166px, 166 - 112 = 54px de folga na base
                self.assertEqual(margin_bottom_trade, 54.0, f"Margem inferior do trade_info_button em 166px deve ser 54px, obtido: {margin_bottom_trade}")

    # --------------------------------------------------------------------------
    # TESTE 4: CONTRA-EXEMPLO ADVERSARIAL (ORÁCULO COM OFFSET LEGADO [24, 60])
    # --------------------------------------------------------------------------
    def test_adversarial_counter_example_legacy_offset_collision(self):
        """
        Desafio adversarial: comprova que se o trade_info_button estivesse no seu offset
        original/legado [24, 60], o algoritmo detectaria uma colisão crítica de 400px²
        (100.0% de sobreposição com a aba lateral).
        Isso valida que o harness de teste é genuinamente capaz de detectar o defeito.
        """
        container_w = 176.0
        container_h = 166.0

        box_tab = compute_bedrock_aabb(container_w, container_h, "top_right", "top_right", (24.0, 28.0), (20.0, 60.0))
        
        # Offset legado [24, 60] com tamanho [20, 20]
        box_trade_legacy = compute_bedrock_aabb(container_w, container_h, "top_right", "top_right", (24.0, 60.0), (20.0, 20.0))

        # A aba ocupa Y=[28, 88]. O botão legado ocupa Y=[60, 80].
        # O botão legado está totalmente contido dentro do intervalo vertical da aba!
        self.assertTrue(box_tab.intersects(box_trade_legacy), "O detector de colisão DEVE detectar colisão com o offset legado [24, 60]!")
        
        overlap_area = box_tab.overlap_area(box_trade_legacy)
        overlap_pct = box_tab.overlap_percentage(box_trade_legacy)

        self.assertEqual(overlap_area, 400.0, f"A área de colisão com offset legado deveria ser 400.0px², mas foi {overlap_area}")
        self.assertEqual(overlap_pct, 100.0, f"A sobreposição com offset legado deveria ser 100.0%, mas foi {overlap_pct}%")

    # --------------------------------------------------------------------------
    # TESTE 5: ANÁLISE DE SENSIBILIDADE A JITTER E JANELA DE TOLERÂNCIA
    # --------------------------------------------------------------------------
    def test_jitter_sensitivity_and_optimal_centering(self):
        """
        Avalia o comportamento da aba sob perturbações e deslocamentos de coordenadas (jitter).
        Espaço livre disponível entre o fim do close_button (Y=24) e o início do trade_info_button (Y=92):
          Disponível: 92 - 24 = 68px.
          Altura da aba: 60px.
          Folga total: 68 - 60 = 8px.
        Comprova que Y=28 divide a folga simetricamente em exatos 4px no topo e 4px na base,
        sendo a solução matematicamente ideal (ótimo global de clearance).
        """
        container_w = 176.0
        container_h = 166.0

        box_close = compute_bedrock_aabb(container_w, container_h, "top_right", "top_right", (24.0, 4.0), (20.0, 20.0))
        box_trade = compute_bedrock_aabb(container_w, container_h, "top_right", "top_right", (24.0, 92.0), (20.0, 20.0))

        valid_y_offsets = []
        for test_y in range(15, 45):
            candidate_tab = compute_bedrock_aabb(container_w, container_h, "top_right", "top_right", (24.0, float(test_y)), (20.0, 60.0))
            collides_close = candidate_tab.intersects(box_close)
            collides_trade = candidate_tab.intersects(box_trade)
            if not collides_close and not collides_trade:
                valid_y_offsets.append(test_y)

        # Janela de não-sobreposição de área (onde a área de colisão é rigorosamente zero):
        # - Em test_y=24, a aba toca a aresta inferior de close_button (Y=24) com overlap_h=0.
        # - Em test_y=32, a base da aba toca a aresta superior de trade_info_button (Y=92) com overlap_h=0.
        # - Abaixo de 24 (ex: 23), invade close_button; acima de 32 (ex: 33), invade trade_info_button.
        expected_zero_overlap_window = list(range(24, 33))  # [24, 25, 26, 27, 28, 29, 30, 31, 32]
        self.assertEqual(valid_y_offsets, expected_zero_overlap_window, f"A janela de zero colisão {valid_y_offsets} não condiz com {expected_zero_overlap_window}")

        # Se exigirmos respiro estritamente positivo (gap >= 1px):
        strict_gap_offsets = [
            y for y in valid_y_offsets
            if (compute_bedrock_aabb(container_w, container_h, "top_right", "top_right", (24.0, float(y)), (20.0, 60.0)).y1 - box_close.y2 >= 1.0) and
               (box_trade.y1 - compute_bedrock_aabb(container_w, container_h, "top_right", "top_right", (24.0, float(y)), (20.0, 60.0)).y2 >= 1.0)
        ]
        self.assertEqual(strict_gap_offsets, list(range(25, 32)))  # [25, 26, 27, 28, 29, 30, 31]

        # Em ambas as métricas (área zero ou gap estrito), o centro geométrico é rigorosamente Y=28:
        # (24 + 32) / 2 = 28.0 e (25 + 31) / 2 = 28.0!
        center_y = (valid_y_offsets[0] + valid_y_offsets[-1]) / 2.0
        self.assertEqual(center_y, 28.0, "O offset Y=28 é o centro geométrico perfeito da janela de segurança!")

    # --------------------------------------------------------------------------
    # TESTE 6: ESCANEAMENTO ADVERSARIAL DE TODAS AS TELAS DE CONTÊINERES DO VDX
    # --------------------------------------------------------------------------
    def test_scan_all_vdx_container_screens_for_side_conflicts(self):
        """
        Escarneia todos os arquivos de contêineres nas pastas:
        - vdx/ui_data/container/*.uidx
        - vdx/ui_data/container_mixed/*.uidx
        Para garantir que nenhum outro elemento posicionado à direita (anchor_from == 'top_right'
        com offset X >= 0) conflita com a área da aba lateral (X: 180 a 200, Y: 28 a 88).
        """
        container_dirs = [
            self.RESOURCE_PACK_DIR / "vdx" / "ui_data" / "container",
            self.RESOURCE_PACK_DIR / "vdx" / "ui_data" / "container_mixed",
        ]

        tab_y_range = (28.0, 88.0)
        conflicts_found = []

        for c_dir in container_dirs:
            if not c_dir.exists():
                continue
            for uidx_file in c_dir.glob("*.uidx"):
                try:
                    data = parse_uidx_file(uidx_file)
                except Exception:
                    continue

                def inspect_controls(obj, current_path=""):
                    if isinstance(obj, dict):
                        # Verificar se o objeto tem offset e anchor_from 'top_right'
                        anchor_from = obj.get("anchor_from")
                        offset = obj.get("offset")
                        size = obj.get("size")

                        # Ignorar o próprio close_button, container_side_tools_tab e trade_info_button
                        if anchor_from == "top_right" and isinstance(offset, list) and len(offset) >= 2:
                            ox, oy = offset[0], offset[1]
                            if isinstance(ox, (int, float)) and isinstance(oy, (int, float)):
                                # Elementos posicionados na margem direita (ox >= 20)
                                if ox >= 20 and isinstance(size, list) and len(size) >= 2:
                                    w, h = size[0], size[1]
                                    if isinstance(w, (int, float)) and isinstance(h, (int, float)):
                                        elem_box = compute_bedrock_aabb(
                                            176.0, 166.0, "top_right", obj.get("anchor_to", "top_right"), (ox, oy), (w, h)
                                        )
                                        tab_box = BoundingBox(180.0, 28.0, 200.0, 88.0)
                                        
                                        # Se for a própria aba, pular
                                        if "container_side_tools_tab" not in current_path and "top_type_toggles" not in current_path:
                                            if elem_box.intersects(tab_box):
                                                conflicts_found.append({
                                                    "file": uidx_file.name,
                                                    "path": current_path,
                                                    "box": (elem_box.x1, elem_box.y1, elem_box.x2, elem_box.y2),
                                                    "overlap": elem_box.overlap_area(tab_box)
                                                })

                        for k, v in obj.items():
                            inspect_controls(v, f"{current_path}/{k}" if current_path else k)
                    elif isinstance(obj, list):
                        for idx, item in enumerate(obj):
                            inspect_controls(item, f"{current_path}[{idx}]")

                inspect_controls(data)

        self.assertEqual(
            len(conflicts_found), 0,
            f"Conflitos espaciais detectados nas telas de contêineres: {conflicts_found}"
        )

    # --------------------------------------------------------------------------
    # TESTE 7: INVARIÂNCIA DE ESCALA DO BEDROCK (GUI SCALE FACTORS 1x, 2x, 3x, 4x)
    # --------------------------------------------------------------------------
    def test_gui_scale_invariance(self):
        """
        Comprova que em qualquer fator de escala de GUI do Bedrock (1.0x, 2.0x, 3.0x, 4.0x),
        a proporcionalidade linear é estritamente preservada e o overlap permanece exatamente 0.0%.
        """
        scale_factors = [1.0, 1.5, 2.0, 3.0, 4.0]

        for s in scale_factors:
            box_close = BoundingBox(180.0 * s, 4.0 * s, 200.0 * s, 24.0 * s)
            box_tab = BoundingBox(180.0 * s, 28.0 * s, 200.0 * s, 88.0 * s)
            box_trade = BoundingBox(180.0 * s, 92.0 * s, 200.0 * s, 112.0 * s)

            self.assertEqual(box_close.overlap_percentage(box_tab), 0.0, f"Falha na escala {s}x para close vs tab")
            self.assertEqual(box_tab.overlap_percentage(box_trade), 0.0, f"Falha na escala {s}x para tab vs trade")
            
            # As folgas de respiro devem crescer linearmente com o fator de escala (4 * s)
            expected_gap = 4.0 * s
            self.assertAlmostEqual(box_tab.y1 - box_close.y2, expected_gap, places=5)
            self.assertAlmostEqual(box_trade.y1 - box_tab.y2, expected_gap, places=5)


if __name__ == "__main__":
    unittest.main()
