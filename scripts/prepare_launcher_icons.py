#!/usr/bin/env python3
"""Apply a circular alpha silhouette to the WifiPad launcher PNGs.

Uses only Python's standard library so the CircleCI Android image needs no pip.
The original RGB artwork is retained; the corners outside the circular mask
become transparent for Android launcher shaping.
"""
from __future__ import annotations
import math
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ICON_PATHS = (
    ROOT / "controller-app/src/main/res/mipmap-nodpi/ic_launcher.png",
    ROOT / "receiver-app/src/main/res/mipmap-nodpi/ic_launcher.png",
)
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
CIRCLE_DIAMETER_RATIO = 0.98


def read_png(path: Path):
    data = path.read_bytes()
    if not data.startswith(PNG_SIGNATURE):
        raise ValueError(f"Not a PNG file: {path}")
    chunks = []
    offset = len(PNG_SIGNATURE)
    header = None
    compressed = bytearray()
    saw_end = False
    while offset < len(data):
        if offset + 12 > len(data):
            raise ValueError(f"Truncated PNG chunk in {path}")
        length = struct.unpack_from(">I", data, offset)[0]
        chunk_type = data[offset + 4:offset + 8]
        end = offset + 12 + length
        if end > len(data):
            raise ValueError(f"PNG chunk exceeds file length in {path}")
        payload = data[offset + 8:offset + 8 + length]
        expected_crc = struct.unpack_from(">I", data, offset + 8 + length)[0]
        actual_crc = zlib.crc32(chunk_type + payload) & 0xFFFFFFFF
        if actual_crc != expected_crc:
            raise ValueError(f"PNG CRC mismatch in {path} ({chunk_type!r})")
        if chunk_type == b"IHDR":
            if header is not None or length != 13:
                raise ValueError(f"Invalid PNG header in {path}")
            header = struct.unpack(">IIBBBBB", payload)
        elif chunk_type == b"IDAT":
            compressed.extend(payload)
        elif chunk_type == b"IEND":
            chunks.append((chunk_type, payload))
            saw_end = True
            offset = end
            break
        else:
            chunks.append((chunk_type, payload))
        offset = end
    if header is None or not saw_end:
        raise ValueError(f"Incomplete PNG file: {path}")
    return chunks, header, bytes(compressed)


def paeth_predictor(left: int, above: int, upper_left: int) -> int:
    estimate = left + above - upper_left
    dl = abs(estimate - left)
    da = abs(estimate - above)
    du = abs(estimate - upper_left)
    if dl <= da and dl <= du:
        return left
    if da <= du:
        return above
    return upper_left


def decode_rgba_rows(compressed: bytes, width: int, height: int):
    raw = zlib.decompress(compressed)
    stride = width * 4
    expected_size = height * (stride + 1)
    if len(raw) != expected_size:
        raise ValueError(f"Unexpected decompressed PNG size: {len(raw)}; expected {expected_size}")
    rows = []
    offset = 0
    previous = bytearray(stride)
    for _ in range(height):
        filter_type = raw[offset]
        scanline = bytearray(raw[offset + 1:offset + 1 + stride])
        offset += stride + 1
        if filter_type == 1:  # Sub
            for i in range(stride):
                left = scanline[i - 4] if i >= 4 else 0
                scanline[i] = (scanline[i] + left) & 0xFF
        elif filter_type == 2:  # Up
            for i in range(stride):
                scanline[i] = (scanline[i] + previous[i]) & 0xFF
        elif filter_type == 3:  # Average
            for i in range(stride):
                left = scanline[i - 4] if i >= 4 else 0
                scanline[i] = (scanline[i] + ((left + previous[i]) // 2)) & 0xFF
        elif filter_type == 4:  # Paeth
            for i in range(stride):
                left = scanline[i - 4] if i >= 4 else 0
                upper_left = previous[i - 4] if i >= 4 else 0
                scanline[i] = (scanline[i] + paeth_predictor(left, previous[i], upper_left)) & 0xFF
        elif filter_type != 0:
            raise ValueError(f"Unsupported PNG filter: {filter_type}")
        rows.append(scanline)
        previous = scanline
    return rows


def encode_png(chunks, header, rows) -> bytes:
    raw = b"".join(b"\x00" + bytes(row) for row in rows)
    compressed = zlib.compress(raw, level=9)
    output = bytearray(PNG_SIGNATURE)

    def append_chunk(chunk_type: bytes, payload: bytes):
        output.extend(struct.pack(">I", len(payload)))
        output.extend(chunk_type)
        output.extend(payload)
        output.extend(struct.pack(">I", zlib.crc32(chunk_type + payload) & 0xFFFFFFFF))

    append_chunk(b"IHDR", struct.pack(">IIBBBBB", *header))
    wrote_idat = False
    for chunk_type, payload in chunks:
        if chunk_type == b"IEND":
            if not wrote_idat:
                append_chunk(b"IDAT", compressed)
                wrote_idat = True
            append_chunk(b"IEND", payload)
        elif chunk_type in (b"IHDR", b"IDAT"):
            if chunk_type == b"IDAT" and not wrote_idat:
                append_chunk(b"IDAT", compressed)
                wrote_idat = True
        else:
            append_chunk(chunk_type, payload)
    if not wrote_idat:
        raise ValueError("PNG did not contain an IDAT chunk")
    return bytes(output)


def apply_circle(rows, width: int, height: int):
    cx = (width - 1) / 2.0
    cy = (height - 1) / 2.0
    radius = min(width, height) * CIRCLE_DIAMETER_RATIO / 2.0
    for y, row in enumerate(rows):
        dy = y - cy
        for x in range(width):
            index = x * 4 + 3
            alpha = row[index]
            if alpha == 0:
                continue
            coverage = max(0.0, min(1.0, radius + 0.5 - math.hypot(x - cx, dy)))
            if coverage < 1.0:
                row[index] = round(alpha * coverage)


def prepare_icon(path: Path):
    if not path.is_file():
        raise FileNotFoundError(f"Launcher icon not found: {path}")
    chunks, header, compressed = read_png(path)
    width, height, bit_depth, color_type, _compression, _filtering, interlace = header
    if width != height:
        raise ValueError(f"Expected square launcher icon, got {width}x{height}: {path}")
    if bit_depth != 8 or color_type != 6 or interlace != 0:
        raise ValueError(f"Expected non-interlaced 8-bit RGBA PNG: {path} (header={header})")
    rows = decode_rgba_rows(compressed, width, height)
    apply_circle(rows, width, height)
    if rows[0][3] != 0 or rows[height // 2][(width // 2) * 4 + 3] == 0:
        raise RuntimeError(f"Circular alpha mask validation failed: {path}")
    path.write_bytes(encode_png(chunks, header, rows))
    print(f"Prepared circular launcher icon: {path.relative_to(ROOT)} ({width}x{height})")


def main():
    for icon_path in ICON_PATHS:
        prepare_icon(icon_path)


if __name__ == "__main__":
    main()
