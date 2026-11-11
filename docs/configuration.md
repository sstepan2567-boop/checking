# Recovered local configuration component

Static-only recovery from `analysis/wrappers/systemdlc.offline.dll/decomp.c` and `xrefs_strings.txt`, cross-checked against the PE disassembly/data of `/tmp/att/client2/systemdlc.offline.dll`. The sample was not executed. The Python module is a decoder only; its test helper builds synthetic fixtures, not production keys.

## Storage and library schema

`FUN_180003b30` (`0x180003b30`, Ghidra size 478) creates/checks the supplied configuration directory, appends `library.json` (format string at `0x180003c70`), lowercases the full path, hashes its UTF-16 bytes with SHA-256, then creates the `Local\SDLC.Configs.%hs` mutex (string at `0x180003cce`). The caller builds `%LOCALAPPDATA%\VcIakcjndWHopuxZ\configs` (`FUN_180002740`); the library is therefore `...\configs\library.json`.

`FUN_180007330` (`0x180007330`) validates the on-disk root object. Confirmed required root members are `version` (number exactly `1.0`), `active` (NUL-free string, at most 512 bytes), `configs` (array, fewer than 1,000 entries), `revoked` and `consumed` (arrays). The version constant is the IEEE-754 `1.0` value at `0x180020620`. `active` may be empty; otherwise it must resolve to a configuration.

Each `configs[]` entry is an object with:

- `config_name`: 1–512 UTF-8 bytes; no NUL, C0 controls or DEL, and not all spaces.
- `configData`: NUL-free string, 0–8 MiB in stored libraries (portable-key parser requires 1–8 MiB).
- `description`: NUL-free string, 0–8 KiB.
- `image`, `payload`: NUL-free strings, each 0–8 MiB.
- `id`: NUL-free string exactly 32 bytes.
- `config_key`: NUL-free string, 0–24 MiB.
- `created_at`, `updated_at`: NUL-free strings, 1–64 bytes.
- `revision`, `image_stamp`: JSON numbers; negative values are rejected.
- `is_private`, `is_imported`: booleans.
- `keys`: array of at most 100 objects. Each has `key_value` (1–24 MiB NUL-free string), `hash` (exactly 64 NUL-free bytes), `revision` (number), `used` and `one_use` (booleans).

Configuration names must be unique. Both `revoked` and `consumed` entries are validated as exactly 64-byte strings without embedded NUL. The schema evidence is the field lookups and limits in `FUN_180007330`; `FUN_180007b40` implements bounded NUL-free strings and `FUN_180007b80` the configuration-name rule. Limits above are byte limits, not character counts.

## Portable key

`FUN_180008a00` emits and `FUN_180008520` parses the `SDLC1.` token. Exact shape:

```text
SDLC1.<unpadded-base64url(JSON UTF-8 bytes)>.<64 lowercase hex SHA-256>
```

The encoder uses Base64 without line breaks, maps `+` to `-` and `/` to `_`, and truncates at `=` (`FUN_180008a00`, including its `0x180020c90` six-pointer field table). That table resolves to `config_name`, `description`, `configData`, `image`, `image_stamp`, `payload`. It also emits `version: 1.0`, a generated `nonce`, and `one_use`. The parser maps the URL alphabet back, adds Base64 padding, decodes, hashes the exact decoded JSON byte sequence, and compares the suffix (`FUN_180008520`). `FUN_180003d50` uses BCrypt SHA-256; `FUN_180006120` formats each digest byte with `%02x` (confirmed by disassembly at `0x180003e80` and helper disassembly at `0x180006120`), yielding lowercase hex. The digest is unkeyed: it detects accidental/tampered bytes only when the checksum is not recomputed; it is not a signature or proof of origin.

Native parser constraints reflected in `decode_key`: total token length 73 bytes through `0x1800000`, correct prefix/separator, Base64URL alphabet without padding, version `1.0`, and NUL-free bounded `config_name`, `configData`, `description`, `image`, and `payload` strings. The native token parser does not validate `nonce`, `one_use`, or `image_stamp` at this stage. The decoder returns the full JSON object and does not recreate native library import/merge behavior.

## Local operation dispatch

`FUN_180003f30` (`0x180003f30`) routes these IDs to local storage:

- `0x1e`–`0x28` inclusive (the `param - 0x1e < 0xb` range);
- `0x2d`–`0x30` and `0x5c`–`0x5d` (mask `0x180000000000f` selects offsets 0–3, 47–48 from `0x2d`);
- `0x6e`.

`FUN_180003f60` (`0x180003f60`) independently applies the same set in its initial guard (mask `0x40003` for offsets 0, 1 and 18 from `0x5c`). Confirmed internal cases include `0x1e` create, `0x1f` replace `configData`, `0x23` rename, `0x24` description, `0x2d` privacy, `0x30` offline-key handling, `0x5c` derive `waypoints` from stored `payload`, `0x5d` validate/store waypoint-array payload, and `0x6e` copy `image`. Not every accepted ID's external semantic name is recoverable from these local functions alone, so no names are assigned to the remaining IDs.

## Decoder and tests

- `sdlc_key_decoder.py`: standard-library decoder/validator; no encoder.
- `test_sdlc_key_decoder.py`: synthetic-only tests for successful decode, URL-safe alphabet, checksum, malformed encoding, version, required field, and name validation.

Run: `python3 -m unittest discover -s tests -v`.

## Compatibility limit

This is a handwritten Python reconstruction of the portable-key decoder, not
the original C++ source. It is intentionally stricter for duplicate JSON fields:
they are rejected, since the native linked-list parser reads the first matching
field while Python normally keeps the last. Canonical keys emitted by the native
generator have no duplicate fields. Tests are synthetic; native interoperability
was not executed.
