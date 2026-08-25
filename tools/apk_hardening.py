#!/usr/bin/env python3
from __future__ import annotations

import os
import re
import subprocess
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REQUIRED_APK_ENTRIES = {"AndroidManifest.xml", "classes.dex", "resources.arsc"}
FORBIDDEN_TRACKED = [
    re.compile(r"(^|/)build/"),
    re.compile(r"\.(apk|aab|jks|keystore|p12|pfx|pem|key)$", re.I),
    re.compile(r"(^|/)(local|keystore)\.properties$", re.I),
    re.compile(r"(^|/)(mapping|seeds|usage|configuration|resources)\.txt$", re.I),
    re.compile(r"(^|/).*(backup|sicherung).*\.json$", re.I),
]
PRIVATE_MARKERS = [
    ("windows_private_path", re.compile(rb"[A-Z]:\\(?:Users|Projects)\\[^\x00\r\n]{1,260}", re.I)),
    ("posix_private_path", re.compile(rb"/(?:home|Users|Projects)/[^\x00\r\n]{1,260}", re.I)),
    ("esca_signing_environment", re.compile(rb"\bESCA_SIGNING_PROPERTIES\b", re.I)),
    ("signing_secret_assignment", re.compile(rb"\b(?:storePassword|keyPassword)\s*[:=]\s*[\"']?[^\s\"',;]{1,128}", re.I)),
    ("signing_file_assignment", re.compile(rb"\bstoreFile\s*[:=]\s*[\"']?[^\r\n]{1,260}\.(?:jks|keystore|p12|pfx)\b", re.I)),
    ("keystore_absolute_path", re.compile(rb"(?:[A-Z]:\\|/)[^\x00\r\n]{0,260}\.(?:jks|keystore|p12|pfx)\b", re.I)),
]


def ok(message: str) -> None:
    print(f"[OK] {message}")


def run(tool: Path | str, *args: str, check: bool = True) -> subprocess.CompletedProcess[str]:
    result = subprocess.run([str(tool), *args], cwd=ROOT, text=True, capture_output=True)
    if check and result.returncode != 0:
        raise RuntimeError(f"{Path(str(tool)).name} failed with exit code {result.returncode}:\n{result.stdout}{result.stderr}")
    return result


def version_key(name: str) -> tuple[int, ...]:
    try:
        return tuple(int(part) for part in name.split("."))
    except ValueError:
        return (0,)


def build_tools_dir() -> Path:
    for raw in (os.environ.get("ANDROID_SDK_ROOT"), os.environ.get("ANDROID_HOME")):
        if not raw:
            continue
        root = Path(raw).expanduser().resolve() / "build-tools"
        if not root.is_dir():
            continue
        versions = sorted((p for p in root.iterdir() if p.is_dir()), key=lambda p: version_key(p.name), reverse=True)
        if versions:
            return versions[0]
    raise RuntimeError("Android SDK build-tools were not found. Set ANDROID_SDK_ROOT or ANDROID_HOME.")


def tool_path(directory: Path, *names: str) -> Path:
    for name in names:
        candidate = directory / name
        if candidate.is_file():
            return candidate
    raise RuntimeError(f"Required Android build tool was not found under {directory}: {', '.join(names)}")


def badging_value(line: str, name: str) -> str:
    match = re.search(rf"(?:^|\s){re.escape(name)}='([^']*)'", line)
    if not match or not match.group(1).strip():
        raise RuntimeError(f"Package metadata field '{name}' was missing or empty.")
    return match.group(1)


def inspect_zip(apk: Path, *, forbid_signatures: bool) -> None:
    patterns = [
        re.compile(r"(^|/)(mapping|seeds|usage|configuration|resources)\.txt$", re.I),
        re.compile(r"(^|/)(local|keystore)\.properties$", re.I),
        re.compile(r"\.(jks|keystore|p12|pfx|pem|key|apk|aab)$", re.I),
        re.compile(r"(^|/).*(backup|sicherung).*\.json$", re.I),
    ]
    if forbid_signatures:
        patterns += [re.compile(r"^META-INF/MANIFEST\.MF$", re.I), re.compile(r"^META-INF/.*\.(RSA|DSA|EC|SF)$", re.I)]
    with zipfile.ZipFile(apk, "r") as archive:
        names = set(archive.namelist())
        missing = REQUIRED_APK_ENTRIES - names
        if missing:
            raise RuntimeError(f"APK is missing required entry: {sorted(missing)[0]}")
        total = 0
        for info in archive.infolist():
            if any(pattern.search(info.filename) for pattern in patterns):
                raise RuntimeError(f"APK contains forbidden generated, private, or signing artifact: {info.filename}")
            if info.is_dir() or info.file_size == 0:
                continue
            if info.file_size > 128 * 1024 * 1024:
                raise RuntimeError(f"APK entry exceeds private-marker inspection bound: {info.filename}; bytes={info.file_size}")
            total += info.file_size
            if total > 512 * 1024 * 1024:
                raise RuntimeError(f"APK exceeds cumulative private-marker inspection bound: bytes={total}")
            data = archive.read(info)
            inspect_bytes(data, f"entry:{info.filename}")


