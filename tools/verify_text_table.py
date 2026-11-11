"""Check reconstructed C++ lookup against TXT1 data, without executing sample code."""
import argparse
import ctypes
import hashlib
import json
from pathlib import Path
import struct
import zipfile

import pefile
from recovered.text.decode_text_table import decode_table, TABLE_RVA, HEADER_SIZE, ENTRY_SIZE
from tools.recover_images import SPECS


def verify(archive: Path, library: Path) -> dict:
    member, expected, *_ = SPECS["client"]
    with zipfile.ZipFile(archive) as bundle:
        if bundle.namelist().count(member) != 1:
            raise ValueError("missing or duplicate client member")
        if not 0 < bundle.getinfo(member).file_size < 128 * 1024 * 1024:
            raise ValueError("unexpected client member size")
        raw = bundle.read(member)
    if hashlib.sha256(raw).hexdigest() != expected:
        raise ValueError("unsupported wrapper hash")
    pe = pefile.PE(data=raw, fast_load=True)
    section = next(s for s in pe.sections if s.Name.rstrip(b"\0") == b".payload")
    image = section.get_data()[:section.Misc_VirtualSize]
    records, plaintext = decode_table(image)
    size = HEADER_SIZE + len(records) * ENTRY_SIZE + len(plaintext)
    resource = image[TABLE_RVA:TABLE_RVA + size]
    # This is our compiled reconstruction, not the supplied DLL.
    native = ctypes.CDLL(str(library.resolve()))
    native.recovered_text_create.argtypes = [ctypes.c_void_p, ctypes.c_size_t] * 2
    native.recovered_text_create.restype = ctypes.c_void_p
    native.recovered_text_destroy.argtypes = [ctypes.c_void_p]
    native.recovered_text_destroy.restype = None
    native.recovered_text_offset.argtypes = [ctypes.c_void_p, ctypes.c_uint64]
    native.recovered_text_offset.restype = ctypes.c_int64
    resource_buffer = ctypes.create_string_buffer(resource)
    plaintext_buffer = ctypes.create_string_buffer(plaintext)
    table = native.recovered_text_create(resource_buffer, len(resource), plaintext_buffer, len(plaintext))
    if not table:
        raise AssertionError("C++ rejected extracted TXT1 data")
    keys = {record.key for record in records}
    digest = hashlib.sha256()
    misses = 0
    try:
        for record in records:
            actual = native.recovered_text_offset(table, record.key)
            if actual != record.cache_offset:
                raise AssertionError(f"lookup mismatch at descriptor {record.index}")
            digest.update(struct.pack("<Qq", record.key, actual))
            missing = record.key ^ (1 << 63)
            if missing not in keys:
                if native.recovered_text_offset(table, missing) != -1:
                    raise AssertionError(f"false match at descriptor {record.index}")
                misses += 1
    finally:
        native.recovered_text_destroy(table)
    return {"mode": "reconstructed_cpp_against_static_data", "sample_sha256": expected,
            "image_sha256": hashlib.sha256(image).hexdigest(), "records": len(records),
            "successful_lookups": len(records), "missing_key_checks": misses, "mismatches": 0,
            "result_sha256": digest.hexdigest(), "sample_instructions_executed": False,
            "limitation": "Predecoded cache only; not a machine-code comparison or lazy-transform verification"}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path)
    parser.add_argument("library", type=Path)
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    report = verify(args.archive, args.library)
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report, indent=2))
