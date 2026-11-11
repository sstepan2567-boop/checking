import base64
import hashlib
import json
import unittest

from recovered.config.sdlc_key_decoder import decode_key


def _synthetic_token(document):
    payload = json.dumps(document, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    encoded = base64.urlsafe_b64encode(payload).decode("ascii").rstrip("=")
    return f"SDLC1.{encoded}.{hashlib.sha256(payload).hexdigest()}"


def _document():
    return {
        "version": 1.0,
        "nonce": "synthetic-only",
        "one_use": False,
        "config_name": "Fixture",
        "description": "",
        "configData": "data",
        "image": "",
        "image_stamp": 1,
        "payload": "",
    }


class DecoderTests(unittest.TestCase):
    def test_round_trip_and_url_alphabet(self):
        document = _document()
        document["configData"] = "\U0010ffff"
        token = _synthetic_token(document)
        encoded = token[len("SDLC1.") : -65]
        self.assertIn("-", encoded)
        self.assertIn("_", encoded)
        self.assertEqual(decode_key(token), document)

    def test_checksum_is_verified(self):
        token = _synthetic_token(_document())
        replacement = "0" if token[-1] != "0" else "1"
        with self.assertRaisesRegex(ValueError, "checksum mismatch"):
            decode_key(token[:-1] + replacement)

    def test_malformed_base64url_is_rejected(self):
        token = _synthetic_token(_document())
        payload = token[len("SDLC1.") : -65]
        malformed = "SDLC1." + payload + "=" + token[-65:]
        with self.assertRaisesRegex(ValueError, "Base64URL"):
            decode_key(malformed)

    def test_invalid_version_is_rejected_even_with_valid_checksum(self):
        document = _document()
        document["version"] = 2.0
        with self.assertRaisesRegex(ValueError, "version"):
            decode_key(_synthetic_token(document))

    def test_required_string_is_enforced(self):
        document = _document()
        del document["configData"]
        with self.assertRaisesRegex(ValueError, "configData"):
            decode_key(_synthetic_token(document))

    def test_boolean_version_is_not_a_number(self):
        document = _document()
        document["version"] = True
        with self.assertRaisesRegex(ValueError, "version"):
            decode_key(_synthetic_token(document))

    def test_duplicate_json_fields_are_rejected(self):
        payload = b'{"version":2,"version":1,"config_name":"Fixture","configData":"data","description":"","image":"","payload":""}'
        encoded = base64.urlsafe_b64encode(payload).decode("ascii").rstrip("=")
        token = f"SDLC1.{encoded}.{hashlib.sha256(payload).hexdigest()}"
        with self.assertRaisesRegex(ValueError, "duplicate JSON field"):
            decode_key(token)

    def test_control_character_in_name_is_rejected(self):
        document = _document()
        document["config_name"] = "bad\nname"
        with self.assertRaisesRegex(ValueError, "config_name"):
            decode_key(_synthetic_token(document))


if __name__ == "__main__":
    unittest.main()