def inspect_bytes(data: bytes, location: str) -> None:
    for label, pattern in PRIVATE_MARKERS:
        if pattern.search(data):
            raise RuntimeError(f"APK contains strong private-build marker '{label}' at {location}.")
    if len(data) >= 2:
        widened = data[::2]
        for label, pattern in PRIVATE_MARKERS:
            if pattern.search(widened):
                raise RuntimeError(f"APK contains strong private-build marker '{label}' at {location} (utf16le view).")


def mapping_has_renamed_app_class(mapping: Path) -> None:
    pattern = re.compile(r"^(com\.k2040\.escaagnellis\.[^ ]+) -> ([^:]+):$")
    with mapping.open("r", encoding="utf-8", errors="replace") as handle:
        for line in handle:
            match = pattern.match(line.rstrip("\n"))
            if match and match.group(1) != match.group(2):
                return
    raise RuntimeError("R8 mapping contains no renamed application class.")


def relative(path: Path) -> str:
    return path.resolve().relative_to(ROOT).as_posix()


def assert_ignored_untracked(path: Path) -> None:
    rel = relative(path)
    ignored = run("git", "check-ignore", "-q", "--", rel, check=False)
    if ignored.returncode != 0:
        raise RuntimeError(f"Expected private build artifact is not ignored by Git: {rel}")
    tracked = run("git", "ls-files", "--", rel).stdout.strip()
    if tracked:
        raise RuntimeError(f"Private build artifact is tracked by Git: {rel}")


def assert_no_tracked_private_artifacts() -> None:
    for rel in run("git", "ls-files").stdout.splitlines():
        if any(pattern.search(rel) for pattern in FORBIDDEN_TRACKED):
            raise RuntimeError(f"Tracked private or release artifact is forbidden: {rel}")


def verify_common(apk: Path, mapping: Path, expected_package: str, expected_version_name: str, expected_version_code: int, *, forbid_signatures: bool) -> tuple[Path, Path, Path, str, str, str]:
    if not apk.is_file():
        raise RuntimeError(f"APK was not found: {apk}")
    if not mapping.is_file() or mapping.stat().st_size <= 0:
        raise RuntimeError(f"Mapping file is missing or empty: {mapping}")
    tools = build_tools_dir()
    aapt = tool_path(tools, "aapt", "aapt.exe")
    apksigner = tool_path(tools, "apksigner", "apksigner.bat", "apksigner.cmd", "apksigner.exe")
    zipalign = tool_path(tools, "zipalign", "zipalign.exe")
    ok("Android build-tools located")
    inspect_zip(apk, forbid_signatures=forbid_signatures)
    run(zipalign, "-c", "-v", "4", str(apk))
    ok("APK archive is readable, complete, and zip-aligned")
    badging = run(aapt, "dump", "badging", str(apk)).stdout
    package_line = next((line for line in badging.splitlines() if line.startswith("package:")), None)
    if package_line is None:
        raise RuntimeError("Package metadata was not found in APK badging.")
    package = badging_value(package_line, "name")
    version_code = badging_value(package_line, "versionCode")
    version_name = badging_value(package_line, "versionName")
    if package != expected_package or version_code != str(expected_version_code) or version_name != expected_version_name:
        raise RuntimeError("Package/version metadata does not match expected values.")
    if "application-debuggable" in badging:
        raise RuntimeError("Release APK is debuggable.")
    permissions = run(aapt, "dump", "permissions", str(apk)).stdout
    if "android.permission.INTERNET" in permissions:
        raise RuntimeError("Release APK requests android.permission.INTERNET.")
    ok("package, version, non-debuggable state, and permissions match")
    mapping_has_renamed_app_class(mapping)
    ok("R8 mapping contains renamed application classes")
    return aapt, apksigner, zipalign, package, version_name, version_code
