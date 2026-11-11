"""Recover flat memory images and metadata without loading or executing them."""
import argparse
import csv
import hashlib
import json
from pathlib import Path
import struct
import zipfile

import pefile

SPECS = {
    "client": ("systemdlc.offline.dll", "0dd86cdc54cbe5b1235454b4751b157098c6f7e9fb57840abd1fef48e4d39a22", 0x2A4416A0000, 0x17240, 644, 0x781000, 23448, 0x3E3AA0),
    "loader": ("systemloader.offline.dll", "8029111e6885b7910aedcb744a14b2e9cac2a57dfe1391693d1023aea7270446", 0x2B751890000, 0xB140, 350, 0x118000, 2763, 0x13E0),
}


def write_tsv(path, columns, rows):
    with path.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.writer(stream, delimiter="\t")
        writer.writerow(columns)
        writer.writerows(rows)


def recover(archive, destination):
    destination.mkdir(parents=True, exist_ok=True)
    results = {}
    with zipfile.ZipFile(archive) as bundle:
        if len(set(bundle.namelist())) != len(bundle.namelist()):
            raise ValueError("duplicate archive members")
        for name, (member, expected, base, imports_rva, imports_count, unwind, count, entry) in SPECS.items():
            info = bundle.getinfo(member)
            if not 0 < info.file_size < 128 * 1024 * 1024:
                raise ValueError("unexpected member size")
            wrapper = bundle.read(member)  # ZIP CRC verified before any output.
            digest = hashlib.sha256(wrapper).hexdigest()
            if digest != expected:
                raise ValueError(f"{member}: unsupported build ({digest})")
            pe = pefile.PE(data=wrapper, fast_load=True)
            section = next(s for s in pe.sections if s.Name.rstrip(b"\0") == b".payload")
            image = section.get_data()[:section.Misc_VirtualSize]
            inner = pefile.PE(data=image, fast_load=True)
            image_base = pe.OPTIONAL_HEADER.ImageBase
            imports = []
            for index in range(imports_count):
                slot, dll_ptr, name_ptr = struct.unpack("<QQQ", pe.get_data(imports_rva + index * 24, 24))
                if slot + 8 > len(image):
                    raise ValueError("import slot outside image")
                dll = pe.get_string_at_rva(dll_ptr - image_base).decode("ascii")
                symbol = pe.get_string_at_rva(name_ptr - image_base).decode("ascii")
                if not dll.lower().endswith(".dll") or not symbol:
                    raise ValueError("invalid import record")
                imports.append((hex(slot), dll, symbol))
            functions = list(struct.iter_unpack("<III", image[unwind:unwind + count * 12]))
            if len(functions) != count or any(not (0 <= a < b <= len(image) and 0 < u < len(image)) for a, b, u in functions):
                raise ValueError("invalid unwind ranges")
            folder = destination / name
            folder.mkdir(exist_ok=True)
            (folder / "image.bin").write_bytes(image)
            write_tsv(folder / "imports.tsv", ("rva", "library", "symbol"), imports)
            write_tsv(folder / "functions.tsv", ("begin", "end", "unwind"), ((hex(a), hex(b), hex(c)) for a, b, c in functions))
            write_tsv(folder / "sections.tsv", ("rva", "size", "flags", "name"), ((hex(s.VirtualAddress), hex(s.Misc_VirtualSize), hex(s.Characteristics), s.Name.rstrip(b"\0").decode("ascii").strip() or f"section_{i}") for i, s in enumerate(inner.sections)))
            results[name] = {"wrapper": member, "sha256": digest, "base": hex(base), "size": len(image), "entry_rva": hex(entry), "imports": len(imports), "unwind_ranges": len(functions), "payload_file_offset": hex(section.PointerToRawData), "image_sha256": hashlib.sha256(image).hexdigest()}
            (folder / "metadata.json").write_text(json.dumps(results[name], indent=2) + "\n")
    (destination / "manifest.json").write_text(json.dumps(results, indent=2) + "\n")
    return results


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    print(json.dumps(recover(args.archive, args.output), indent=2))
