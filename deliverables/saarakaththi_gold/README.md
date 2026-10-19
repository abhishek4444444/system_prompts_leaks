# Saarakaththi — Gold Moody Pack 🎬✨

Video reference: **"Color Grading Breakdown from a Short Film (Saarakaththi)"** (colourist: Siyayoudeen)
Look: **Warm Golden Moody** — gold/amber highlights + warm skin tones, deep teal shadows, crushed blacks (Tamil crime short-film cinematic feel).

---

## 📦 Pack mein kya hai

| File | Kya hai |
|---|---|
| `Saarakaththi_Gold_Moody.dng` | **Lightroom preset (DNG format)** — develop settings embedded ke saath |
| `Saarakaththi_Gold_Moody.xmp` | Bonus: same preset `.xmp` (Lightroom Classic / Camera Raw ke liye) |
| `Gold_Motion_Cinematic.xml` | **Gold Motion app effect XML** — same look + gold shimmer sweep, glow, motion trail, vignette, grain |
| `build_dng.py` | DNG generator script (re-generate/tweak ke liye) |
| `preview_before_after.png` | Before → After preview (left = original, right = gold moody grade) |

---

## 📱 Lightroom Mobile — DNG preset kaise use karein

1. `Saarakaththi_Gold_Moody.dng` ko download karo aur phone mein save karo.
2. Lightroom Mobile → **Add Photo** → ye DNG import karo.
3. DNG kholo — gold-moody grade already applied dikhega (settings DNG ke andar embedded hain).
4. Top-right **⋯ (three dots)** → **Create Preset** (ya **Copy Settings** → apni photo pe **Paste Settings**).
5. Preset group "Saarakaththi Gold" choose karke save karo — ab har photo pe 1 tap mein lag jayega.

> Tip: Agar look heavy lage to "Effect Intensity" / Exposure sliders thoda adjust kar lena — har camera/phone footage pe thoda fine-tune chahiye hota hai.

## 💻 Lightroom Classic / Camera Raw

- **Option A:** DNG ko import karo → Develop module → Settings copy karo → apni photo pe paste.
- **Option B:** Presets panel → **+ → Import Presets** → `Saarakaththi_Gold_Moody.xmp` select karo.

---

## 🎞️ Gold Motion — effect XML kaise use karein

`Gold_Motion_Cinematic.xml` bilkul usi format mein hai jaise aapka `universal_fx_pro.xml` (Alight-Motion-style custom effect).

1. XML ko app ke effects folder mein copy karo (jaise `universal_fx_pro.xml` use karte ho, waise hi) ya app ke "Import Effect" se load karo.
2. Apni clip pe **Gold Motion Cinematic** effect apply karo.
3. Bas — default settings hi **Saarakaththi gold look** dete hain (warm gold highlights, teal shadows, crushed blacks, golden glow, vignette, grain).

### ✨ Gold shimmer sweep animation (keyframe)

- Effect controls mein **"Sweep Position (animate me)"** pe jao.
- Clip ke start pe value `0`, end pe `1` keyframe karo → screen pe gold shimmer sweep chalega.
- **Sweep Angle / Sweep Width / Shimmer Strength** se sweep ka direction aur style badlo.

### 🎛️ Quick controls

| Section | Controls |
|---|---|
| Gold Grade | Exposure, Contrast, Black Crush, Warmth, Gold Highlights (hue+strength), Teal Shadows (hue+strength), Saturation, Vibrance |
| Golden Glow | Intensity, Radius, Threshold, Gold Warmth |
| Shimmer Sweep | Strength, Position (keyframeable), Width, Angle, Sparkle Scale |
| Motion Trail | Strength, Direction, Length, Decay |
| Look | Vignette Amount/Softness, Film Grain, Grain Seed |
| Blend | 11 blend modes + opacity |

---

## 🎨 Grade recipe (kya set hai)

- **WB:** 6900K, Tint +14 (warm golden base)
- **Tone:** Exposure +0.15, Contrast +18, Highlights −25, Shadows −32, Whites −10, Blacks −28 (crushed moody blacks)
- **Color:** Vibrance +12, Saturation −8; greens −35 sat (muted), orange skin +8 lum (glowing skin)
- **Split Tone:** Highlights hue 42 (gold) sat 28 · Shadows hue 205 (teal) sat 22
- **Tone Curve:** soft S-curve + slightly crushed shadow toe

Agar video ka exact look aur alag lage to batana — XMP/XML values tweak karke updated pack de dunga. 🙌
