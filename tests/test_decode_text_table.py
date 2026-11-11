import json
import struct
import tempfile
import unittest
from pathlib import Path

from recovered.text.decode_text_table import (
    CLIENT_BASE,
    DecodeError,
    decode_table,
    find_class_magic,
    is_structural_classfile,
    scan_sensitive_candidates,
    write_outputs,
)


def fixture_dump(texts):
    body = b"".join(text.encode("utf-8") + b"\0" for text in texts)
    table_rva = 0x100
    plaintext_rva = 0x400
    header = struct.pack("<4sII", b"TXT1", len(texts), len(body))
    descriptors = bytearray()
    offset = 0
    for i, text in enumerate(texts):
        length = len(text.encode("utf-8")) + 1
        descriptors += struct.pack("<QIIQ", 0x123400 + i, offset, length, 0xABC000 + i)
        offset += length
    dump = bytearray(0x500)
    dump[table_rva : table_rva + len(header)] = header
    descriptor_end = table_rva + len(header) + len(descriptors)
    dump[table_rva : table_rva + len(header)] = header
    dump[table_rva + len(header) : descriptor_end] = descriptors
    data_start = descriptor_end
    dump[data_start : data_start + len(body)] = bytes(value ^ 0xA5 for value in body)
    dump[plaintext_rva : plaintext_rva + len(body)] = body
    return bytes(dump), table_rva, plaintext_rva, len(body)


def minimal_classfile():
    cp = b"\x01\x00\x01A\x07\x00\x01\x01\x00\x10java/lang/Object\x07\x00\x03"
    return (
        b"\xca\xfe\xba\xbe"
        + struct.pack(">HHH", 0, 61, 5)
        + cp
        + struct.pack(">HHHHHHH", 0x21, 2, 4, 0, 0, 0, 0)
    )


class DecodeTests(unittest.TestCase):
    def test_round_trip_and_offsets(self):
        dump, table, source, size = fixture_dump(["alpha", "", "Привет"])
        rows, raw = decode_table(
            dump, table_rva=table, plaintext_rva=source, expected_count=3,
            expected_size=size, image_size=len(dump),
        )
        self.assertEqual(raw, "alpha\0\0Привет\0".encode("utf-8"))
        self.assertEqual([r.index for r in rows], [0, 1, 2])
        self.assertEqual([r.cache_offset for r in rows], [0, 6, 7])
        self.assertEqual(rows[1].text, "")
        with tempfile.TemporaryDirectory() as tmp:
            write_outputs(rows, raw, Path(tmp), plaintext_rva=source)
            saved = [json.loads(line) for line in (Path(tmp) / "text-strings.jsonl").read_text().splitlines()]
            rebuilt = b"".join(bytes.fromhex(row["stored_raw_hex"]) for row in saved)
            self.assertEqual(rebuilt, raw)
            self.assertEqual(saved[0]["client_va"], CLIENT_BASE + source)

    def test_rejects_out_of_bounds_descriptor(self):
        dump, table, source, size = fixture_dump(["one", "two"])
        broken = bytearray(dump)
        struct.pack_into("<I", broken, table + 12 + 24 + 8, size + 1)
        with self.assertRaises(DecodeError):
            decode_table(
                bytes(broken), table_rva=table, plaintext_rva=source, expected_count=2,
                expected_size=size, image_size=len(dump),
            )

    def test_sensitive_detector_reports_only_categories(self):
        kinds, count = scan_sensitive_candidates(
            ["ordinary class name", "session_id=example-placeholder-value", "Bearer example-token-value"]
        )
        self.assertEqual(count, 2)
        self.assertEqual(kinds["credential-assignment"], 2)

    def test_classfile_structure_not_magic_only(self):
        valid = minimal_classfile()
        self.assertTrue(is_structural_classfile(valid))
        self.assertEqual(find_class_magic(b"x" + valid), [1])
        self.assertFalse(is_structural_classfile(b"\xca\xfe\xba\xbe\xfa\xce\xdb\xad\x00" * 4))

    def test_supplied_flat_dump(self):
        sample = Path(__file__).resolve().parents[1] / "analysis/images/client/image.bin"
        if not sample.exists():
            self.skipTest("supplied flat dump unavailable")
        dump = sample.read_bytes()
        records, plaintext = decode_table(dump)
        self.assertEqual(len(records), 0xED1A)
        self.assertEqual(len(plaintext), 0x16D582)
        self.assertEqual(len(find_class_magic(dump)), 1)
        self.assertFalse(is_structural_classfile(dump, 0x65CDC0))
        _, flagged = scan_sensitive_candidates(record.text for record in records)
        self.assertEqual(flagged, 0)


if __name__ == "__main__":
    unittest.main()
