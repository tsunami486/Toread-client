from pathlib import Path

from PIL import Image, ImageDraw


BRAND_BACKGROUND = (78, 95, 159, 255)
ANDROID_DENSITIES = {
    "mdpi": (48, 108),
    "hdpi": (72, 162),
    "xhdpi": (96, 216),
    "xxhdpi": (144, 324),
    "xxxhdpi": (192, 432),
}

BRANDING_DIR = Path(__file__).resolve().parent
APP_DIR = BRANDING_DIR.parent
ANDROID_RES_DIR = APP_DIR / "androidApp" / "src" / "main" / "res"
DESKTOP_RES_DIR = APP_DIR / "desktopApp" / "src" / "main" / "resources"
FOREGROUND_PATH = BRANDING_DIR / "tongyue-icon-foreground.png"


def resize(image: Image.Image, size: int) -> Image.Image:
    return image.resize((size, size), Image.Resampling.LANCZOS)


def colored_background(size: int, *, round_icon: bool) -> Image.Image:
    background = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(background)
    if round_icon:
        draw.ellipse((0, 0, size - 1, size - 1), fill=BRAND_BACKGROUND)
    else:
        radius = max(2, round(size * 0.22))
        draw.rounded_rectangle((0, 0, size - 1, size - 1), radius=radius, fill=BRAND_BACKGROUND)
    return background


def composite_icon(foreground: Image.Image, size: int, *, round_icon: bool) -> Image.Image:
    background = colored_background(size, round_icon=round_icon)
    background.alpha_composite(resize(foreground, size))
    return background


def monochrome_foreground(foreground: Image.Image, size: int) -> Image.Image:
    alpha = resize(foreground.getchannel("A"), size)
    result = Image.new("RGBA", (size, size), (255, 255, 255, 0))
    result.putalpha(alpha)
    return result


def save_png(image: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, format="PNG", optimize=True)


def main() -> None:
    foreground = Image.open(FOREGROUND_PATH).convert("RGBA")

    master = composite_icon(foreground, 1024, round_icon=False)
    save_png(master, BRANDING_DIR / "tongyue-icon.png")

    for density, (legacy_size, adaptive_size) in ANDROID_DENSITIES.items():
        mipmap_dir = ANDROID_RES_DIR / f"mipmap-{density}"
        save_png(
            composite_icon(foreground, legacy_size, round_icon=False),
            mipmap_dir / "ic_launcher.png",
        )
        save_png(
            composite_icon(foreground, legacy_size, round_icon=True),
            mipmap_dir / "ic_launcher_round.png",
        )

        drawable_dir = ANDROID_RES_DIR / f"drawable-{density}"
        save_png(resize(foreground, adaptive_size), drawable_dir / "ic_launcher_foreground.png")
        save_png(
            monochrome_foreground(foreground, adaptive_size),
            drawable_dir / "ic_launcher_monochrome.png",
        )

    desktop_icon = resize(master, 512)
    save_png(desktop_icon, DESKTOP_RES_DIR / "tongyue-icon.png")
    DESKTOP_RES_DIR.mkdir(parents=True, exist_ok=True)
    master.save(
        DESKTOP_RES_DIR / "tongyue-icon.ico",
        format="ICO",
        sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)],
    )


if __name__ == "__main__":
    main()
