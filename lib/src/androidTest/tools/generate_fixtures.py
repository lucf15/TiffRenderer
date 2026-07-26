#!/usr/bin/env python3
"""Regenerates every fixture in lib/src/androidTest/assets/ from scratch.

Run from anywhere: `python3 generate_fixtures.py [output_dir]` (defaults to
lib/src/androidTest/assets/ relative to this file). Requires `tiffcp`/`tiffinfo` on PATH
(Homebrew: `brew install libtiff`) for the codec-variant step; everything else is stdlib-only.

Two fixtures this script does *not* produce -- unsupported_webp.tif and unsupported_lerc.tif --
because `tiffcp` doesn't wire up `-c webp`/`-c lerc` at all (confirmed via its own `-h` text),
regardless of what the underlying libtiff build supports. Those need a throwaway
`tifffile`+`imagecodecs` venv instead:

    python3 -m venv /tmp/tiff_venv && /tmp/tiff_venv/bin/pip install tifffile imagecodecs
    # then tifffile.imwrite(path, pages_ndarray, photometric="rgb", compression="webp"|"lerc")
"""
import os
import struct
import subprocess
import sys

DEFAULT_OUT_DIR = os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "..", "assets"
)
OUT_DIR = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_OUT_DIR


def _pack_entry(tag, typ, count, value_bytes):
    # value_bytes must already be exactly 4 bytes (padded if the type is shorter than that).
    assert len(value_bytes) == 4
    return struct.pack("<HHI", tag, typ, count) + value_bytes


def _short(v):
    return struct.pack("<HH", v & 0xFFFF, 0)


def _long(v):
    return struct.pack("<I", v)


def write_tiff(path, pages):
    """pages: list of dicts with keys:
    width, height, samples_per_pixel, photometric, pixel_bytes,
    optional: extra_samples (int tag value, e.g. 1=associated alpha), bits_per_sample (default 8)
    """
    header_size = 8
    buf = bytearray()
    buf += b"II" + struct.pack("<H", 42) + struct.pack("<I", header_size)

    # First pass: compute each page's IFD offset and strip offset.
    cursor = header_size
    layouts = []
    for page in pages:
        has_extra = "extra_samples" in page
        num_entries = 9 + (1 if has_extra else 0)
        ifd_size = 2 + num_entries * 12 + 4
        ifd_offset = cursor
        strip_offset = ifd_offset + ifd_size
        strip_size = len(page["pixel_bytes"])
        layouts.append((ifd_offset, strip_offset, strip_size))
        cursor = strip_offset + strip_size

    body = bytearray()
    for i, page in enumerate(pages):
        ifd_offset, strip_offset, strip_size = layouts[i]
        next_ifd_offset = layouts[i + 1][0] if i + 1 < len(layouts) else 0
        has_extra = "extra_samples" in page
        bits = page.get("bits_per_sample", 8)

        entries = [
            (256, 3, 1, _short(page["width"])),
            (257, 3, 1, _short(page["height"])),
            (258, 3, 1, _short(bits)),
            (259, 3, 1, _short(1)),  # Compression = none
            (262, 3, 1, _short(page["photometric"])),
            (273, 4, 1, _long(strip_offset)),
            (277, 3, 1, _short(page["samples_per_pixel"])),
            (278, 3, 1, _short(page["height"])),  # RowsPerStrip = full image, single strip
            (279, 4, 1, _long(strip_size)),
        ]
        if has_extra:
            entries.append((338, 3, 1, _short(page["extra_samples"])))
        entries.sort(key=lambda e: e[0])

        ifd_bytes = bytearray()
        ifd_bytes += struct.pack("<H", len(entries))
        for tag, typ, count, value_bytes in entries:
            ifd_bytes += _pack_entry(tag, typ, count, value_bytes)
        ifd_bytes += struct.pack("<I", next_ifd_offset)

        assert header_size + len(body) == ifd_offset, (header_size + len(body), ifd_offset)
        body += ifd_bytes
        assert header_size + len(body) == strip_offset
        body += page["pixel_bytes"]

    buf += body
    with open(path, "wb") as f:
        f.write(buf)


def solid_rgb_page(width, height, r, g, b):
    row = bytes([r, g, b]) * width
    return {
        "width": width,
        "height": height,
        "samples_per_pixel": 3,
        "photometric": 2,  # RGB
        "pixel_bytes": row * height,
    }


def solid_gray_page(width, height, gray):
    row = bytes([gray]) * width
    return {
        "width": width,
        "height": height,
        "samples_per_pixel": 1,
        "photometric": 1,  # BlackIsZero
        "pixel_bytes": row * height,
    }


def solid_rgba_page(width, height, r, g, b, a):
    row = bytes([r, g, b, a]) * width
    return {
        "width": width,
        "height": height,
        "samples_per_pixel": 4,
        "photometric": 2,
        "extra_samples": 1,  # associated alpha
        "pixel_bytes": row * height,
    }


