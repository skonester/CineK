"""Build Android launcher and TV artwork from CineK's checked-in icon."""

from pathlib import Path
from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "android/app/src/main/res"
SOURCE = Image.open(ROOT / "icon.png").convert("RGBA")
GOLD = (252, 191, 29, 255)
CHARCOAL = (27, 27, 28, 255)


def logo(size: int, fraction: float = 0.82) -> Image.Image:
    result = Image.new("RGBA", (size, size))
    side = round(size * fraction)
    artwork = SOURCE.resize((side, side), Image.Resampling.LANCZOS)
    result.alpha_composite(artwork, ((size - side) // 2, (size - side) // 2))
    return result


def launcher(size: int) -> Image.Image:
    result = Image.new("RGBA", (size, size), CHARCOAL)
    result.alpha_composite(logo(size, 0.91))
    return result.convert("RGB")


def main() -> None:
    drawable = RES / "drawable"
    drawable.mkdir(parents=True, exist_ok=True)
    logo(512, 0.96).save(drawable / "cinek_logo.png")

    densities = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
    for density, scale in densities.items():
        size = round(48 * scale)
        layer_size = round(108 * scale)
        folder = RES / f"mipmap-{density}"
        folder.mkdir(parents=True, exist_ok=True)
        for old in folder.glob("ic_launcher*.webp"):
            old.unlink()
        launcher(size).save(folder / "ic_launcher.png")
        launcher(size).save(folder / "ic_launcher_round.png")
        logo(layer_size, 0.62).save(folder / "ic_launcher_foreground.png")
        mono = Image.new("RGBA", (layer_size, layer_size))
        icon_alpha = logo(layer_size, 0.62).getchannel("A")
        mono.paste((255, 255, 255, 255), (0, 0, layer_size, layer_size), icon_alpha)
        mono.save(folder / "ic_launcher_monochrome.png")

    banner = Image.new("RGB", (320, 180), CHARCOAL[:3])
    banner.paste(logo(144, 0.90), (20, 18), logo(144, 0.90))
    draw = ImageDraw.Draw(banner)
    font_path = RES / "font/outfit_semibold.ttf"
    font = ImageFont.truetype(str(font_path), 42)
    draw.text((148, 66), "CineK", fill=GOLD[:3], font=font)
    banner.save(drawable / "cinek_banner.png")


if __name__ == "__main__":
    main()
