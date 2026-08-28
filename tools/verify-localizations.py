#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app" / "src" / "main" / "res"
EXPECTED_COUNT = 221
LOCALES = {
    "de": "values",
    "en": "values-en",
    "es": "values-es",
    "fr": "values-fr",
    "pt-PT": "values-pt-rPT",
    "tr": "values-tr",
    "ar": "values-ar",
    "pl": "values-pl",
    "ru": "values-ru",
    "uk": "values-uk",
    "ro": "values-ro",
}
PLACEHOLDER_RE = re.compile(r"%(?:\d+\$)?[-#+ 0,(<]*\d*(?:\.\d+)?[a-zA-Z]")
MOJIBAKE_RE = re.compile(r"\u00C3.|\u00C2.|\u00E2\u20AC[\u2122\u0153\u017E\u201C\u201D]|\u00F0\u0178|\u00EF\u00BF\u00BD|\uFFFD")
failures: list[str] = []

def fail(message: str) -> None:
    failures.append(message)
    print(f"FAIL: {message}")

def placeholders(text: str) -> list[str]:
    return sorted(PLACEHOLDER_RE.findall(text.replace("%%", "")))

def parse_entries(root: ET.Element, locale: str) -> dict[str, dict]:
    entries: dict[str, dict] = {}
    for node in root:
        if node.tag not in {"string", "string-array", "plurals"}:
            continue
        name = node.attrib.get("name", "").strip()
        if not name:
            fail(f"{locale} resource entry without a name.")
            continue
        if node.attrib.get("translatable") == "false":
            continue
        if name in entries:
            fail(f"{locale} duplicate resource key: {name}")
            continue
        if node.tag == "string":
            text = "".join(node.itertext())
            if not text.strip():
                fail(f"{locale} empty string value for key: {name}")
            entries[name] = {"kind": "string", "placeholders": placeholders(text)}
            continue
        items = []
        seen: set[str] = set()
        for index, item in enumerate(node.findall("item")):
            text = "".join(item.itertext())
            if not text.strip():
                fail(f"{locale} empty {node.tag} item for key: {name} index: {index}")
            quantity = item.attrib.get("quantity") if node.tag == "plurals" else None
            if node.tag == "plurals":
                if not quantity:
                    fail(f"{locale} plurals item missing quantity for key: {name} index: {index}")
                elif quantity in seen:
                    fail(f"{locale} duplicate plurals quantity for key: {name} quantity: {quantity}")
                else:
                    seen.add(quantity)
            items.append({"quantity": quantity, "placeholders": placeholders(text)})
        if not items:
            fail(f"{locale} empty {node.tag} resource: {name}")
        entries[name] = {"kind": node.tag, "items": items}
    return entries

def compare_entry(locale: str, key: str, expected: dict, actual: dict) -> None:
    if expected["kind"] != actual["kind"]:
        fail(f"{locale} resource kind differs for key: {key}")
        return
    if expected["kind"] == "string":
        if expected["placeholders"] != actual["placeholders"]:
            fail(f"Placeholder mismatch: {locale} string key: {key}")
        return
    if expected["kind"] == "string-array":
        if len(expected["items"]) != len(actual["items"]):
            fail(f"{locale} string-array item count differs for key: {key}")
            return
        for index, (left, right) in enumerate(zip(expected["items"], actual["items"])):
            if left["placeholders"] != right["placeholders"]:
                fail(f"Placeholder mismatch: {locale} string-array key: {key} index: {index}")
        return
    left = {item["quantity"]: item["placeholders"] for item in expected["items"]}
    right = {item["quantity"]: item["placeholders"] for item in actual["items"]}
    if set(left) != set(right):
        fail(f"{locale} plurals quantities differ for key: {key}")
        return
    for quantity in sorted(left):
        if left[quantity] != right[quantity]:
            fail(f"Placeholder mismatch: {locale} plurals key: {key} quantity: {quantity}")

def main() -> int:
    if (RES / "values-pt").exists():
        fail("Generic values-pt is forbidden; use values-pt-rPT only.")
    parsed: dict[str, dict[str, dict]] = {}
    for locale, directory in LOCALES.items():
        path = RES / directory / "strings.xml"
        if not path.parent.is_dir():
            fail(f"Missing locale directory: {directory}")
            continue
        if not path.is_file():
            fail(f"Missing strings file: {path}")
            continue
        raw = path.read_bytes()
        if raw.startswith(b"\xef\xbb\xbf"):
            fail(f"UTF-8 BOM is not allowed: {path}")
        try:
            text = raw.decode("utf-8", errors="strict")
        except UnicodeDecodeError:
            fail(f"Invalid UTF-8: {path}")
            continue
        if MOJIBAKE_RE.search(text):
            fail(f"Obvious mojibake marker found: {path}")
        try:
            parsed[locale] = parse_entries(ET.fromstring(text), locale)
        except ET.ParseError as exc:
            fail(f"Invalid XML in {path}: {exc}")
    default = parsed.get("de")
    if default is None:
        fail("German default resources could not be loaded.")
    else:
        if len(default) != EXPECTED_COUNT:
            fail(f"de translatable key count must be {EXPECTED_COUNT}, found {len(default)}")
        expected_keys = set(default)
        for locale in LOCALES:
            entries = parsed.get(locale)
            if entries is None:
                continue
            if len(entries) != EXPECTED_COUNT:
                fail(f"{locale} translatable key count must be {EXPECTED_COUNT}, found {len(entries)}")
            for key in sorted(expected_keys - set(entries)):
                fail(f"{locale} missing key: {key}")
            for key in sorted(set(entries) - expected_keys):
                fail(f"{locale} has unexpected translatable key: {key}")
            for key in sorted(expected_keys & set(entries)):
                compare_entry(locale, key, default[key], entries[key])
            print(f"{locale}: {len(entries)} translatable keys")
    if failures:
        print(f"Localization verification failed with {len(failures)} issue(s).")
        return 1
    print("Localization verification passed.")
    return 0

if __name__ == "__main__":
    sys.exit(main())
