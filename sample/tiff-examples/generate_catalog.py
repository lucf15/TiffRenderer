from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
RESOURCES = ROOT / 'sample/shared/src/commonMain/composeResources'
FONTS = RESOURCES / 'font'
SIZE = (1200, 1680)
INK = '#202823'
PAPER = '#F6F4EE'
ORANGE = '#D65E3E'
SAGE = '#7B9477'
BLUE = '#294B62'
YELLOW = '#E4D788'


def font(size, weight='regular'):
    return ImageFont.truetype(str(FONTS / f'inter_{weight}.ttf'), size)


def text(draw, xy, value, size, fill=INK, weight='regular'):
    draw.text(xy, value, font=font(size, weight), fill=fill, spacing=int(size * .08))


def page(number, colour=PAPER):
    image = Image.new('RGB', SIZE, colour)
    draw = ImageDraw.Draw(image)
    colour_ink = PAPER if colour == INK else INK
    text(draw, (76, 48), 'FORM & FIELD', 15, colour_ink, 'semibold')
    text(draw, (925, 48), 'VOL. 01 / 2026', 13, colour_ink, 'medium')
    draw.line((76, 90, 1124, 90), fill=colour_ink, width=1)
    text(draw, (76, 1618), 'A TIFFRENDERER SAMPLE PUBLICATION', 11, colour_ink, 'medium')
    text(draw, (1090, 1614), f'{number:02}', 16, colour_ink, 'medium')
    return image, draw


def composition(image, box, background, foreground, alternate):
    x, y, right, bottom = box
    width, height = right - x, bottom - y
    artwork = Image.new('RGB', (width * 2, height * 2), background)
    draw = ImageDraw.Draw(artwork)
    w, h = artwork.size
    draw.rectangle((w*.12, h*.15, w*.88, h*.90), fill=alternate)
    draw.ellipse((w*.12, h*.01, w*.88, h*.78), fill=foreground)
    draw.rectangle((w*.12, h*.39, w*.88, h*.90), fill=foreground)
    draw.rectangle((w*.46, h*.53, w*.88, h*.90), fill=background)
    draw.ellipse((w*.46, h*.36, w*.88, h*.73), fill=background)
    image.paste(artwork.resize((width, height), Image.Resampling.LANCZOS), (x, y))


pages = []
image, draw = page(1)
text(draw, (70, 132), 'Form', 166, weight='semibold')
text(draw, (66, 292), '& Field', 166, weight='semibold')
text(draw, (890, 178), 'NOTES ON\nCOLOUR\nAND FORM', 18, weight='medium')
composition(image, (76, 508, 1124, 1372), ORANGE, PAPER, BLUE)
text(draw, (76, 1420), 'A study of light, colour\nand everyday shapes.', 33, weight='medium')
text(draw, (870, 1472), 'FOUR PAGES.\nA CLOSER LOOK.', 14, weight='medium')
pages.append(image)

image, draw = page(2)
text(draw, (76, 154), 'The shape\nof a day.', 92, weight='semibold')
text(draw, (702, 208), 'Small observations.\nUnexpected combinations.\nA different point of view.', 24)
boxes = [(76, 466, 574, 940), (626, 466, 1124, 940), (76, 1020, 574, 1494), (626, 1020, 1124, 1494)]
palettes = [(SAGE, PAPER, BLUE), (YELLOW, ORANGE, PAPER), (BLUE, SAGE, YELLOW), (ORANGE, YELLOW, BLUE)]
for index, (box, palette) in enumerate(zip(boxes, palettes), 1):
    composition(image, box, *palette)
    text(draw, (box[0], box[3]+14), f'STUDY {index:02} / EVERYDAY GEOMETRY', 12, weight='medium')
pages.append(image)

image, draw = page(3, INK)
text(draw, (76, 150), 'Colour,\nconsidered.', 105, PAPER, 'semibold')
draw.rectangle((0, 508, 1200, 1140), fill=ORANGE)
for offset, colour in enumerate([BLUE, SAGE, YELLOW, PAPER]):
    x = 60 + offset*270
    draw.ellipse((x, 616, x+270, 886), fill=colour)
    draw.rectangle((x, 750, x+270, 1028), fill=colour)
for x, title, body in [(76, '01 / CONTRAST', 'One colour changes\nthe way we see another.'), (444, '02 / BALANCE', 'Leave space for the\neye to find its way.'), (810, '03 / RHYTHM', 'A simple shape.\nA new perspective.')]:
    text(draw, (x, 1240), title, 14, PAPER, 'semibold')
    text(draw, (x, 1290), body, 22, PAPER)
pages.append(image)

image, draw = page(4, '#E9D5C6')
text(draw, (76, 152), 'Look a little\ncloser.', 100, weight='semibold')
text(draw, (76, 442), 'Four pages. One TIFF. Every detail intact.', 25, weight='medium')
for row in range(12):
    for column in range(20):
        x, y = 76+column*52, 574+row*52
        colour = [ORANGE, SAGE, BLUE, PAPER][(row+column)%4]
        draw.ellipse((x, y, x+36, y+36), fill=colour)
text(draw, (76, 1288), 'Designed to be explored.', 38, weight='semibold')
text(draw, (76, 1360), 'Zoom in. Move between pages. Try another document.\nThis publication is rendered locally by TiffRenderer.', 25)
text(draw, (76, 1498), '1200 × 1680 PX  /  LZW COMPRESSION  /  RGBA RENDERING', 13, weight='medium')
pages.append(image)

output = RESOURCES / 'files/example_catalog.tiff'
pages[0].save(output, save_all=True, append_images=pages[1:], compression='tiff_lzw', dpi=(144, 144))
poster = pages[0].copy()
poster.thumbnail((280, 360))
poster.save(RESOURCES / 'drawable/example_catalog.png')
print(f'{output}: {len(pages)} pages, {output.stat().st_size:,} bytes')
