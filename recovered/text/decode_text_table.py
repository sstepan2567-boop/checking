#!/usr/bin/env python3
"""Decode the TXT1 resource table from a flat client memory dump."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import re
import struct
import sys
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

CLIENT_BASE = 0x2A4416A0000
IMAGE_SIZE = 0x5317F58
TABLE_RVA = 0x311E8A0
PLAINTEXT_RVA = 0x51AA8B0
ENTRY_COUNT = 0xED1A
PLAINTEXT_SIZE = 0x16D582
HEADER_SIZE = 12
ENTRY_SIZE = 24

SECRET_LABEL = re.compile(
    r"(?i)(?:password|passwd|client[_ -]?secret|api[_ -]?key|access[_ -]?token|"
    r"refresh[_ -]?token|authorization|bearer|session(?:[_ -]?id)?|cookie|private[_ -]?key)"
)
SECRET_ASSIGNMENT = re.compile(
    r"(?i)(?:password|passwd|client[_ -]?secret|api[_ -]?key|access[_ -]?token|"
    r"refresh[_ -]?token|authorization|bearer|session(?:[_ -]?id)?|cookie|private[_ -]?key)"
    r"(?:\s*[:=]\s*|\s+)([^\s,;]{8,})"
)
JWT = re.compile(r"^[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}$")
KNOWN_TOKEN = re.compile(
    r"(?i)(?:gh[pousr]_[A-Za-z0-9]{20,}|sk-[A-Za-z0-9]{20,}|xox[baprs]-[A-Za-z0-9-]{15,})"
)


class DecodeError(ValueError):
    pass


@dataclass(frozen=True)
class TextRecord:
    index: int
    descriptor_offset: int
    cache_offset: int
    record_length: int
    key: int
    aux: int
    raw: bytes
    text: str


def scan_sensitive_candidates(texts: Iterable[str]) -> tuple[Counter[str], int]:
    """Return metadata only; never return or print candidate values."""
    kinds: Counter[str] = Counter()
    flagged = 0
    for text in texts:
        matched = False
        if SECRET_LABEL.search(text):
            kinds["credential-like-label"] += 1
        if SECRET_ASSIGNMENT.search(text):
            kinds["credential-assignment"] += 1
            matched = True
        if JWT.fullmatch(text.strip()):
            kinds["jwt-shaped"] += 1
            matched = True
        if KNOWN_TOKEN.search(text):
            kinds["known-token-prefix"] += 1
            matched = True
        candidate = text.strip()
        if len(candidate) >= 24 and re.fullmatch(r"[A-Za-z0-9_./+=-]+", candidate):
            frequencies = Counter(candidate)
            entropy = -sum((n / len(candidate)) * math.log2(n / len(candidate)) for n in frequencies.values())
            letters = sum(ch.isalpha() for ch in candidate) / len(candidate)
            digits = sum(ch.isdigit() for ch in candidate) / len(candidate)
            if entropy >= 4.5 and letters > 0.4 and digits > 0.1:
                kinds["high-entropy-token-like"] += 1
                matched = True
        flagged += int(matched)
    return kinds, flagged


def _u16(data: bytes, offset: int) -> tuple[int, int]:
    if offset + 2 > len(data):
        raise DecodeError("truncated class file")
    return struct.unpack_from(">H", data, offset)[0], offset + 2


def _skip_attributes(data: bytes, offset: int, count: int) -> int:
    for _ in range(count):
        _, offset = _u16(data, offset)
        if offset + 4 > len(data):
            raise DecodeError("truncated class attribute")
        length = struct.unpack_from(">I", data, offset)[0]
        offset += 4 + length
        if offset > len(data):
            raise DecodeError("class attribute exceeds input")
    return offset


def is_structural_classfile(data: bytes, offset: int = 0) -> bool:
    """Validate a class-file structure at offset, not just CAFEBABE magic."""
    try:
        if data[offset : offset + 4] != b"\xca\xfe\xba\xbe":
            return False
        minor, major = struct.unpack_from(">HH", data, offset + 4)
        if not 45 <= major <= 100 or (minor == 0xFFFF and major < 56):
            return False
        cp_count, pos = _u16(data, offset + 8)
        if cp_count < 2:
            return False
        i = 1
        while i < cp_count:
            if pos >= len(data):
                return False
            tag = data[pos]
            pos += 1
            if tag == 1:
                length, pos = _u16(data, pos)
                pos += length
            elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
                pos += 4
            elif tag in (5, 6):
                pos += 8
                i += 1
            elif tag in (7, 8, 16, 19, 20):
                pos += 2
            elif tag == 15:
                pos += 3
            else:
                return False
            if pos > len(data) or i >= cp_count:
                return False
            i += 1
        if pos + 8 > len(data):
            return False
        _, this_class, super_class = struct.unpack_from(">HHH", data, pos)
        if this_class == 0 or super_class >= cp_count:
            return False
        pos += 6
        interfaces, pos = _u16(data, pos)
        pos += interfaces * 2
        if pos > len(data):
            return False
        for _ in range(2):
            count, pos = _u16(data, pos)
            for _ in range(count):
                pos += 6
                attr_count, pos = _u16(data, pos)
                pos = _skip_attributes(data, pos, attr_count)
        attr_count, pos = _u16(data, pos)
        pos = _skip_attributes(data, pos, attr_count)
        return pos <= len(data)
    except (DecodeError, struct.error, IndexError):
        return False


def find_class_magic(data: bytes) -> list[int]:
    positions = []
    start = 0
    while True:
        start = data.find(b"\xca\xfe\xba\xbe", start)
        if start < 0:
            return positions
        positions.append(start)
        start += 1


def decode_table(
    dump: bytes,
    *,
    table_rva: int = TABLE_RVA,
    plaintext_rva: int = PLAINTEXT_RVA,
    expected_count: int = ENTRY_COUNT,
    expected_size: int = PLAINTEXT_SIZE,
    image_size: int = IMAGE_SIZE,
) -> tuple[list[TextRecord], bytes]:
    if len(dump) < image_size:
        raise DecodeError("dump shorter than declared client image")
    if table_rva + HEADER_SIZE > image_size:
        raise DecodeError("table header exceeds client image")
    magic, count, data_size = struct.unpack_from("<4sII", dump, table_rva)
    if magic != b"TXT1":
        raise DecodeError("TXT1 magic mismatch")
    if count != expected_count or data_size != expected_size:
        raise DecodeError("unexpected TXT1 count or plaintext size")
    descriptors = table_rva + HEADER_SIZE
    data_rva = descriptors + count * ENTRY_SIZE
    if data_rva + data_size > image_size:
        raise DecodeError("TXT1 table exceeds client image")
    if plaintext_rva + data_size > image_size:
        raise DecodeError("plaintext cache exceeds client image")
    if data_rva + data_size > len(dump) or plaintext_rva + data_size > len(dump):
        raise DecodeError("TXT1 or plaintext cache truncated in dump")

    plaintext = dump[plaintext_rva : plaintext_rva + data_size]
    records = []
    cursor = 0
    for index in range(count):
        descriptor = descriptors + index * ENTRY_SIZE
        key, cache_offset, record_length, aux = struct.unpack_from("<QIIQ", dump, descriptor)
        if cache_offset != cursor or record_length < 1:
            raise DecodeError(f"invalid descriptor at index {index}")
        end = cache_offset + record_length
        if end > data_size:
            raise DecodeError(f"record at index {index} exceeds plaintext cache")
        stored = plaintext[cache_offset:end]
        if stored[-1] != 0 or b"\0" in stored[:-1]:
            raise DecodeError(f"record at index {index} has invalid NUL framing")
        raw = stored[:-1]
        try:
            text = raw.decode("utf-8")
        except UnicodeDecodeError as exc:
            raise DecodeError(f"record at index {index} is not UTF-8") from exc
        records.append(TextRecord(index, descriptor, cache_offset, record_length, key, aux, raw, text))
        cursor = end
    if cursor != data_size:
        raise DecodeError("descriptor lengths do not cover plaintext cache")
    return records, plaintext


def write_outputs(records: list[TextRecord], plaintext: bytes, out_dir: Path, plaintext_rva: int = PLAINTEXT_RVA) -> None:
    out_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / "plaintext-cache.bin").write_bytes(plaintext)
    with (out_dir / "text-strings.jsonl").open("w", encoding="utf-8", newline="\n") as jf:
        for record in records:
            payload_rva = plaintext_rva + record.cache_offset
            row = {
                "index": record.index,
                "descriptor_rva": record.descriptor_offset,
                "cache_offset": record.cache_offset,
                "payload_rva": payload_rva,
                "client_va": CLIENT_BASE + payload_rva,
                "record_length": record.record_length,
                "byte_length": len(record.raw),
                "key_u64": record.key,
                "aux_u64": record.aux,
                "text": record.text,
                "raw_hex": record.raw.hex(),
                "stored_raw_hex": (record.raw + b"\0").hex(),
            }
            jf.write(json.dumps(row, ensure_ascii=False, separators=(",", ":")) + "\n")
    with (out_dir / "text-strings.tsv").open("w", encoding="utf-8", newline="\n") as tf:
        tf.write(
            "index\tdescriptor_rva_hex\tcache_offset_hex\tpayload_rva_hex\tclient_va_hex\t"
            "record_length\tbyte_length\tkey_u64_hex\taux_u64_hex\ttext_json\n"
        )
        for record in records:
            rva = plaintext_rva + record.cache_offset
            tf.write(
                f"{record.index}\t0x{record.descriptor_offset:x}\t0x{record.cache_offset:x}"
                f"\t0x{rva:x}\t0x{CLIENT_BASE + rva:x}\t{record.record_length}"
                f"\t{len(record.raw)}\t0x{record.key:016x}\t0x{record.aux:016x}"
                f"\t{json.dumps(record.text, ensure_ascii=False)}\n"
            )


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("dump", type=Path, help="flat client memory dump")
    parser.add_argument("--out", type=Path, default=Path("text-recovery"))
    args = parser.parse_args(argv)
    try:
        dump = args.dump.read_bytes()
        records, plaintext = decode_table(dump)
        kinds, flagged = scan_sensitive_candidates(record.text for record in records)
        if flagged:
            print(
                "Refusing export: potential credential/session value pattern found; "
                "only metadata can be reported.",
                file=sys.stderr,
            )
            return 2
        write_outputs(records, plaintext, args.out)
    except (OSError, DecodeError) as exc:
        print(f"decode failed: {exc}", file=sys.stderr)
        return 1
    print(
        f"decoded={len(records)} bytes={len(plaintext):#x} utf8=all roundtrip=exact "
        f"flagged_values={flagged} label_mentions={kinds['credential-like-label']} "
        f"source_sha256={hashlib.sha256(plaintext).hexdigest()}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
