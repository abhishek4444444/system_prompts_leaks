#!/usr/bin/env python3
"""
build_dng.py — builds "Saarakaththi Gold Moody" Lightroom preset as a real DNG.

The DNG is a small LinearRaw (16-bit, uncompressed) test card with the develop
settings embedded as an XMP packet (TIFF tag 700). Import the DNG into
Lightroom (mobile or desktop) — the gold-moody grade is applied automatically;
then ••• → "Create Preset" to save it into your preset list.

Usage:  python3 build_dng.py
Output: Saarakaththi_Gold_Moody.dng (next to this script)
"""

import math
import os
import struct

W = H = 128

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "Saarakaththi_Gold_Moody.dng")

# ---------------------------------------------------------------------------
# XMP develop settings — "Warm Golden Moody" (Saarakaththi-style grade)
# ---------------------------------------------------------------------------
XMP = """<?xpacket begin="\ufeff" id="W5M0MpCehiHzreSzNTczkc9d"?>
<x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="Adobe XMP Core 5.6-c140 79.163499, 2018/08/13-16:40:22">
 <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
  <rdf:Description rdf:about=""
    xmlns:crs="http://xmlns.adobe.com/camera-raw-settings/1.0/"
    xmlns:xmp="http://ns.adobe.com/xap/1.0/"
    crs:Version="15.3"
    crs:ProcessVersion="11.0"
    crs:CameraProfile="Adobe Standard"
    crs:WhiteBalance="Custom"
    crs:Temperature="6900"
    crs:Tint="+14"
    crs:Exposure2012="+0.15"
    crs:Contrast2012="+18"
    crs:Highlights2012="-25"
    crs:Shadows2012="-32"
    crs:Whites2012="-10"
    crs:Blacks2012="-28"
    crs:Texture="+10"
    crs:Clarity2012="+12"
    crs:Dehaze="+8"
    crs:Vibrance="+12"
    crs:Saturation="-8"
    crs:HueAdjustmentRed="-3"
    crs:HueAdjustmentOrange="+5"
    crs:HueAdjustmentYellow="-12"
    crs:HueAdjustmentGreen="-25"
    crs:HueAdjustmentAqua="-10"
    crs:HueAdjustmentBlue="+15"
    crs:HueAdjustmentPurple="0"
    crs:HueAdjustmentMagenta="0"
    crs:SaturationAdjustmentRed="+5"
    crs:SaturationAdjustmentOrange="+8"
    crs:SaturationAdjustmentYellow="-15"
    crs:SaturationAdjustmentGreen="-35"
    crs:SaturationAdjustmentAqua="-8"
    crs:SaturationAdjustmentBlue="-18"
    crs:SaturationAdjustmentPurple="-5"
    crs:SaturationAdjustmentMagenta="0"
    crs:LuminanceAdjustmentRed="+3"
    crs:LuminanceAdjustmentOrange="+8"
    crs:LuminanceAdjustmentYellow="+4"
    crs:LuminanceAdjustmentGreen="-8"
    crs:LuminanceAdjustmentAqua="0"
    crs:LuminanceAdjustmentBlue="-14"
    crs:LuminanceAdjustmentPurple="0"
    crs:LuminanceAdjustmentMagenta="0"
    crs:ParametricShadows="+5"
    crs:ParametricDarks="+5"
    crs:ParametricLights="+5"
    crs:ParametricHighlights="-10"
    crs:ParametricShadowTint="50"
    crs:ParametricHighlightTint="50"
    crs:SplitToningHighlightHue="42"
    crs:SplitToningHighlightSaturation="28"
    crs:SplitToningShadowHue="205"
    crs:SplitToningShadowSaturation="22"
    crs:SplitToningBalance="+5"
    crs:ColorGradingHighlightHue="42"
    crs:ColorGradingHighlightSaturation="25"
    crs:ColorGradingMidtoneHue="35"
    crs:ColorGradingMidtoneSaturation="8"
    crs:ColorGradingShadowHue="205"
    crs:ColorGradingShadowSaturation="20"
    crs:ColorGradingBlending="50"
    crs:ColorGradingBalance="+8"
    crs:ShadowTint="48"
    crs:RedHue="+2"
    crs:RedSaturation="+5"
    crs:GreenHue="0"
    crs:GreenSaturation="0"
    crs:BlueHue="-3"
    crs:BlueSaturation="+5"
    crs:SharpenDetail="25"
    crs:ConvertToGrayscale="False"
    crs:HasSettings="True"
    crs:ToneCurveName2012="Custom">
   <crs:ToneCurvePV2012>
    <rdf:Seq>
     <rdf:li>0, 0</rdf:li>
     <rdf:li>16, 6</rdf:li>
     <rdf:li>48, 42</rdf:li>
     <rdf:li>96, 100</rdf:li>
     <rdf:li>160, 172</rdf:li>
     <rdf:li>220, 234</rdf:li>
     <rdf:li>255, 255</rdf:li>
    </rdf:Seq>
   </crs:ToneCurvePV2012>
  </rdf:Description>
 </rdf:RDF>
</x:xmpmeta>
<!--W5M0MpCehiHzreSzNTczkc9d-->
<?xpacket end="w"?>
"""

