# Sarkari Alert (सरकारी अलर्ट)

Sarkari naukri, exam aur online form ke alerts — ek hi Android app me, Hindi UI ke saath, bina ads.

**Final APK:** `dist/SarkariAlert-v1.0.1.apk` (~100 KB) — [GitHub Release se download karo](https://github.com/abhishek4444444/system_prompts_leaks/releases/tag/v1.0) ya is repo ke `sarkari-alert/dist/` folder se lo.

## Features

- **Seed data (offline-first):** app pehli baar khulne par 10 real, current (Oct 2026) bharti ke entries dikhata hai — RRB NTPC Graduate + 10+2 (CEN 06 & 07/2026), UPSSSC Inter Level 1784 pad + JE, UGC NET Dec 2026, Indian Army TGC-145, MP ESB Sub Engineer 1700 pad, DSSSB, IOCL Apprentice, GSSC.
- **Live feeds:** Settings me koi bhi RSS/Atom/JSON feed add ki ja sakti hai (default: `https://sarkariresult.com.cm/feed/`). JSON me `alerts/items/jobs/data` arrays support hain, fields flexible (title/last_date/url/…).
- **Deadline badges:** har alert par "X din baki" (hara = safe, lal = ≤3 din / aaj aakhri din / samapt).
- **Categories + search:** railway, SSC, UPSC, banking, defence, police, shikshan, rajya, PSU chips + free-text search.
- **Save + reminders:** kisi form ko ⭐ save karne par uski last date se 3 din aur 1 din pehle notification (AlarmManager `setExactAndAllowWhileIdle`). Reboot par BootReceiver sab dobara schedule karta hai.
- **Daily check:** roz subah 8:30 bade naye alerts par notification (Settings se off ho sakta hai).
- **Share + open:** official website direct open, WhatsApp share with ready text.
- Storage: SQLite, sab on-device. Minimum Android 7.0 (API 24), target API 29.

## Install

1. APK phone me copy karo (USB/WhatsApp/Drive).
2. File kholo → "Unknown sources / अज्ञात स्रोत" allow karo (sirf pehli baar).
3. Install → app drawer me **सरकारी अलर्ट** icon.

APK **APK Signature Scheme v2** se signed hai (RSA-2048, self-signed cert "Sarkari Alert"), aur androguard se cross-verified.

## Rebuild (bina Android SDK / Gradle)

Is machine par dl.google.com band hai, isliye poora toolchain GitHub/npm/PyPI se joda gaya hai. Scripts `build/` me hain:

| Tool | Source |
|------|--------|
| `aapt2` | npm `aaptjs3` (bundled linux binary) |
| Java (JRE) | PyPI `jdk4py` (Temurin 25) — `/tmp/jenv` |
| ECJ compiler | `SDI4Apps/liferay` repo ka committed `ecj.jar` (api.github.com raw) |
| `dx` dexer | AOSP `platform_dalvik` `dx/src` — android-22 bootclasspath ke saath ECJ se compile kiya gaya |
| `android.jar` | `Sable/android-platforms` sparse clone (API 3–37 sab) |
| Sign/pack | `build/package_sign.py` — pure-Python APK v2 signer (cryptography lib) |

Dobara banane ke liye (naye session me):

```bash
pip install jdk4py cryptography && python -m venv /tmp/jenv && /tmp/jenv/bin/pip install jdk4py cryptography
git clone --filter=blob:none --no-checkout --depth 1 https://github.com/Sable/android-platforms /tmp/android-platforms
cd /tmp/android-platforms && git sparse-checkout init --cone && git sparse-checkout set android-22 android-28 && git checkout
curl -sL $(curl -s https://registry.npmjs.org/aaptjs3 | python3 -c "import json,sys;d=json.load(sys.stdin);v=d['dist-tags']['latest'];print(d['versions'][v]['dist']['tarball'])") | tar -xz -C /tmp && chmod +x /tmp/package/bin/x64/linux/aapt2
# ecj.jar: gh api repos/SDI4Apps/liferay/contents/dependencies/com.liferay.jasper.jspc/lib/ecj.jar -H "Accept: application/vnd.github.raw" > /tmp/tools/ecj.jar
# dx.jar: aosp dx/src ko ecj se android-22 bootclasspath par compile karke jar banao (tools.sh me path set)
./build/build.sh
```

Signing key `~/keys/sarkari-alert/` me hai — **isse sambhal ke rakho**, agla update isi key se sign hoga warna phone par "App not installed" aayega (key change = reinstall).

## Notes

- Seed data October 2026 ka hai; aane wale naye forms default RSS feed se aayenge, ya koi aur feed add karo.
- Yeh app kisi sarakaari sanstha se juda nahi hai. Data public aggregators se aata hai — aavedan se pehle official notification zaroor padho.
