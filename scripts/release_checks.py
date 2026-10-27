#!/usr/bin/env python3
"""Fail closed on release/tag mismatches or incomplete native APKs (stdlib only)."""

import argparse
import json
import os
from pathlib import Path
import re
import zipfile

ROOT = Path(__file__).resolve().parent.parent
ABIS = {"arm64-v8a", "x86_64"}
NATIVE_LIBRARIES = {"libgojni.so", "libkeroid_term.so"}


def read_version(path):
    values = {}
    for line in path.read_text().splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        key, sep, value = line.partition("=")
        if not sep or key not in {"versionName", "versionCode"} or key in values:
            raise ValueError("Invalid or duplicate version property")
        values[key] = value
    name = values.get("versionName", "")
    if not re.fullmatch(r"(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)", name):
        raise ValueError("versionName must be X.Y.Z without leading zeroes")
    code = values.get("versionCode", "")
    if not re.fullmatch(r"[1-9][0-9]*", code) or not 1 <= int(code) <= 2_100_000_000:
        raise ValueError("versionCode must be in 1..2100000000")
    return name, int(code)


def check_tag(tag, version):
    if tag and tag != f"v{version}":
        raise ValueError(f"Release tag must be v{version}, got {tag!r}")


def check_apk(directory, version, code):
    metadata = json.loads((directory / "output-metadata.json").read_text())
    if metadata.get("applicationId") != "com.sulthonuladib.keroid":
        raise ValueError("Unexpected application ID")
    elements = metadata.get("elements", [])
    if len(elements) != 1 or elements[0].get("filters"):
        raise ValueError("Expected exactly one universal APK")
    element = elements[0]
    if (element.get("versionName"), element.get("versionCode")) != (version, code):
        raise ValueError("APK version does not match version.properties")
    filename = element["outputFile"]
    if Path(filename).name != filename or not filename.endswith(".apk"):
        raise ValueError("Invalid APK filename")
    apk = directory / filename
    with zipfile.ZipFile(apk) as archive:
        names = set(archive.namelist())
    abis = {name.split("/")[1] for name in names if name.startswith("lib/")}
    if abis != ABIS:
        raise ValueError(f"APK ABI mismatch: expected {sorted(ABIS)}, got {sorted(abis)}")
    for abi in ABIS:
        for library in NATIVE_LIBRARIES:
            if f"lib/{abi}/{library}" not in names:
                raise ValueError(f"Missing {library} for {abi}")
    return apk


def check_certificate(report, expected):
    expected = re.sub(r"[:\s]", "", expected).lower()
    if not re.fullmatch(r"[0-9a-f]{64}", expected):
        raise ValueError("Expected signing certificate must be a SHA-256 fingerprint")
    counts = re.findall(r"^Number of signers: ([0-9]+)$", report, re.MULTILINE)
    # Build-tools 37 labels certificates by scheme (V3.0 Signer), whereas
    # earlier tools use Signer #1. Accept both, but never public-key hashes.
    certificates = re.findall(
        r"^(?:Signer #[1-9][0-9]*|V[0-9]+(?:\.[0-9]+)? Signer): "
        r"certificate SHA-256 digest: ([0-9a-fA-F]{64})$",
        report, re.MULTILINE,
    )
    if counts != ["1"] or not certificates or {c.lower() for c in certificates} != {expected}:
        raise ValueError("Signing certificate does not match the configured release identity")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tag", default="")
    parser.add_argument("--apk-dir", type=Path)
    parser.add_argument("--certificate-report", type=Path)
    args = parser.parse_args()
    try:
        version, code = read_version(ROOT / "version.properties")
        check_tag(args.tag, version)
        if args.apk_dir:
            print(f"Checked APK: {check_apk(args.apk_dir, version, code)}")
        if args.certificate_report:
            check_certificate(args.certificate_report.read_text(),
                              os.environ.get("KEROID_SIGNING_CERT_SHA256", ""))
            print("Verified release signing certificate")
        print(f"Keroid {version} (versionCode {code})")
    except (ValueError, KeyError, OSError, zipfile.BadZipFile) as error:
        parser.exit(1, f"Release validation failed: {error}\n")


if __name__ == "__main__":
    main()