# ---------------------------------------------------------------------------
# Test-card pixels: a neutral, photo-like mini scene (the XMP does the grading)
# ---------------------------------------------------------------------------
def srgb_to_linear(v):
    return v / 12.92 if v <= 0.04045 else ((v + 0.055) / 1.055) ** 2.4


def build_pixels():
    chips = [
        (0.78, 0.16, 0.12),  # red
        (0.85, 0.50, 0.18),  # orange (skin-ish)
        (0.86, 0.75, 0.22),  # yellow
        (0.35, 0.58, 0.30),  # green
        (0.25, 0.62, 0.70),  # aqua
        (0.24, 0.38, 0.70),  # blue
        (0.60, 0.32, 0.60),  # magenta
        (0.50, 0.50, 0.50),  # gray
    ]
    data = bytearray()
    for y in range(H):
        t = y / (H - 1)  # 0 = top
        for x in range(W):
            u = x / (W - 1)
            if t < 0.60:
                k = t / 0.60
                # blue sky fading warmer near horizon
                r = 0.32 + 0.50 * k
                g = 0.46 + 0.30 * k
                b = 0.72 - 0.28 * k
            else:
                k = (t - 0.60) / 0.40
                # ground: olive-brown going dark
                r = 0.42 - 0.28 * k
                g = 0.38 - 0.24 * k
                b = 0.22 - 0.14 * k
            # sun glow
            d2 = (u - 0.72) ** 2 + (t - 0.28) ** 2
            glow = math.exp(-d2 / 0.010)
            r += 0.45 * glow
            g += 0.36 * glow
            b += 0.14 * glow
            # neutral color chips strip along the bottom
            if t > 0.90:
                idx = min(int(u * 8), 7)
                r, g, b = chips[idx]
            for v in (r, g, b):
                v = max(0.0, min(1.0, v))
                data += struct.pack("<H", int(round(srgb_to_linear(v) * 65535)))
    return bytes(data)


# ---------------------------------------------------------------------------
# Minimal little-endian TIFF/DNG writer
# ---------------------------------------------------------------------------
TYPE_SIZE = {1: 1, 2: 1, 3: 2, 4: 4, 5: 8, 7: 1, 10: 8}


def tiff_value(typ, values):
    """Pack a list of ints (or raw bytes for ASCII/BYTE) into TIFF value bytes."""
    if typ == 2:  # ASCII (NUL-terminated)
        return values if isinstance(values, bytes) else values.encode("ascii") + b"\x00"
    if typ in (1, 7):  # BYTE / UNDEFINED
        return values if isinstance(values, bytes) else bytes(values)
    out = b""
    for v in values:
        if typ == 3:
            out += struct.pack("<H", v)
        elif typ == 4:
            out += struct.pack("<I", v)
        elif typ == 5:
            out += struct.pack("<II", v[0], v[1])
        elif typ == 10:
            out += struct.pack("<ii", v[0], v[1])
        else:
            raise ValueError(typ)
    return out


