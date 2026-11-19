#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
==============================================================================
Script Utilitário: Gerador e Empacotador Automático de Texturas PBR (26.65+)
==============================================================================
Compatível com a engine gráfica Render Dragon, Vibrant Visuals, iluminação
Deferred Lighting e RTX do Minecraft Bedrock Edition 1.26.65+ (26.65+).

Funcionalidades implementadas:
1. Empacotamento unificado de canais no mapa MER de 3 canais RGB
   (Metalness, Emissive, Roughness):
   - Canal Vermelho (R): Metalicidade (0 = dielétrico, 255 = metal puro).
   - Canal Verde (G): Emissividade (intensidade do brilho emitido no escuro).
   - Canal Azul (B): Rugosidade (0 = espelhado/liso, 255 = fosco/áspero).
2. Empacotamento unificado de canais no mapa MERS de 4 canais RGBA
   (Metalness, Emissive, Roughness, Subsurface Scattering) para Vibrant Visuals:
   - Canal Vermelho (R): Metalicidade.
   - Canal Verde (G): Emissividade.
   - Canal Azul (B): Rugosidade.
   - Canal Alfa (A): Dispersão Subsuperficial / Subsurface Scattering
     (0 = totalmente opaco, 255 = máxima translucidez interna para folhas, gelo,
     cristais, cera e pele).
3. Conversão matemática de mapas normais OpenGL (Y-up) para DirectX (Y-down).
4. Geração programática de mapas normais Tangent Space DirectX a partir do Albedo/Altura
   utilizando diferenças centrais periódicas para ladrilhamento contínuo.
5. Geração automática dos manifestos canônicos `.texture_set.json` da Mojang
   em conformidade estrita com o schema da Blockception (`format_version: "1.21.30"`),
   garantindo exclusividade mútua entre `mer` e `mers` e entre `normal` e `heightmap`.
