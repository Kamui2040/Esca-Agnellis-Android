#!/usr/bin/env python3
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

from apk_hardening import ROOT, inspect_bytes, ok, run, verify_common


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Verify a signed Esca developer release APK.")
    parser.add_argument("--apk", default="app/build/outputs/apk/release/Esca-Agnellis-v0.16.0-vc40-release.apk")
    parser.add_argument("--mapping", default="app/build/outputs/mapping/release/mapping.txt")
    parser.add_argument("--package", default="com.k2040.escaagnellis")
    parser.add_argument("--version-name", default="0.16.0")
    parser.add_argument("--version-code", type=int, default=40)
    parser.add_argument("--expected-cert-sha256", required=True)
    return parser.parse_args()


def absolute(raw: str) -> Path:
    path = Path(raw)
    return path if path.is_absolute() else ROOT / path


def main() -> int:
    args = parse_args()
    if not re.fullmatch(r"[0-9A-Fa-f]{64}", args.expected_cert_sha256):
        raise RuntimeError("Expected certificate SHA-256 must contain exactly 64 hexadecimal characters.")
    apk = absolute(args.apk)
    mapping = absolute(args.mapping)
    _, apksigner, _, package, version_name, version_code = verify_common(
        apk,
        mapping,
        args.package,
        args.version_name,
        args.version_code,
        forbid_signatures=False,
    )
    signature = run(apksigner, "verify", "--verbose", "--print-certs", str(apk)).stdout
    match = re.search(r"SHA-256 digest:\s*([0-9A-Fa-f]{64})", signature, re.I)
    if not match:
        raise RuntimeError("Release certificate SHA-256 fingerprint was not found.")
    actual = match.group(1).upper()
    if actual != args.expected_cert_sha256.upper():
        raise RuntimeError("Release certificate SHA-256 fingerprint does not match.")
    ok("release certificate fingerprint matches")
    inspect_bytes(apk.read_bytes(), "raw_archive")
    ok("APK contains no strong private markers")
    print("RELEASE_HARDENING_VERDICT: PASSED")
    print(f"PACKAGE: {package}")
    print(f"VERSION_NAME: {version_name}")
    print(f"VERSION_CODE: {version_code}")
    print(f"CERT_SHA256: {actual}")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as exc:
        print(f"FAIL: {exc}")
        sys.exit(1)