def build_dng():
    xmp = XMP.encode("utf-8")
    pixels = build_pixels()

    # XYZ (D65) -> linear sRGB  (DNG ColorMatrix1: XYZ -> camera native)
    cm = [
        (32406, 10000), (-15372, 10000), (-4986, 10000),
        (-9689, 10000), (18758, 10000), (415, 10000),
        (557, 10000), (-2040, 10000), (10570, 10000),
    ]

    entries = [
        (254, 4, tiff_value(4, [0])),                       # NewSubfileType
        (256, 4, tiff_value(4, [W])),                       # ImageWidth
        (257, 4, tiff_value(4, [H])),                       # ImageLength
        (258, 3, tiff_value(3, [16, 16, 16])),              # BitsPerSample
        (259, 3, tiff_value(3, [1])),                       # Compression = none
        (262, 3, tiff_value(3, [34892])),                   # Photometric = LinearRaw
        (271, 2, tiff_value(2, "Arena Studio")),            # Make
        (272, 2, tiff_value(2, "Saarakaththi Gold Preset Camera")),  # Model
        (273, 4, b"\x00\x00\x00\x00"),                      # StripOffsets (patched later)
        (274, 3, tiff_value(3, [1])),                       # Orientation
        (277, 3, tiff_value(3, [3])),                       # SamplesPerPixel
        (278, 4, tiff_value(4, [H])),                       # RowsPerStrip
        (279, 4, tiff_value(4, [len(pixels)])),              # StripByteCounts
        (282, 5, tiff_value(5, [(72, 1)])),                 # XResolution
        (283, 5, tiff_value(5, [(72, 1)])),                 # YResolution
        (296, 3, tiff_value(3, [2])),                       # ResolutionUnit = inch
        (305, 2, tiff_value(2, "Arena AI - Lightroom Preset Builder")),  # Software
        (306, 2, tiff_value(2, "2026:10:19 14:00:00")),     # DateTime
        (700, 1, xmp),                                      # XMP packet
        (50706, 1, tiff_value(1, bytes([1, 4, 0, 0]))),     # DNGVersion 1.4.0.0
        (50707, 1, tiff_value(1, bytes([1, 1, 0, 0]))),     # DNGBackwardVersion 1.1
        (50708, 2, tiff_value(2, "Arena Saarakaththi Gold")),   # UniqueCameraModel
        (50714, 3, tiff_value(3, [0, 0, 0])),               # BlackLevel
        (50717, 3, tiff_value(3, [65535, 65535, 65535])),   # WhiteLevel
        (50721, 10, tiff_value(10, cm)),                    # ColorMatrix1
        (50727, 5, tiff_value(5, [(1, 1), (1, 1), (1, 1)])),  # AsShotNeutral
        (50778, 3, tiff_value(3, [21])),                    # CalibrationIlluminant1 = D65
    ]
    entries.sort(key=lambda e: e[0])

    n = len(entries)
    ifd_off = 8
    ifd_size = 2 + 12 * n + 4
    heap_off = ifd_off + ifd_size

    # First pass: assign out-of-line heap offsets
    heap = bytearray()
    resolved = []
    cur = heap_off
    strip_idx = None
    for i, (tag, typ, raw) in enumerate(entries):
        count = len(raw) // TYPE_SIZE[typ]
        if len(raw) <= 4:
            field = raw.ljust(4, b"\x00")
        else:
            if cur % 2:
                heap += b"\x00"
                cur += 1
            field = struct.pack("<I", cur)
            heap += raw
            cur += len(raw)
        if tag == 273:
            strip_idx = i
        resolved.append((tag, typ, count, field))

    # Image strip goes after the heap
    if cur % 2:
        heap += b"\x00"
        cur += 1
    img_off = cur

    tag_t, typ_t, cnt_t, field_t = resolved[strip_idx]
    resolved[strip_idx] = (tag_t, typ_t, cnt_t, struct.pack("<I", img_off))

    out = bytearray()
    out += b"II" + struct.pack("<HI", 42, ifd_off)
    out += struct.pack("<H", n)
    for tag, typ, count, field in resolved:
        out += struct.pack("<HHI", tag, typ, count) + field
    out += struct.pack("<I", 0)  # next IFD
    out += heap
    out += pixels

    with open(OUT, "wb") as f:
        f.write(out)
    print(f"wrote {OUT} ({len(out)} bytes)")


if __name__ == "__main__":
    build_dng()
