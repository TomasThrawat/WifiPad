#!/usr/bin/env python3
"""Give full-bleed black launcher icons a circular silhouette.

The original white artwork and its proportions are preserved. Only the outer
corners become transparent so Android launchers can apply their icon mask.
"""
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ICON_PATHS = (
    ROOT / "controller-app/src/main/res/mipmap-nodpi/ic_launcher.png",
    ROOT / "receiver-app/src/main/res/mipmap-nodpi/ic_launcher.png",
)
SUPERSAMPLE = 4
CIRCLE_DIAMETER_RATIO = 0.98


def prepare_icon(path: Path) -> None:
    if not path.is_file():
        raise FileNotFoundError(f"Launcher icon not found: {path}")

    with Image.open(path) as source:
        if source.format != "PNG":
            raise ValueError(f"Expected a PNG launcher icon: {path}")
        image = source.convert("RGBA")

    width, height = image.size
    if width != height:
        raise ValueError(f"Expected a square launcher icon, got {width}x{height}: {path}")

    scaled_size = width * SUPERSAMPLE
    inset = round(scaled_size * (1.0 - CIRCLE_DIAMETER_RATIO) / 2.0)
    mask = Image.new("L", (scaled_size, scaled_size), 0)
    ImageDraw.Draw(mask).ellipse(
        (inset, inset, scaled_size - 1 - inset, scaled_size - 1 - inset),
        fill=255,
    )
    mask = mask.resize(image.size, Image.Resampling.LANCZOS)
    image.putalpha(ImageChops.multiply(image.getchannel("A"), mask))
    if image.getchannel("A").getextrema()[0] != 0:
        raise RuntimeError(f"Transparent launcher corners were not created: {path}")
    image.save(path, format="PNG", optimize=True)
    print(f"Prepared circular launcher icon: {path.relative_to(ROOT)} ({width}x{height})")


def main() -> None:
    for icon_path in ICON_PATHS:
        prepare_icon(icon_path)


if __name__ == "__main__":
    main()