"""

import os
import json
from PIL import Image, ImageOps
import numpy as np


def _load_grayscale_channel(
    image_path: str,
    default_value: int,
    fallback_size: tuple[int, int] = (16, 16),
) -> Image.Image:
    """
    Carrega um mapa auxiliar em escala de cinza (8 bits) ou cria um plano uniforme
    com o valor padrão caso o arquivo de entrada não seja fornecido ou não exista.

    Parâmetros:
        image_path (str): Caminho para o arquivo de imagem de entrada (pode ser vazio).
        default_value (int): Valor constante de preenchimento (0 a 255) no fallback.
        fallback_size (tuple[int, int]): Dimensão (largura, altura) usada no fallback.

    Retorno:
        Image.Image: Imagem Pillow no modo "L" (luminância / canal único de 8 bits).
    """
    if image_path and os.path.exists(image_path):
        # Converte diretamente para luminância (0-255) garantindo canal único compatível
        return Image.open(image_path).convert("L")
    return Image.new("L", fallback_size, default_value)


def _match_channel_size(channel: Image.Image, target_size: tuple[int, int]) -> Image.Image:
    """
    Harmoniza a resolução de um canal para coincidir com a dimensão base usando
    interpolação vizinho mais próximo (NEAREST), preservando pixels nítidos de pixel art.

    Parâmetros:
        channel (Image.Image): Canal em escala de cinza a ser ajustado.
        target_size (tuple[int, int]): Resolução de destino (largura, altura).

    Retorno:
        Image.Image: Canal redimensionado (ou original se já tiver a mesma dimensão).
    """
    if channel.size != target_size:
        return channel.resize(target_size, Image.Resampling.NEAREST)
    return channel


def _ensure_parent_dir(output_path: str) -> None:
    """
    Garante que o diretório pai do caminho de saída exista antes da gravação em disco.

    Parâmetros:
        output_path (str): Caminho completo ou relativo do arquivo de saída.
    """
    output_dir = os.path.dirname(os.path.abspath(output_path))
    if output_dir:
        os.makedirs(output_dir, exist_ok=True)


def pack_mer_image(
    metallic_path: str,
    emissive_path: str,
    roughness_path: str,
    output_path: str,
) -> None:
    """
    Empacota imagens em escala de cinza isoladas nos canais R, G e B de uma única
    textura MER (`*_mer.png`) compatível com Deferred Lighting / RTX.

    Parâmetros:
        metallic_path (str): Caminho para o mapa de metalicidade em escala de cinza.
        emissive_path (str): Caminho para o mapa de emissividade/brilho em escala de cinza.
        roughness_path (str): Caminho para o mapa de rugosidade/acabamento em escala de cinza.
        output_path (str): Caminho de saída onde o arquivo PNG unificado será gravado.
    """
    # 1. Processamento do Canal Vermelho (Metalicidade; padrão 0 = material dielétrico)
    r_chan = _load_grayscale_channel(metallic_path, default_value=0)
    target_size = r_chan.size

    # 2. Processamento do Canal Verde (Emissividade; padrão 0 = sem emissão própria)
    g_chan = _match_channel_size(
        _load_grayscale_channel(emissive_path, default_value=0, fallback_size=target_size),
        target_size,
    )

    # 3. Processamento do Canal Azul (Rugosidade; padrão 204 ≈ 80% fosco)
    b_chan = _match_channel_size(
        _load_grayscale_channel(roughness_path, default_value=204, fallback_size=target_size),
        target_size,
    )

    # Fusão dos três canais isolados em uma única textura RGB de 24 bits
    mer_image = Image.merge("RGB", (r_chan, g_chan, b_chan))
    _ensure_parent_dir(output_path)
    mer_image.save(output_path, "PNG")
    print(f"✅ [PBR/MER] Mapa MER salvo com sucesso em: {output_path}")


def pack_mers_image(
    metallic_path: str,
    emissive_path: str,
    roughness_path: str,
    subsurface_path: str,
    output_path: str,
) -> None:
    """
    Empacota quatro mapas em escala de cinza nos canais R, G, B e Alpha de uma
    textura MERS (`*_mers.png`) para o pipeline Vibrant Visuals do Bedrock 1.26.65+.

    Mapeamento dos canais RGBA:
        - R (Red): Metalicidade (0 = dielétrico, 255 = metálico).
        - G (Green): Emissividade (0 = sem luz própria, 255 = brilho máximo).
        - B (Blue): Rugosidade (0 = espelhado, 255 = totalmente fosco).
        - A (Alpha): Subsurface Scattering (0 = opaco, 255 = dispersão subsuperficial máxima).

    Parâmetros:
        metallic_path (str): Caminho do mapa de metalicidade em escala de cinza.
        emissive_path (str): Caminho do mapa de emissividade em escala de cinza.
        roughness_path (str): Caminho do mapa de rugosidade em escala de cinza.
        subsurface_path (str): Caminho do mapa de dispersão subsuperficial em escala de cinza.
        output_path (str): Caminho de saída onde o arquivo PNG RGBA (`*_mers.png`) será salvo.
    """
    # 1. Processamento dos três canais PBR clássicos (R, G, B)
    r_chan = _load_grayscale_channel(metallic_path, default_value=0)
    target_size = r_chan.size

    g_chan = _match_channel_size(
        _load_grayscale_channel(emissive_path, default_value=0, fallback_size=target_size),
        target_size,
    )
    b_chan = _match_channel_size(
        _load_grayscale_channel(roughness_path, default_value=204, fallback_size=target_size),
        target_size,
    )

    # 2. Processamento do Canal Alfa (Subsurface Scattering; padrão 0 = material opaco)
    a_chan = _match_channel_size(
        _load_grayscale_channel(subsurface_path, default_value=0, fallback_size=target_size),
        target_size,
    )

    # Fusão dos quatro canais em uma textura RGBA de 32 bits para Vibrant Visuals
    mers_image = Image.merge("RGBA", (r_chan, g_chan, b_chan, a_chan))
    _ensure_parent_dir(output_path)
    mers_image.save(output_path, "PNG")
    print(f"✅ [PBR/MERS] Mapa MERS (4 canais RGBA) salvo com sucesso em: {output_path}")


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
    # Inversão do canal Verde (eixo Y da normal em Tangent Space)
    g_inverted = ImageOps.invert(g)
    dx_normal = Image.merge("RGB", (r, g_inverted, b))
    _ensure_parent_dir(output_path)
    dx_normal.save(output_path, "PNG")
    print(f"✅ [PBR] Normal DirectX convertida e salva em: {output_path}")


def generate_normal_from_albedo(albedo_path: str, output_path: str, strength: float = 2.0) -> None:
    """
    Gera um mapa normal Tangent Space DirectX a partir de uma textura de Albedo ou Altura.
    Utiliza diferenças centrais com rolagem circular periódica para garantir o
    ladrilhamento contínuo (seamless tiling) entre blocos adjacentes.

    Parâmetros:
        albedo_path (str): Caminho da textura albedo/difusa em PNG.
        output_path (str): Caminho de saída do mapa normal DirectX gerado.
        strength (float): Intensidade do relevo simulado (padrão: 2.0).
    """
    albedo = Image.open(albedo_path).convert("L")
    arr = np.array(albedo, dtype=np.float32) / 255.0

    # Diferença central com rolagem circular periódica (np.roll) para bordas contínuas
    gx = (np.roll(arr, -1, axis=1) - np.roll(arr, 1, axis=1)) * 0.5 * strength
    gy = (np.roll(arr, -1, axis=0) - np.roll(arr, 1, axis=0)) * 0.5 * strength

    # No padrão DirectX (Y-down), o eixo vertical Y da normal aponta para baixo
    dx = gx
    dy = -gy
    dz = np.ones_like(arr)

    # Normalização vetorial euclidiana unitária: L = sqrt(dx² + dy² + dz²)
    length = np.sqrt(dx**2 + dy**2 + dz**2)
    nx = (dx / length) * 0.5 + 0.5
    ny = (dy / length) * 0.5 + 0.5
    nz = (nz_val := (dz / length) * 0.5 + 0.5)  # noqa: F841 - clareza na conversão para 8 bits

    # Quantização para inteiros de 8 bits sem sinal (0 a 255)
    r = (nx * 255.0).clip(0, 255).astype(np.uint8)
    g = (ny * 255.0).clip(0, 255).astype(np.uint8)
    b = (nz * 255.0).clip(0, 255).astype(np.uint8)

    normal_img = Image.merge("RGB", (Image.fromarray(r), Image.fromarray(g), Image.fromarray(b)))
    _ensure_parent_dir(output_path)
    normal_img.save(output_path, "PNG")
    print(f"✅ [PBR] Normal DirectX calculada e salva em: {output_path}")


def create_texture_set_json(
    texture_name: str,
    directory: str,
    has_normal: bool = True,
    has_mer: bool = True,
    has_mers: bool = False,
) -> str:
    """
    Gera o arquivo de manifesto formal `<texture_name>.texture_set.json` da Mojang
    para Bedrock 1.21.30+ / 1.26.65+ (Render Dragon / Vibrant Visuals).

    Nota de Schema (Blockception `resource/textures/texture_set.json`):
        - `format_version` aceita `"1.16.100"` ou `"1.21.30"` (usamos `"1.21.30"`).
        - `metalness_emissive_roughness` (MER) e `metalness_emissive_roughness_subsurface`
          (MERS) são mutuamente exclusivos; quando `has_mers=True`, apenas o MERS é gravado.

    Parâmetros:
        texture_name (str): Identificador da textura sem extensão (ex.: 'obsidian_block').
        directory (str): Diretório onde o arquivo `.texture_set.json` será gravado.
        has_normal (bool): Se verdadeiro, inclui a chave `'normal'` (`<nome>_normal`).
        has_mer (bool): Se verdadeiro (e `has_mers=False`), inclui `'metalness_emissive_roughness'`.
        has_mers (bool): Se verdadeiro, inclui `'metalness_emissive_roughness_subsurface'`
                         (`*_mers`) e suprime `'metalness_emissive_roughness'` para evitar conflito.

    Retorno:
        str: Caminho absoluto do arquivo JSON gerado.
    """
    texture_set: dict = {
        "format_version": "1.21.30",
        "minecraft:texture_set": {
            "color": texture_name
        },
    }

    if has_normal:
        texture_set["minecraft:texture_set"]["normal"] = f"{texture_name}_normal"

    # Garante conformidade com a regra "not" do schema: nunca emitir MER e MERS juntos
    if has_mers:
        texture_set["minecraft:texture_set"]["metalness_emissive_roughness_subsurface"] = (
            f"{texture_name}_mers"
        )
    elif has_mer:
        texture_set["minecraft:texture_set"]["metalness_emissive_roughness"] = (
            f"{texture_name}_mer"
        )

    os.makedirs(directory, exist_ok=True)
    json_path = os.path.join(directory, f"{texture_name}.texture_set.json")
    with open(json_path, "w", encoding="utf-8") as f:
        json.dump(texture_set, f, indent=2)

    print(f"✅ [PBR] Manifesto Texture Set gerado em: {json_path}")
    return json_path


if __name__ == "__main__":
    print("====================================================================")
    print("Script Utilitário PBR (MER / MERS) para Bedrock 26.65+ carregado.")
    print("====================================================================")
