from pathlib import Path
import json
import random
import textwrap

from PIL import Image, ImageDraw, ImageFont

OUTPUT = Path(__file__).resolve().parent
FONT_DIR = Path('/System/Library/Fonts/Supplemental')
REGULAR = str(FONT_DIR / 'Arial.ttf')
BOLD = str(FONT_DIR / 'Arial Bold.ttf')

PARAGRAPHS = [
    'Questo documento contiene testo, numeri, accenti e piccoli dettagli grafici. '
    'Serve a controllare leggibilità, proporzioni e qualità durante lo scorrimento: '
    'città, qualità, perché, più, già, € 1.234,56.',
    'Ogni pagina riporta un numero ben visibile e un contenuto differente. '
    'Passando rapidamente tra le pagine, il titolo e la tabella devono rimanere '
    'nitidi e non devono comparire elementi appartenenti alla pagina precedente.',
    'Le versioni compresse e non compresse rappresentano carichi diversi. '
    'La dimensione del file non coincide con la memoria necessaria per visualizzarlo: '
    'un documento piccolo può contenere molte pagine ad alta risoluzione.',
    'Le righe sottili e il motivo in fondo alla pagina aiutano a riconoscere '
    'sfocature, scalettature e problemi di ridimensionamento. I margini devono '
    'restare regolari anche quando cambia il formato della pagina.',
]


def page(width, height, number, count, title, noise=0, mono=False):
    scale = min(width / 1240, height / 1754)
    def px(value):
        return max(1, round(value * scale))
    def font(size, bold=False):
        return ImageFont.truetype(BOLD if bold else REGULAR, px(size))

    if noise:
        rng = random.Random(4200 + number)
        lookup = bytes(255 - value % noise for value in range(256))
        paper = rng.randbytes(width * height).translate(lookup)
        image = Image.frombytes('L', (width, height), paper).convert('RGB')
    else:
        image = Image.new('RGB', (width, height), '#fffefa')
    draw = ImageDraw.Draw(image)
    margin = px(85)
    accent = ['#185c91', '#855219', '#326d48', '#783f7d'][number % 4]
    draw.rectangle((0, 0, width, px(24)), fill=accent)
    draw.text((margin, px(65)), 'TIFFRENDERER / DOCUMENTI DI PROVA', font=font(20, True), fill=accent)
    draw.text((width - px(330), px(65)), f'PAGINA {number:02d} / {count:02d}', font=font(22, True), fill=accent)
    draw.text((margin, px(125)), title, font=font(46, True), fill='#152535')
    draw.text((margin, px(190)), f'{width} × {height} pixel  |  Capitolo {number}', font=font(22), fill='#40515c')
    draw.line((margin, px(240), width - margin, px(240)), fill=accent, width=px(3))
    y = px(275)
    table_y = height - px(520)
    for section in range(4):
        heading = f'{section + 1}. ' + ['Lettura del documento', 'Navigazione multipagina', 'Dimensioni e qualità', 'Controllo dei dettagli'][section]
        paragraph = PARAGRAPHS[(section + number - 1) % len(PARAGRAPHS)]
        lines = textwrap.wrap(paragraph, width=83)
        if y + px(44) + len(lines) * px(34) + px(30) > table_y - px(30):
            break
        draw.text((margin, y), heading, font=font(26, True), fill='#152535')
        y += px(44)
        for line in lines:
            draw.text((margin, y), line, font=font(22), fill='#202a31')
            y += px(34)
        y += px(30)
    row_height = px(55)
    columns = [margin, px(650), px(860), width - margin]
    for row in range(5):
        top = table_y + row * row_height
        if row % 2 == 0:
            draw.rectangle((margin, top, width - margin, top + row_height), fill='#e8eef3')
        values = ['Voce', 'Quantità', 'Importo'] if row == 0 else [
            f'Elemento {number}.{row}', str(number * 7 + row), f'€ {number * row * 12},50'
        ]
        for col, value in enumerate(values):
            draw.text((columns[col] + px(12), top + px(12)), value, font=font(20, row == 0), fill='#152535')
    pattern_y = height - px(155)
    for i in range(180):
        x = margin + i * px(3)
        draw.line((x, pattern_y, x, pattern_y + px(45)), fill='#101820' if i % 2 == 0 else '#ffffff', width=px(1))
    draw.text((margin, height - px(75)), 'Documento sintetico • Nessun dato personale • 10 novembre 2026', font=font(18), fill='#40515c')
    draw.text((width - px(220), height - px(78)), f'{number} / {count}', font=font(26, True), fill=accent)
    return image.convert('L').point(lambda value: 255 if value > 180 else 0, mode='1') if mono else image


def make(name, count, size, compression, title, noise=0, mono=False, mixed=False):
    pages = []
    try:
        for index in range(count):
            dimensions = [(1240, 1754), (1754, 1240), (900, 1500), (1654, 2339), (2000, 1300)][index] if mixed else size
            pages.append(page(*dimensions, index + 1, count, title, noise=noise, mono=mono))
        path = OUTPUT / name
        options = dict(save_all=True, append_images=pages[1:], compression=compression, dpi=(300, 300))
        if compression == 'jpeg':
            options['quality'] = 85
        pages[0].save(path, **options)
    finally:
        for image in pages:
            image.close()
    dimensions = []
    with Image.open(path) as image:
        assert image.n_frames == count
        for index in range(count):
            image.seek(index)
            image.load()
            assert image.width > 0 and image.height > 0
            dimensions.append(list(image.size))
    result = dict(file=name, pages=count, bytes=path.stat().st_size, compression=compression, dimensions=dimensions)
    print(f'{name}: {count} pagine, {result["bytes"] / 1_000_000:.2f} MB', flush=True)
    return result


def main():
    samples = [
        make('01_testo_1_pagina_lzw.tif', 1, (1240, 1754), 'tiff_lzw', 'Relazione di esempio'),
        make('02_fax_12_pagine_group4.tif', 12, (1728, 2444), 'group4', 'Archivio documentale', mono=True),
        make('03_manuale_30_pagine_lzw.tif', 30, (1240, 1754), 'tiff_lzw', 'Manuale multipagina'),
        make('04_scansioni_8_pagine_jpeg.tif', 8, (2480, 3508), 'jpeg', 'Scansioni a colori', noise=12),
        make('05_scansioni_6_pagine_deflate.tif', 6, (1654, 2339), 'tiff_adobe_deflate', 'Archivio ad alta qualità', noise=64),
        make('06_non_compresso_3_pagine.tif', 3, (2480, 3508), 'raw', 'Documento non compresso'),
        make('07_formati_misti_5_pagine.tif', 5, None, 'tiff_lzw', 'Formati differenti', mixed=True),
    ]
    (OUTPUT / 'manifest.json').write_text(json.dumps(samples, indent=2) + '\n')
    print(f'Totale: {sum(s["bytes"] for s in samples) / 1_000_000:.2f} MB', flush=True)


if __name__ == '__main__':
    main()
