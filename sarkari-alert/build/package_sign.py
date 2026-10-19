#!/usr/bin/env python3
"""Package aapt2 output + classes.dex into the final APK and apply
Android APK Signature Scheme v2 (RSA-PKCS1 SHA-256). Pure Python.

Usage:
  package_sign.py pack   <base.apk> <classes.dex|-> <key.pem> <cert.der> <out.apk>
  package_sign.py verify <apk>
  package_sign.py genkey <dir>
"""
import struct, sys, io, zipfile, hashlib, os, zlib

MAGIC = b"APK Sig Block 42"
V2_ID = 0x7109871A
ALG = 0x0103  # RSASSA-PKCS1-v1_5 with SHA2-256
CHUNK = 1048576

def u32(v): return struct.pack("<I", v)
def u64(v): return struct.pack("<Q", v)
def lp(b): return u32(len(b)) + b

def build_v1_files(entry_items, key, cert):
    """META-INF MANIFEST.MF + CERT.SF + CERT.RSA (JAR signing, RSA SHA-256).
    entry_items: list of (name_bytes, data_bytes) excluding META-INF/* ."""
    import base64, hashlib as _h
    from cryptography.hazmat.primitives import hashes, serialization
    mf_sections = [b"Manifest-Version: 1.0\r\nCreated-By: 1.0 (Sarkari Alert Builder)\r\n\r\n"]
    mf_names = []
    for name, data in entry_items:
        digest = base64.b64encode(_h.sha256(data).digest()).decode()
        sec = ("Name: %s\r\nSHA-256-Digest: %s\r\n\r\n" % (name.decode(), digest)).encode()
        mf_sections.append(sec)
        mf_names.append(name.decode())
    manifest = b"".join(mf_sections)

    sf = b"Signature-Version: 1.0\r\nCreated-By: 1.0 (Sarkari Alert Builder)\r\n"
    sf += ("SHA-256-Digest-Manifest: %s\r\n" % base64.b64encode(_h.sha256(manifest).digest()).decode()).encode()
    sf += b"\r\n"
    for sec in mf_sections[1:]:
        sf += sec + ("SHA-256-Digest: %s\r\n\r\n" % base64.b64encode(_h.sha256(sec).digest()).decode()).encode()
    from cryptography.hazmat.primitives.serialization import pkcs7
    p7 = (pkcs7.PKCS7SignatureBuilder()
          .set_data(sf)
          .add_signer(cert, key, hashes.SHA256())
          .sign(serialization.Encoding.DER, [pkcs7.PKCS7Options.DetachedSignature]))
    return [("META-INF/MANIFEST.MF", manifest),
            ("META-INF/CERT.SF", sf),
            ("META-INF/CERT.RSA", p7)]

