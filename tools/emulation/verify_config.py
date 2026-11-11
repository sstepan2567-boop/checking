"""Compare original selected machine code with compiled C++ on synthetic inputs."""
import argparse
import ctypes
import hashlib
import json
from pathlib import Path
import struct
import zipfile

import pefile
from tools.recover_images import SPECS
from tools.emulation.machine import DATA, Machine, model_memchr

BASE = 0x180000000


class JsonStringView(ctypes.Structure):
    _fields_ = [("type", ctypes.c_int32), ("reserved", ctypes.c_uint32),
                ("member_name", ctypes.c_uint64), ("data", ctypes.c_void_p),
                ("size", ctypes.c_size_t)]


def verify(archive: Path, library: Path) -> dict:
    member, expected, *_ = SPECS["client"]
    with zipfile.ZipFile(archive) as bundle:
        raw = bundle.read(member)
    if hashlib.sha256(raw).hexdigest() != expected:
        raise ValueError("unsupported wrapper hash")
    pe = pefile.PE(data=raw, fast_load=True)
    # Only the small wrapper prefix; neither entry point nor embedded client is run.
    image = bytearray(0x28000)
    for section in pe.sections:
        if section.VirtualAddress >= len(image):
            continue
        data = section.get_data()[:len(image) - section.VirtualAddress]
        image[section.VirtualAddress:section.VirtualAddress + len(data)] = data
    machine = Machine(BASE, bytes(image), [(BASE + 0x7B40, BASE + 0x7B7C), (BASE + 0x7B80, BASE + 0x7BFA)])
    machine.add_model(BASE + 0x16A80, model_memchr)
    native = ctypes.CDLL(str(library.resolve()))  # Our compiled reconstruction, never the sample.
    pointer = ctypes.POINTER(JsonStringView)
    native.recovered_config_name.argtypes = [pointer]
    native.recovered_config_name.restype = ctypes.c_bool
    native.recovered_bounded_string.argtypes = [pointer, ctypes.c_size_t, ctypes.c_size_t]
    native.recovered_bounded_string.restype = ctypes.c_bool
    cases = [(bytes([value]), 3, 0, 512) for value in range(256)]
    cases += [(value, kind, minimum, maximum) for value in
              [b"", b" ", b"   ", b" good ", b"bad\0name", b"a" * 512, b"a" * 513, "Имя".encode()]
              for kind, minimum, maximum in [(3, 1, 512), (3, 0, 0), (3, 5, 2), (2, 0, 512)]]
    cases.append((b"", None, 0, 512))
    instructions = modeled = compared = 0
    results = hashlib.sha256()
    for value, kind, minimum, maximum in cases:
        machine.write(DATA + 0x100, value + b"\0")
        machine.write(DATA, struct.pack("<iIQQQ", kind or 0, 0, 0, DATA + 0x100, len(value)))
        backing = ctypes.create_string_buffer(value)
        view = JsonStringView(kind or 0, 0, 0, ctypes.cast(backing, ctypes.c_void_p), len(value))
        argument = DATA if kind is not None else 0
        host_argument = ctypes.byref(view) if kind is not None else None
        for rva, expected_value, args in [
            (0x7B40, native.recovered_bounded_string(host_argument, minimum, maximum), (argument, minimum, maximum)),
            (0x7B80, native.recovered_config_name(host_argument), (argument,)),
        ]:
            actual = machine.call(BASE + rva, *args)
            if bool(actual.value & 255) != expected_value:
                raise AssertionError(f"mismatch at RVA {rva:#x}, fixture {compared}")
            compared += 1
            instructions += actual.instructions
            modeled += actual.modeled_calls
            results.update(bytes([actual.value & 255]))
    return {"mode": "bounded_cpu_emulation", "sample_sha256": expected,
            "function_rvas": ["0x7b40", "0x7b80"], "fixtures": len(cases), "comparisons": compared,
            "mismatches": 0, "instructions": instructions, "modeled_memchr_calls": modeled,
            "result_sha256": results.hexdigest(), "sample_process_started": False,
            "limitation": "memchr modeled; synthetic inputs; no Windows/JVM/game runtime"}


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
