#!/usr/bin/env python3
"""Fixtures for compressed and Windows payloads missed by the original scan."""
from collections import defaultdict
import importlib.util
import io
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

spec = importlib.util.spec_from_file_location('scan', Path(__file__).with_name('scan_all_independence.py'))
scan = importlib.util.module_from_spec(spec)
spec.loader.exec_module(scan)


def archive(entries):
    out = io.BytesIO()
    with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as z:
        for name, data in entries.items():
            z.writestr(name, data)
    out.seek(0)
    return out


class PayloadScanTests(unittest.TestCase):
    def test_nested_jar_inside_aar_inside_apk(self):
        jar = archive({'extensionless': b'https://api.nuvio.tv/account SupabaseClient'})
        aar = archive({'classes.jar': jar.getvalue()})
        apk = archive({'assets/engine.aar': aar.getvalue()})
        hits, errors = defaultdict(list), []
        scan.scan_zip(apk, hits, errors)
        self.assertFalse(errors)
        for key in ('legacy_host', 'retired_sdk'):
            self.assertTrue(any('classes.jar!extensionless' in p for p in hits[key]))

    def test_windows_utf16_and_buffer_boundaries(self):
        for codec in ('utf-16le', 'utf-16be'):
            for offset in (scan.BUFFER - 11, scan.BUFFER - 12):
                hits = defaultdict(list)
                payload = b'x' * offset + 'https://api.nuvio.tv'.encode(codec)
                scan.scan_stream(io.BytesIO(payload), 'engine.dll', hits)
                self.assertEqual(['engine.dll'], hits['legacy_host'])

    def test_depth_and_corruption_are_errors(self):
        hits, errors = defaultdict(list), []
        scan.scan_zip(io.BytesIO(b'broken'), hits, errors)
        self.assertTrue(errors)
        errors.clear()
        data = archive({'safe.txt': b'hello'}).getvalue()
        for _ in range(scan.MAX_ARCHIVE_DEPTH + 2):
            data = archive({'nested.jar': data}).getvalue()
        scan.scan_zip(io.BytesIO(data), hits, errors)
        self.assertTrue(any('depth limit' in e for e in errors))

    def test_archive_size_limit_is_reported(self):
        hits, errors = defaultdict(list), []
        scan.scan_zip(archive({'safe.txt': b'hello'}), hits, errors, budget=[2])
        self.assertTrue(errors)

    def test_missing_msi_extractor_is_error(self):
        hits, errors = defaultdict(list), []
        with patch.object(scan.shutil, 'which', return_value=None):
            scan.scan_msi(Path('app.msi'), hits, errors)
        self.assertTrue(errors)

    def test_msi_extracted_payload_is_scanned_recursively(self):
        def extract(args, **kwargs):
            dest = Path(args[2])
            (dest / 'engine.dll').write_bytes('https://api.nuvio.tv'.encode('utf-16le'))
            (dest / 'app.jar').write_bytes(archive({'code': b'SupabaseClient'}).getvalue())
        hits, errors = defaultdict(list), []
        with patch.object(scan.shutil, 'which', return_value='/usr/bin/msiextract'), patch.object(scan.subprocess, 'run', side_effect=extract):
            scan.scan_msi(Path('app.msi'), hits, errors)
        self.assertFalse(errors)
        self.assertTrue(hits['legacy_host'])
        self.assertTrue(hits['retired_sdk'])

    def test_failed_or_empty_msi_extraction_is_error(self):
        for effect in (scan.subprocess.CalledProcessError(1, 'msiextract'), None):
            errors = []
            with patch.object(scan.shutil, 'which', return_value='/usr/bin/msiextract'), patch.object(scan.subprocess, 'run', side_effect=effect):
                scan.scan_msi(Path('app.msi'), defaultdict(list), errors)
            self.assertTrue(errors)

    def test_public_suffix_registry_is_not_a_server(self):
        for extra, expected in ((b"", False), (b"https://supabase.co/account\n", True)):
            hits, errors = defaultdict(list), []
            scan.scan_zip(archive({'assets/PublicSuffixDatabase.list': b'com\nsupabase.co\nnet\n' + extra}), hits, errors)
            self.assertFalse(errors)
            self.assertTrue(hits['public_suffix_registry'])
            self.assertEqual(expected, bool(hits['legacy_host']))

    def test_safe_archive_has_no_legacy_findings(self):
        hits, errors = defaultdict(list), []
        scan.scan_zip(archive({'safe': b'https://api.github.com/repos/anderson76389/Kovelyx'}), hits, errors)
        self.assertFalse(errors)
        self.assertFalse(any(hits[k] for k in scan.PATTERNS))


if __name__ == '__main__':
    unittest.main()