def bilevel_checkerboard_page(width, height):
    # True 1-bit-per-pixel bilevel data, MSB-first, each row padded to a byte boundary -- CCITT
    # G3/G4 (tiffcp -c g4) genuinely requires BitsPerSample=1 source data, unlike the other
    # codecs which tolerate 8-bit RGB.
    rows = bytearray()
    row_bytes = (width + 7) // 8
    for y in range(height):
        row = bytearray(row_bytes)
        for x in range(width):
            bit_is_white = (x // 8 + y // 8) % 2 == 0
            if bit_is_white:
                row[x // 8] |= 0x80 >> (x % 8)
        rows += row
    return {
        "width": width,
        "height": height,
        "samples_per_pixel": 1,
        "bits_per_sample": 1,
        "photometric": 1,
        "pixel_bytes": bytes(rows),
    }


def main():
    os.makedirs(OUT_DIR, exist_ok=True)

    # --- Baseline sources for tiffcp to recompress with each codec -----------------------------
    base_rgb_pages = [
        solid_rgb_page(64, 48, 220, 40, 40),
        solid_rgb_page(64, 48, 40, 200, 60),
        solid_rgb_page(64, 48, 40, 90, 220),
    ]
    write_tiff(os.path.join(OUT_DIR, "_base_rgb.tif"), base_rgb_pages)

    write_tiff(os.path.join(OUT_DIR, "_base_bilevel.tif"), [bilevel_checkerboard_page(64, 48)])

    # --- Fixtures written directly (no codec involved) ------------------------------------------
    write_tiff(os.path.join(OUT_DIR, "single_page_rgb.tif"), [solid_rgb_page(32, 24, 128, 64, 200)])

    write_tiff(
        os.path.join(OUT_DIR, "varying_page_dimensions.tif"),
        [
            solid_rgb_page(10, 10, 200, 30, 30),
            solid_rgb_page(20, 15, 30, 200, 30),
            solid_rgb_page(8, 40, 30, 30, 200),
        ],
    )

    write_tiff(
        os.path.join(OUT_DIR, "rgba_associated_alpha.tif"),
        [solid_rgba_page(40, 30, 255, 0, 0, 128)],
    )

    # --- Corrupt / hostile fixtures --------------------------------------------------------------
    with open(os.path.join(OUT_DIR, "not_a_tiff.bin"), "wb") as f:
        f.write(b"this is not a TIFF file, just some arbitrary bytes\x00\x01\x02" * 8)

    # Valid header/IFD, but truncated before the strip's pixel data actually appears on disk.
    single_page_path = os.path.join(OUT_DIR, "single_page_rgb.tif")
    with open(single_page_path, "rb") as f:
        full = f.read()
    truncated = full[: len(full) - 40]
    with open(os.path.join(OUT_DIR, "truncated.tif"), "wb") as f:
        f.write(truncated)

    # 100000x100000, single strip -- passes TIFFReadDirectory (confirmed via tiffinfo) despite
    # being an unreasonable amount of pixel data.
    huge_entries = [
        (256, 4, 1, _long(100000)),
        (257, 4, 1, _long(100000)),
        (258, 3, 1, _short(8)),
        (259, 3, 1, _short(1)),
        (262, 3, 1, _short(1)),
        (273, 4, 1, _long(0)),  # placeholder
        (277, 3, 1, _short(1)),
        (278, 4, 1, _long(100000)),
        (279, 4, 1, _long(12)),
    ]
    huge_entries.sort(key=lambda e: e[0])
    header_size = 8
    ifd_size = 2 + len(huge_entries) * 12 + 4
    strip_offset = header_size + ifd_size
    huge_entries = [
        (273, 4, 1, _long(strip_offset)) if t == 273 else (t, ty, c, v) for (t, ty, c, v) in huge_entries
    ]
    buf = bytearray()
    buf += b"II" + struct.pack("<H", 42) + struct.pack("<I", header_size)
    buf += struct.pack("<H", len(huge_entries))
    for tag, typ, count, value_bytes in huge_entries:
        buf += _pack_entry(tag, typ, count, value_bytes)
    buf += struct.pack("<I", 0)
    buf += b"\xff" * 12
    with open(os.path.join(OUT_DIR, "huge_dimensions.tif"), "wb") as f:
        f.write(buf)

    # --- Codec variants, via tiffcp against the two baseline sources above ---------------------
    base_rgb = os.path.join(OUT_DIR, "_base_rgb.tif")
    base_bilevel = os.path.join(OUT_DIR, "_base_bilevel.tif")

    def tiffcp(codec, src, dst, extra_args=()):
        subprocess.run(
            ["tiffcp", "-c", codec, *extra_args, src, os.path.join(OUT_DIR, dst)], check=True
        )

    tiffcp("none", base_rgb, "supported_uncompressed.tif")
    tiffcp("lzw", base_rgb, "supported_lzw.tif")
    tiffcp("packbits", base_rgb, "supported_packbits.tif")
    tiffcp("zip", base_rgb, "supported_deflate.tif")
    tiffcp("g4", base_bilevel, "supported_ccittg4.tif")
    tiffcp("jpeg", base_rgb, "supported_jpeg.tif", extra_args=["-r", "48"])
    tiffcp("zstd", base_rgb, "unsupported_zstd.tif")
    tiffcp("lzma", base_rgb, "unsupported_lzma.tif")

    os.remove(base_rgb)
    os.remove(base_bilevel)

    print("wrote fixtures to", OUT_DIR)
    print("still needed: unsupported_webp.tif, unsupported_lerc.tif -- see this file's docstring")


if __name__ == "__main__":
    main()
