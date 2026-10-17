import os
import sys

from PIL import Image, ImageDraw

ASSETS = "app/src/main/assets"
RES = "app/src/main/res"
SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
EXTENSIONS = (".jpg", ".jpeg", ".png", ".webp")


def find_avatar():
    # The avatar is the first landscape (or square) image in the assets folder.
    for name in sorted(os.listdir(ASSETS)):
        if not name.lower().endswith(EXTENSIONS):
            continue
        path = os.path.join(ASSETS, name)
        with Image.open(path) as image:
            width, height = image.size
        if width >= height:
            return path
    return None


def face_square(image):
    # A square crop around the middle of the photo, where the face is.
    width, height = image.size
    side = int(min(width, height) * 0.75)
    center_x = width * 0.5
    center_y = height * 0.48
    left = int(min(max(center_x - side / 2, 0), width - side))
    top = int(min(max(center_y - side / 2, 0), height - side))
    return image.crop((left, top, left + side, top + side))


def main():
    path = find_avatar()
    if path is None:
        print("ERROR: no landscape avatar image found in " + ASSETS)
        sys.exit(1)
    print("Using avatar: " + path)

    if hasattr(Image, "Resampling"):
        resample = Image.Resampling.LANCZOS
    else:
        resample = Image.LANCZOS

    with Image.open(path) as source:
        square = face_square(source.convert("RGBA"))

    for density, size in SIZES.items():
        folder = os.path.join(RES, "mipmap-" + density)
        os.makedirs(folder, exist_ok=True)

        icon = square.resize((size, size), resample)
        icon.save(os.path.join(folder, "ic_launcher.png"))

        scale = 4
        mask = Image.new("L", (size * scale, size * scale), 0)
        ImageDraw.Draw(mask).ellipse(
            (0, 0, size * scale - 1, size * scale - 1), fill=255
        )
        mask = mask.resize((size, size), resample)
        round_icon = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        round_icon.paste(icon, (0, 0), mask)
        round_icon.save(os.path.join(folder, "ic_launcher_round.png"))

    print("Icons created")


main()