def align_repack(base_bytes, dex_bytes, alignment=4, extra_files=None):
    """Rewrite the zip with every STORED entry 4-byte aligned (zipalign).
    Alignment padding lives in each local header extra field (id 0xd935),
    mirrored in the central directory. Deflated entries are re-deflated."""
    zin = zipfile.ZipFile(io.BytesIO(base_bytes))
    items = [(info, zin.read(info.filename)) for info in zin.infolist()]
    if dex_bytes is not None:
        items.append((None, dex_bytes))
    if extra_files:
        for n, d in extra_files:
            items.append(("__extra__:" + n, d))
    out = io.BytesIO()
    cd_parts = []
    for info, data in items:
        if isinstance(info, str):
            name = info.split(":", 1)[1].encode("utf-8")
            method = zipfile.ZIP_DEFLATED
            dt = (2026, 10, 19, 0, 0, 0)
            ext_attr = 0o644 << 16
        elif info is not None:
            name = info.filename.encode("utf-8")
            method = info.compress_type
            dt = info.date_time
            ext_attr = info.external_attr
        else:
            name = b"classes.dex"
            method = zipfile.ZIP_DEFLATED
            dt = (2026, 10, 19, 0, 0, 0)
            ext_attr = 0o644 << 16
        if method == zipfile.ZIP_DEFLATED:
            co = zlib.compressobj(9, zlib.DEFLATED, -15)
            payload = co.compress(data) + co.flush()
        else:
            payload = data
        crc = zlib.crc32(data) & 0xFFFFFFFF
        dostime = ((dt[3] % 24) << 11) | ((dt[4] // 2) << 5) | ((dt[5] // 2) & 0x1F)
        dosdate = ((dt[0] - 1980) << 9) | (dt[1] << 5) | (dt[2] & 0x1F)
        lh_off = out.tell()
        extra = b""
        if method == 0:  # stored entries must start 4-aligned
            pad = (alignment - ((lh_off + 30 + len(name)) % alignment)) % alignment
            if pad < 4:
                pad += alignment
            extra = struct.pack("<HH", 0xD935, pad - 4) + b"\x00" * (pad - 4)
        out.write(struct.pack("<IHHHHHIIIHH", 0x04034B50, 20, 0, method,
                              dostime, dosdate, crc, len(payload), len(data),
                              len(name), len(extra)))
        out.write(name + extra + payload)
        cd_parts.append(struct.pack("<IHHHHHHIIIHHHHHII", 0x02014B50, 20, 20, 0,
                                    method, dostime, dosdate, crc, len(payload),
                                    len(data), len(name), len(extra), 0, 0, 0,
                                    ext_attr, lh_off) + name + extra)
    cd_off = out.tell()
    for c in cd_parts:
        out.write(c)
    cd_size = out.tell() - cd_off
    n = len(cd_parts)
    out.write(struct.pack("<IHHHHIIH", 0x06054B50, 0, 0, n, n, cd_size, cd_off, 0))
    return out.getvalue()

def entries_of(data):
    z = zipfile.ZipFile(io.BytesIO(data))
    out = []
    for info in z.infolist():
        if info.filename.startswith("META-INF/"):
            continue
        out.append((info.filename.encode("utf-8"), z.read(info.filename)))
    return out

def x509_mod():
    from cryptography import x509
    return x509

def check_alignment(data, alignment=4):
    z = zipfile.ZipFile(io.BytesIO(data))
    bad = []
    for info in z.infolist():
        if info.compress_type == 0:
            nlen, elen = struct.unpack_from("<HH", data, info.header_offset + 26)
            off = info.header_offset + 30 + nlen + elen
            if off % alignment:
                bad.append(info.filename)
    return bad

def find_eocd(data):
    idx = data.rfind(b"PK\x05\x06")
    if idx < 0 or idx < len(data) - (22 + 65535):
        raise ValueError("EOCD not found")
    return idx

def eocd_cd_offset(data, idx):
    return struct.unpack_from("<I", data, idx + 16)[0]

def _feed_chunks(h, region):
    for i in range(0, len(region), CHUNK):
        chunk = region[i:i + CHUNK]
        h.update(b"\xa5" + u32(len(chunk)) + chunk)

def content_digest(regions):
    """single chunked SHA-256 over all regions in order (APK Sig v2 spec):
    0xa5+len+chunk for every 1MB chunk of every region, then 0x5a+u64(total)."""
    h = hashlib.sha256()
    total = 0
    for r in regions:
        _feed_chunks(h, r)
        total += len(r)
    h.update(b"\x5a" + u64(total))
    return h.digest()

def repack(base_bytes, dex_bytes, **kw):
    return align_repack(base_bytes, dex_bytes, **kw)

def repack_unaligned(base_bytes, dex_bytes):
    zin = zipfile.ZipFile(io.BytesIO(base_bytes))
    out = io.BytesIO()
    zout = zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED)
    for info in zin.infolist():
        zi = zipfile.ZipInfo(info.filename, date_time=info.date_time)
        zi.compress_type = info.compress_type
        zi.external_attr = info.external_attr
        zi.internal_attr = info.internal_attr
        zi.create_system = info.create_system
        zout.writestr(zi, zin.read(info.filename))
    if dex_bytes is not None:
        zi = zipfile.ZipInfo("classes.dex", date_time=(2026, 10, 19, 0, 0, 0))
        zi.compress_type = zipfile.ZIP_DEFLATED
        zi.external_attr = 0o644 << 16
        zout.writestr(zi, dex_bytes)
    zout.close()
    return out.getvalue()

def gen_key(dirpath):
    from cryptography import x509
    from cryptography.x509.oid import NameOID
    from cryptography.hazmat.primitives import hashes, serialization
    from cryptography.hazmat.primitives.asymmetric import rsa
    import datetime
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    name = x509.Name([
        x509.NameAttribute(NameOID.COMMON_NAME, u"Sarkari Alert"),
        x509.NameAttribute(NameOID.ORGANIZATION_NAME, u"Sarkari Alert"),
        x509.NameAttribute(NameOID.COUNTRY_NAME, u"IN"),
    ])
    now = datetime.datetime.now(datetime.timezone.utc)
    cert = (x509.CertificateBuilder()
            .subject_name(name).issuer_name(name)
            .public_key(key.public_key())
            .serial_number(x509.random_serial_number())
            .not_valid_before(now - datetime.timedelta(days=1))
            .not_valid_after(now + datetime.timedelta(days=365 * 30))
            .sign(key, hashes.SHA256()))
    os.makedirs(dirpath, exist_ok=True)
    with open(os.path.join(dirpath, "release.key.pem"), "wb") as f:
        f.write(key.private_bytes(serialization.Encoding.PEM,
                                  serialization.PrivateFormat.PKCS8,
                                  serialization.NoEncryption()))
    with open(os.path.join(dirpath, "release.cert.der"), "wb") as f:
        f.write(cert.public_bytes(serialization.Encoding.DER))
    with open(os.path.join(dirpath, "release.cert.pem"), "wb") as f:
        f.write(cert.public_bytes(serialization.Encoding.PEM))
    print("key + self-signed cert written to", dirpath)

def sign(apk_bytes, key_path, cert_path):
    from cryptography.hazmat.primitives import hashes, serialization
    from cryptography.hazmat.primitives.asymmetric import padding
    with open(key_path, "rb") as f:
        key = serialization.load_pem_private_key(f.read(), password=None)
    with open(cert_path, "rb") as f:
        cert_der = f.read()
    eocd_idx = find_eocd(apk_bytes)
    cd_off = eocd_cd_offset(apk_bytes, eocd_idx)
    entries = apk_bytes[:cd_off]
    cd = apk_bytes[cd_off:eocd_idx]
    eocd = apk_bytes[eocd_idx:]

    digest = content_digest([entries, cd, eocd])
    digests_seq = lp(u32(ALG) + lp(digest))
    certs_seq = lp(cert_der)
    additional = b""
    signed_data = lp(digests_seq) + lp(certs_seq) + lp(additional)
    signature = key.sign(signed_data, padding.PKCS1v15(), hashes.SHA256())
    signatures_seq = lp(u32(ALG) + lp(signature))
    spki = key.public_key().public_bytes(serialization.Encoding.DER,
                                         serialization.PublicFormat.SubjectPublicKeyInfo)
    signer = lp(signed_data) + lp(signatures_seq) + lp(spki)
    v2_value = lp(lp(signer))
    # official spec: each ID-value pair is u64-length-prefixed
    pair = u64(4 + len(v2_value)) + u32(V2_ID) + v2_value
    # per spec: size field = size of block excluding the first length field
    # (i.e. pairs + second u64 + magic). Total file footprint = 8 + size.
    block_size = len(pair) + 8 + 16
    block = u64(block_size) + pair + u64(block_size) + MAGIC
    patched_eocd = bytearray(eocd)
    struct.pack_into("<I", patched_eocd, 16, cd_off + block_size + 8)
    return entries + block + cd + bytes(patched_eocd)

def verify(path):
    from cryptography.hazmat.primitives import hashes, serialization
    from cryptography.hazmat.primitives.asymmetric import padding
    data = open(path, "rb").read()
    eocd_idx = find_eocd(data)
    cd_off_final = struct.unpack_from("<I", data, eocd_idx + 16)[0]
    pairs_len = struct.unpack_from("<Q", data, cd_off_final - 24)[0]
    assert data[cd_off_final - 16:cd_off_final] == MAGIC, "magic mismatch"
    block_start = cd_off_final - 8 - pairs_len
    assert struct.unpack_from("<Q", data, block_start)[0] == pairs_len, "block len mismatch"
    off = block_start + 8
    pair_size = struct.unpack_from("<Q", data, off)[0]
    pair_id = struct.unpack_from("<I", data, off + 8)[0]
    assert pair_id == V2_ID, "not an APK Sig v2 block (id=0x%x)" % pair_id
    val = data[off + 12: off + 8 + pair_size]
    signers_len = struct.unpack_from("<I", val, 0)[0]
    signers_blob = val[4:4 + signers_len]
    inner_len = struct.unpack_from("<I", signers_blob, 0)[0]
    signer = signers_blob[4:4 + inner_len]
    sd_len = struct.unpack_from("<I", signer, 0)[0]
    signed_data = signer[4:4 + sd_len]
    rest = signer[4 + sd_len:]
    sigs_len = struct.unpack_from("<I", rest, 0)[0]
    sigs_blob = rest[4:4 + sigs_len]
    entry_len = struct.unpack_from("<I", sigs_blob, 0)[0]
    spki_off = 4 + sigs_len
    spki_len = struct.unpack_from("<I", rest, spki_off)[0]
    spki = rest[spki_off + 4: spki_off + 4 + spki_len]

    d_len = struct.unpack_from("<I", signed_data, 0)[0]
    digests_blob = signed_data[4:4 + d_len]
    cd_off = block_start
    entries = data[:cd_off]
    cd = data[cd_off_final: eocd_idx]
    eocd = bytearray(data[eocd_idx:])
    struct.pack_into("<I", eocd, 16, cd_off)
    expect = content_digest([entries, cd, bytes(eocd)])
    rec_len = struct.unpack_from("<I", digests_blob, 0)[0]
    rec = digests_blob[4:4 + rec_len]
    assert rec[:8] == u32(ALG) + u32(32), "digest record format"
    got = rec[8:8 + 32]
    assert expect == got, "digest mismatch! APK content altered"
    pub = serialization.load_der_public_key(spki)
    sig_alg = struct.unpack_from("<I", sigs_blob, 4)[0]
    sig_len = struct.unpack_from("<I", sigs_blob, 8)[0]
    sig = sigs_blob[12: 12 + sig_len]
    assert entry_len == 4 + 4 + sig_len, "signature entry format"
    pub.verify(sig, signed_data, padding.PKCS1v15(), hashes.SHA256())
    bad = check_alignment(data)
    assert not bad, "misaligned stored entries after signing: %s" % bad
    print("OK: APK Signature Scheme v2 verified (id=0x%x, alg=0x%x, %d bytes sig)"
          % (pair_id, sig_alg, len(sig)))
    print("zipalign: all stored entries 4-byte aligned")
    print("block: [%d, %d), cd@%d, eocd@%d" % (block_start, cd_off_final - 0, cd_off_final, eocd_idx))

def main():
    cmd = sys.argv[1]
    if cmd == "genkey":
        gen_key(sys.argv[2])
    elif cmd == "pack":
        base = open(sys.argv[2], "rb").read()
        dex = open(sys.argv[3], "rb").read() if sys.argv[3] != "-" else None
        from cryptography.hazmat.primitives import serialization as _ser
        key = _ser.load_pem_private_key(open(sys.argv[4], "rb").read(), password=None)
        cert = x509_mod().load_der_x509_certificate(open(sys.argv[5], "rb").read())
        packed = repack(base, dex)
        v1 = build_v1_files(entries_of(packed), key, cert)
        packed = repack(base, dex, extra_files=v1)
        bad = check_alignment(packed)
        assert not bad, "misaligned stored entries: %s" % bad
        signed = sign(packed, sys.argv[4], sys.argv[5])
        open(sys.argv[6], "wb").write(signed)
        print("packed+aligned+v1+v2 signed ->", sys.argv[6], "(%d bytes)" % len(signed))
    elif cmd == "verify":
        verify(sys.argv[2])
    else:
        print(__doc__); sys.exit(2)

if __name__ == "__main__":
    main()
