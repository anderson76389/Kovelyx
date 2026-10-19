#!/usr/bin/env python3
"""Non-network regression tests for the Codespaces first-account HTTP transport."""
from pathlib import Path
import importlib.util
import unittest
from unittest.mock import patch
from email.message import Message

MODULE = Path(__file__).parents[1] / "scripts/first-account.py"
spec = importlib.util.spec_from_file_location("kv_first_account", MODULE)
account = importlib.util.module_from_spec(spec)
spec.loader.exec_module(account)


class Response:
    headers = Message()

    def __enter__(self):
        return self

    def __exit__(self, *args):
        pass

    def read(self, maximum):
        return b'{"status":"ok"}'


class FirstAccountHttpTest(unittest.TestCase):
    def test_user_agent_is_not_python_urllib(self):
        with patch.object(account, "urlopen", return_value=Response()) as req:
            data, _ = account.api("GET", "/health")
            self.assertEqual(data["status"], "ok")
            headers = dict(req.call_args.args[0].header_items())
            self.assertEqual(headers.get("User-agent"), "KovelyxCloudSetup/1.0")

    def test_authorization_header_is_only_supplied_to_authenticated_requests(self):
        with patch.object(account, "urlopen", return_value=Response()) as req:
            account.api("GET", "/health")
            req_obj = req.call_args.args[0]
            self.assertNotIn("Authorization", dict(req_obj.header_items()))
            account.api("GET", "/v1/me", token="EXAMPLE_FAKE_TOKEN_NOT_REAL")
            headers = dict(req.call_args.args[0].header_items())
            self.assertEqual(headers.get("Authorization"), "Bearer EXAMPLE_FAKE_TOKEN_NOT_REAL")


if __name__ == "__main__":
    unittest.main()
