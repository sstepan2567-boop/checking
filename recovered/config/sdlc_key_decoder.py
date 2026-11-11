"""Offline decoder for the client's SDLC1 configuration-export token."""

from __future__ import annotations

import base64
import binascii
import hashlib
import hmac
import json
import re
from typing import Any

PREFIX = "SDLC1."
MAX_TOKEN_BYTES = 0x1800000
MAX_NAME_BYTES = 0x200
MAX_CONFIG_DATA_BYTES = 0x800000
MAX_DESCRIPTION_BYTES = 0x2000
_HEX_SHA256 = re.compile(r"[0-9a-f]{64}\Z")
_BASE64URL = re.compile(r"[A-Za-z0-9_-]+\Z")


def _fail(message: str) -> None:
    raise ValueError(message)


def _string_bytes(value: Any, field: str, minimum: int, maximum: int) -> bytes:
    if not isinstance(value, str):
        _fail(f"{field} must be a JSON string")
    try:
        raw = value.encode("utf-8")
    except UnicodeEncodeError as exc:
        raise ValueError(f"{field} is not valid UTF-8 text") from exc
    if not minimum <= len(raw) <= maximum or b"\0" in raw:
        _fail(f"{field} has an invalid byte length or embedded NUL")
    return raw


def _check_config_name(value: Any) -> None:
    raw = _string_bytes(value, "config_name", 1, MAX_NAME_BYTES)
    if any(byte < 0x20 or byte == 0x7F for byte in raw) or not any(
        byte > 0x20 for byte in raw
    ):
        _fail("config_name contains controls or is blank")


def _reject_constant(value: str) -> None:
    raise ValueError(f"invalid JSON numeric constant: {value}")


def _unique_object(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"duplicate JSON field: {key}")
        result[key] = value
    return result


def decode_key(token: str) -> dict[str, Any]:
    """Verify and decode one native SDLC1 configuration-export token.

    This validates the unkeyed SHA-256 checksum and the fields checked by the
    native token parser. Duplicate fields are rejected to avoid first/last-value
    ambiguity. It does not establish who created the token.
    """
    if not isinstance(token, str):
        _fail("token must be text")
    try:
        token_bytes = token.encode("ascii")
    except UnicodeEncodeError as exc:
        raise ValueError("token must contain ASCII characters only") from exc
    if not 0x49 <= len(token_bytes) <= MAX_TOKEN_BYTES:
        _fail("token length is outside the native parser limits")
    if not token.startswith(PREFIX):
        _fail("unsupported token prefix")

    separator = len(token) - 65
    if token[separator] != ".":
        _fail("missing checksum separator")
    encoded = token[len(PREFIX) : separator]
    checksum = token[-64:]
    if not _BASE64URL.fullmatch(encoded):
        _fail("payload is not unpadded Base64URL")
    if len(encoded) % 4 == 1:
        _fail("invalid Base64URL length")
    if not _HEX_SHA256.fullmatch(checksum):
        _fail("checksum must be 64 lowercase hexadecimal characters")

    standard = encoded.replace("-", "+").replace("_", "/")
    standard += "=" * ((-len(standard)) % 4)
    try:
        payload = base64.b64decode(standard, validate=True)
    except (ValueError, binascii.Error) as exc:
        raise ValueError("invalid Base64URL payload") from exc
    actual = hashlib.sha256(payload).hexdigest()
    if not hmac.compare_digest(actual, checksum):
        _fail("SHA-256 checksum mismatch")

    try:
        document = json.loads(payload.decode("utf-8"), parse_constant=_reject_constant, object_pairs_hook=_unique_object)
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise ValueError("payload is not valid UTF-8 JSON") from exc
    if not isinstance(document, dict):
        _fail("payload must be a JSON object")

    version = document.get("version")
    if isinstance(version, bool) or not isinstance(version, (int, float)) or version != 1.0:
        _fail("unsupported portable-key version")
    _check_config_name(document.get("config_name"))
    _string_bytes(document.get("configData"), "configData", 1, MAX_CONFIG_DATA_BYTES)
    _string_bytes(document.get("description"), "description", 0, MAX_DESCRIPTION_BYTES)
    _string_bytes(document.get("image"), "image", 0, MAX_CONFIG_DATA_BYTES)
    _string_bytes(document.get("payload"), "payload", 0, MAX_CONFIG_DATA_BYTES)
    return document
