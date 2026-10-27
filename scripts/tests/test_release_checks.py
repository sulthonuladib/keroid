import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

spec = importlib.util.spec_from_file_location(
    "release_checks", Path(__file__).resolve().parents[1] / "release_checks.py"
)
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


class ReleaseChecksTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def version_file(self, text):
        path = self.root / "version.properties"
        path.write_text(text)
        return path

    def test_version_and_tag(self):
        self.assertEqual(
            release.read_version(self.version_file("# release\nversionName=1.0.0\nversionCode=1\n")),
            ("1.0.0", 1),
        )
        release.check_tag("v1.0.0", "1.0.0")
        release.check_tag("", "1.0.0")
        for tag in ("v2.0.0", "1.0.0", "v1.0.0-rc1", "main"):
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                release.check_tag(tag, "1.0.0")

    def test_certificate_output_formats(self):
        fingerprint = "a" * 64
        for label in ("Signer #1", "V3.0 Signer", "V3.2 Signer"):
            report = (f"Verifies\nNumber of signers: 1\n"
                      f"{label}: certificate SHA-256 digest: {fingerprint}\n")
            with self.subTest(label=label):
                release.check_certificate(report, fingerprint)
                release.check_certificate(report, ":".join(["AA"] * 32))

    def test_certificate_mismatch_and_missing_signer(self):
        fingerprint = "a" * 64
        valid = f"Number of signers: 1\nV3.0 Signer: certificate SHA-256 digest: {fingerprint}\n"
        for report, expected in (
            (valid, "b" * 64),
            (valid, ""),
            ("", fingerprint),
            (valid.replace("signers: 1", "signers: 2"), fingerprint),
            (valid.replace("certificate SHA", "public key SHA"), fingerprint),
            (valid + f"V2 Signer: certificate SHA-256 digest: {'b' * 64}\n", fingerprint),
        ):
            with self.subTest(report=report, expected=expected), self.assertRaises(ValueError):
                release.check_certificate(report, expected)

    def test_invalid_versions(self):
        for text in (
            "versionName=1.0\nversionCode=1",
            "versionName=01.0.0\nversionCode=1",
            "versionName=1.0.0\nversionCode=0",
            "versionName=1.0.0\nversionCode=2100000001",
            "versionName=1.0.0\nversionCode=one",
            "versionName=1.0.0",
            "versionName=1.0.0\nversionCode=1\nversionCode=2",
            "versionName=1.0.0\nversionCode=1\nunexpected=true",
        ):
            with self.subTest(text=text), self.assertRaises(ValueError):
                release.read_version(self.version_file(text))

    def apk_fixture(self, *, missing=None, extra=None, **element_overrides):
        element = {"filters": [], "versionName": "1.0.0", "versionCode": 1,
                   "outputFile": "app-release-unsigned.apk", **element_overrides}
        metadata = {"applicationId": "com.sulthonuladib.keroid", "elements": [element]}
        (self.root / "output-metadata.json").write_text(json.dumps(metadata))
        with zipfile.ZipFile(self.root / "app-release-unsigned.apk", "w") as archive:
            for abi in release.ABIS:
                for library in release.NATIVE_LIBRARIES:
                    name = f"lib/{abi}/{library}"
                    if name != missing:
                        archive.writestr(name, b"fixture")
            if extra:
                archive.writestr(extra, b"fixture")

    def test_complete_universal_apk(self):
        self.apk_fixture()
        self.assertEqual(release.check_apk(self.root, "1.0.0", 1).name,
                         "app-release-unsigned.apk")

    def test_missing_library(self):
        self.apk_fixture(missing="lib/arm64-v8a/libkeroid_term.so")
        with self.assertRaisesRegex(ValueError, "Missing libkeroid_term"):
            release.check_apk(self.root, "1.0.0", 1)

    def test_unsupported_abi(self):
        self.apk_fixture(extra="lib/armeabi-v7a/transitive.so")
        with self.assertRaisesRegex(ValueError, "ABI mismatch"):
            release.check_apk(self.root, "1.0.0", 1)

    def test_wrong_metadata(self):
        for overrides in ({"versionName": "2.0.0"}, {"versionCode": 2},
                          {"filters": [{"filterType": "ABI", "value": "arm64-v8a"}]},
                          {"outputFile": "../outside.apk"}):
            self.apk_fixture(**overrides)
            with self.subTest(overrides=overrides), self.assertRaises(ValueError):
                release.check_apk(self.root, "1.0.0", 1)


if __name__ == "__main__":
    unittest.main()
