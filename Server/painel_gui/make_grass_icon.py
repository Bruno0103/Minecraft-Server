"""Gera um ícone ICO pixel-art de bloco de grama sem dependências externas."""

from pathlib import Path
import struct


def pixel(size: int, x: int, y: int) -> tuple[int, int, int, int]:
    top = y < size * 0.36
    grass = (48, 170, 72, 255) if (x + y * 3) % 17 else (106, 211, 85, 255)
    dirt = (135, 82, 43, 255) if (x + y) % 13 else (167, 103, 53, 255)
    edge = x < size * 0.06 or x > size * 0.94 or y < size * 0.06 or y > size * 0.94
    return (35, 101, 51, 255) if edge else (grass if top else dirt)


def dib(size: int) -> bytes:
    rows = []
    for y in range(size - 1, -1, -1):
        rows.append(b"".join(bytes(pixel(size, x, y)[2::-1]) + b"\xff" for x in range(size)))
    pixels = b"".join(rows)
    mask = b"\x00" * (((size + 31) // 32) * 4 * size)
    header = struct.pack("<IiiHHIIiiII", 40, size, size * 2, 1, 32, 0,
                         len(pixels), 0, 0, 0, 0)
    return header + pixels + mask


def main() -> None:
    images = [dib(size) for size in (16, 32, 48, 256)]
    header = struct.pack("<HHH", 0, 1, len(images))
    offset = 6 + 16 * len(images)
    entries = []
    for size, image in zip((16, 32, 48, 256), images):
        dimension = 0 if size == 256 else size
        entries.append(struct.pack("<BBBBHHII", dimension, dimension, 0, 0, 1, 32,
                                   len(image), offset))
        offset += len(image)
    target = Path(__file__).with_name("grass_block.ico")
    target.write_bytes(header + b"".join(entries) + b"".join(images))
    print(target)


if __name__ == "__main__":
    main()
