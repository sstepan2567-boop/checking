import csv
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("recover_images", ROOT / "tools/recover_images.py")
recovery = importlib.util.module_from_spec(spec)
spec.loader.exec_module(recovery)


class RejectBadInput(unittest.TestCase):
    def test_truncated_archive(self):
        with tempfile.TemporaryDirectory() as directory:
            archive = Path(directory) / "input.zip"
            archive.write_bytes(b"PK\x03\x04" + b"\0" * 32)
            with self.assertRaises(zipfile.BadZipFile):
                recovery.recover(archive, Path(directory) / "out")

    def test_other_build_rejected_before_parsing(self):
        with tempfile.TemporaryDirectory() as directory:
            archive = Path(directory) / "input.zip"
            with zipfile.ZipFile(archive, "w") as bundle:
                bundle.writestr("systemdlc.offline.dll", b"not a PE")
            with self.assertRaisesRegex(ValueError, "unsupported build"):
                recovery.recover(archive, Path(directory) / "out")
            self.assertFalse((Path(directory) / "out/client/image.bin").exists())


class RecoveredImageChecks(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.folder = ROOT / "analysis/images"
        if not (cls.folder / "manifest.json").exists():
            raise unittest.SkipTest("run recover_images.py against the supplied archive first")

    def test_hashes_and_address_ranges(self):
        manifest = json.loads((self.folder / "manifest.json").read_text())
        for module, expected_count, expected_imports in [("client", 23448, 644), ("loader", 2763, 350)]:
            with self.subTest(module=module):
                folder = self.folder / module
                image = (folder / "image.bin").read_bytes()
                self.assertEqual(hashlib.sha256(image).hexdigest(), manifest[module]["image_sha256"])
                self.assertEqual(len(image), manifest[module]["size"])
                with (folder / "functions.tsv").open() as stream:
                    functions = list(csv.DictReader(stream, delimiter="\t"))
                self.assertEqual(len(functions), expected_count)
                self.assertEqual(len({row["begin"] for row in functions}), expected_count)
                self.assertIn(manifest[module]["entry_rva"], {r["begin"] for r in functions})
                for row in functions:
                    begin, end, unwind = (int(row[key], 16) for key in ["begin", "end", "unwind"])
                    self.assertTrue(0 <= begin < end <= len(image))
                    self.assertLess(unwind, len(image))
                with (folder / "imports.tsv").open() as stream:
                    imports = list(csv.DictReader(stream, delimiter="\t"))
                self.assertEqual(len(imports), expected_imports)
                self.assertEqual(len({r["rva"] for r in imports}), expected_imports)
                for row in imports:
                    self.assertTrue(0 <= int(row["rva"], 16) <= len(image) - 8)
                    self.assertTrue(row["library"].lower().endswith(".dll"))
                    self.assertTrue(row["symbol"])

    def test_bootstrap_targets_contain_native_prologues(self):
        for module, entry in [("client", 0x3E3AA0), ("loader", 0x13E0)]:
            image = (self.folder / module / "image.bin").read_bytes()
            self.assertEqual(image[entry:entry + 5], bytes.fromhex("48895c2408"))


if __name__ == "__main__":
    unittest.main()
