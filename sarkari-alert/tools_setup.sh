#!/bin/bash
# Provision the build toolchain into /tmp (fast: pip + 2 git clones + 1 tarball).
# Heavy jars (ecj, dx, apksig) live in this repo's tools/ dir, so they never
# need downloading. Run once per fresh machine, then ./build/build.sh
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
if ! command -v /tmp/jenv/bin/python >/dev/null 2>&1; then
    python3 -m venv /tmp/jenv
    /tmp/jenv/bin/pip install -q jdk4py cryptography
fi
if [ ! -f /tmp/android-platforms/android-28/android.jar ]; then
    git clone -q --filter=blob:none --no-checkout --depth 1 \
        https://github.com/Sable/android-platforms /tmp/android-platforms
    (cd /tmp/android-platforms && git sparse-checkout init --cone \
        && git sparse-checkout set android-22 android-28 && git checkout -q)
fi
if [ ! -x /tmp/aaptjs3/package/bin/x64/linux/aapt2 ]; then
    url=$(curl -s https://registry.npmjs.org/aaptjs3 | python3 -c \
        "import json,sys;d=json.load(sys.stdin);v=d['dist-tags']['latest'];print(d['versions'][v]['dist']['tarball'])")
    curl -s -o /tmp/aaptjs3.tgz "$url"
    mkdir -p /tmp/aaptjs3 && tar -xzf /tmp/aaptjs3.tgz -C /tmp/aaptjs3
    chmod +x /tmp/aaptjs3/package/bin/x64/linux/aapt2
fi
echo "toolchain ready"
