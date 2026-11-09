#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
==============================================================================
Script Utilitário: Gerador e Empacotador Automático de Texturas PBR para Minecraft Bedrock
==============================================================================
Compatível com a engine gráfica Render Dragon, iluminação Deferred Lighting e RTX
(Vibrant Visuals) do Minecraft Bedrock 1.21+.

Funcionalidades implementadas:
1. Empacotamento unificado de canais no mapa MER (Metalness, Emissive, Roughness):
   - Canal Vermelho (R): Metalicidade (0 = dielétrico, 255 = metal puro).
   - Canal Verde (G): Emissividade (intensidade do brilho emitido no escuro).
   - Canal Azul (B): Rugosidade (0 = espelhado/liso, 255 = fosco/áspero).
2. Conversão matemática de mapas normais OpenGL (Y-up) para DirectX (Y-down).
3. Geração programática de mapas normais Tangent Space DirectX a partir do Albedo/Altura
   utilizando convolução periódica com operadores de gradiente de Sobel.
4. Geração automática dos manifestos canônicos .texture_set.json da Mojang.
"""

import os
import json
import sys
from pathlib import Path
from PIL import Image, ImageOps
import numpy as np


def pack_mer_image(metallic_path: str, emissive_path: str, roughness_path: str, output_path: str) -> None:
    """
    Empacota imagens em escala de cinza isoladas nos canais R, G e B de uma única textura MER.

    Parâmetros:
        metallic_path (str): Caminho para o mapa de metalicidade em escala de cinza.
        emissive_path (str): Caminho para o mapa de emissividade/brilho em escala de cinza.
        roughness_path (str): Caminho para o mapa de rugosidade/acabamento em escala de cinza.
        output_path (str): Caminho de saída onde o arquivo PNG unificado será gravado.
    """
    # 1. Processamento do Canal Vermelho (Metalicidade)
    if metallic_path and os.path.exists(metallic_path):
        r_chan = Image.open(metallic_path).convert("L")
    else:
        # Padrão: 0 (material não metálico / dielétrico como pedra ou tecido)
        r_chan = Image.new("L", (16, 16), 0)

    # 2. Processamento do Canal Verde (Emissividade / Glow)
    if emissive_path and os.path.exists(emissive_path):
        g_chan = Image.open(emissive_path).convert("L")
    else:
        # Padrão: 0 (sem emissão de luz própria)
        g_chan = Image.new("L", r_chan.size, 0)

    # 3. Processamento do Canal Azul (Rugosidade)
    if roughness_path and os.path.exists(roughness_path):
        b_chan = Image.open(roughness_path).convert("L")
    else:
        # Padrão: 204 (~80% de rugosidade / acabamento levemente fosco padrão)
        b_chan = Image.new("L", r_chan.size, 204)

    # Harmonização da resolução das imagens para o tamanho do canal base
    target_size = r_chan.size
    if g_chan.size != target_size:
        g_chan = g_chan.resize(target_size, Image.Resampling.NEAREST)
    if b_chan.size != target_size:
        b_chan = b_chan.resize(target_size, Image.Resampling.NEAREST)

    # Fusão dos três canais isolados em uma única textura RGB
    mer_image = Image.merge("RGB", (r_chan, g_chan, b_chan))
    output_dir = os.path.dirname(os.path.abspath(output_path))
    if output_dir:
        os.makedirs(output_dir, exist_ok=True)
    mer_image.save(output_path, "PNG")
    print(f"✅ [PBR] Mapa MER salvo com sucesso em: {output_path}")


def convert_opengl_to_directx_normal(normal_path: str, output_path: str) -> None:
    """
    Converte um mapa normal gerado em padrão OpenGL (Y-up) para o formato DirectX (Y-down)
    exigido pelo motor gráfico Render Dragon do Minecraft Bedrock.
    Inverte cirurgicamente o canal Verde (G = 255 - G).

    Parâmetros:
        normal_path (str): Caminho do mapa normal OpenGL de entrada.
        output_path (str): Caminho de saída do mapa normal DirectX convertido.
    """
    img = Image.open(normal_path).convert("RGB")
    r, g, b = img.split()
    # Inversão do canal Verde (eixo Y da normal)
    g_inverted = ImageOps.invert(g)
    dx_normal = Image.merge("RGB", (r, g_inverted, b))
    output_dir = os.path.dirname(os.path.abspath(output_path))
    if output_dir:
        os.makedirs(output_dir, exist_ok=True)
    dx_normal.save(output_path, "PNG")
    print(f"✅ [PBR] Normal DirectX convertida e salva em: {output_path}")


def generate_normal_from_albedo(albedo_path: str, output_path: str, strength: float = 2.0) -> None:
    """
    Gera um mapa normal Tangent Space DirectX a partir de uma textura de Albedo ou Altura.
    Utiliza operadores de gradiente de Sobel com convolução periódica circular para garantir
    o ladrilhamento contínuo (seamless tiling) entre blocos adjacentes.

    Parâmetros:
        albedo_path (str): Caminho da textura albedo/difusa em PNG.
        output_path (str): Caminho de saída do mapa normal DirectX gerado.
        strength (float): Intensidade do relevo simulado (padrão: 2.0).
    """
    albedo = Image.open(albedo_path).convert("L")
    arr = np.array(albedo, dtype=np.float32) / 255.0

    # Inicialização dos tensores de gradiente
    gx = np.zeros_like(arr)
    gy = np.zeros_like(arr)

    # Diferença central com rolagem circular periódica (np.roll) para bordas perfeitas
    gx[:, :] = (np.roll(arr, -1, axis=1) - np.roll(arr, 1, axis=1)) * 0.5 * strength
    gy[:, :] = (np.roll(arr, -1, axis=0) - np.roll(arr, 1, axis=0)) * 0.5 * strength

    # No padrão DirectX, o vetor Y aponta para baixo (inversão de sinal do gradiente vertical)
    dx = gx
    dy = -gy
    dz = np.ones_like(arr)

    # Normalização vetorial euclidiana unitária: L = sqrt(dx² + dy² + dz²)
    length = np.sqrt(dx**2 + dy**2 + dz**2)
    nx = (dx / length) * 0.5 + 0.5
    ny = (dy / length) * 0.5 + 0.5
    nz = (dz / length) * 0.5 + 0.5

    # Quantização para inteiros de 8 bits sem sinal (0 a 255)
    r = (nx * 255.0).clip(0, 255).astype(np.uint8)
    g = (ny * 255.0).clip(0, 255).astype(np.uint8)
    b = (nz * 255.0).clip(0, 255).astype(np.uint8)

    normal_img = Image.merge("RGB", (Image.fromarray(r), Image.fromarray(g), Image.fromarray(b)))
    output_dir = os.path.dirname(os.path.abspath(output_path))
    if output_dir:
        os.makedirs(output_dir, exist_ok=True)
    normal_img.save(output_path, "PNG")
    print(f"✅ [PBR] Normal DirectX calculada via Sobel em: {output_path}")


def create_texture_set_json(texture_name: str, directory: str, has_normal: bool = True, has_mer: bool = True) -> str:
    """
    Gera o arquivo de manifesto formal <texture_name>.texture_set.json da Mojang.

    Parâmetros:
        texture_name (str): Identificador da textura sem extensão (ex: 'obsidian_block').
        directory (str): Diretório onde o arquivo .texture_set.json será gravado.
        has_normal (bool): Se verdadeiro, inclui a chave 'normal'.
        has_mer (bool): Se verdadeiro, inclui a chave 'metalness_emissive_roughness'.

    Retorno:
        str: Caminho absoluto do arquivo JSON gerado.
    """
    texture_set = {
        "format_version": "1.21.30",
        "minecraft:texture_set": {
            "color": texture_name
        }
    }

    if has_normal:
        texture_set["minecraft:texture_set"]["normal"] = f"{texture_name}_normal"
    if has_mer:
        texture_set["minecraft:texture_set"]["metalness_emissive_roughness"] = f"{texture_name}_mer"

    json_path = os.path.join(directory, f"{texture_name}.texture_set.json")
    with open(json_path, "w", encoding="utf-8") as f:
        json.dump(texture_set, f, indent=2)

    print(f"✅ [PBR] Manifesto Texture Set gerado em: {json_path}")
    return json_path


if __name__ == "__main__":
    print("====================================================================")
    print("Script Utilitário PBR Bedrock para Render Dragon carregado com sucesso.")
    print("====================================================================")

