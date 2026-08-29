#!/usr/bin/env python3
from __future__ import annotations

import argparse
import sys
from pathlib import Path

from apk_hardening import ROOT, assert_ignored_untracked, assert_no_tracked_private_artifacts, ok, run, verify_common


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Verify an unsigned F-Droid-style Esca release build.")
    parser.add_argument("--apk", default="app/build/outputs/apk/release/Esca-Agnellis-v0.17.0-vc41-release.apk")
    parser.add_argument("--mapping", default="app/build/outputs/mapping/release/mapping.txt")
    parser.add_argument("--package", default="com.k2040.escaagnellis")
    parser.add_argument("--version-name", default="0.17.0")
    parser.add_argument("--version-code", type=int, default=41)
    return parser.parse_args()


def absolute(raw: str) -> Path:
    path = Path(raw)
    return path if path.is_absolute() else ROOT / path


def main() -> int:
    args = parse_args()
    apk = absolute(args.apk)
    mapping = absolute(args.mapping)
    _, apksigner, _, _, _, _ = verify_common(
        apk,
        mapping,
        args.package,
        args.version_name,
        args.version_code,
        forbid_signatures=True,
    )
    signature = run(apksigner, "verify", "--verbose", "--print-certs", str(apk), check=False)
    text = signature.stdout + signature.stderr
    if signature.returncode == 0:
        raise RuntimeError("F-Droid APK unexpectedly verifies as signed.")
    if "DOES NOT VERIFY" not in text:
        raise RuntimeError(f"apksigner did not report the expected unsigned state: {text.strip()}")
    ok("F-Droid APK is unsigned")
    assert_ignored_untracked(apk)
    assert_ignored_untracked(mapping)
    ok("APK and mapping outputs are ignored and untracked")
    mapping_dir = mapping.parent
    resources = mapping_dir / "resources.txt"
    if not resources.is_file() or resources.stat().st_size <= 0:
        raise RuntimeError(f"Resource-shrinking report is missing or empty: {resources}")
    assert_ignored_untracked(resources)
    for name in ("seeds.txt", "usage.txt", "configuration.txt", "resources.txt"):
        path = mapping_dir / name
        if path.is_file():
            assert_ignored_untracked(path)
    debug_mapping = ROOT / "app" / "build" / "outputs" / "mapping" / "debug"
    if debug_mapping.exists():
        raise RuntimeError("Debug mapping output exists; debug builds should remain unminified.")
    assert_no_tracked_private_artifacts()
    ok("repository tracks no private or release build artifacts")
    print("F-Droid hardening verification passed.")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as exc:
        print(f"FAIL: {exc}")
        sys.exit(1)
