#!/usr/bin/env python3
"""Download immutable source references and verify bytes before vault publication."""
import hashlib
import json
from pathlib import Path
import sys
import urllib.request

SOURCES = [
    ('mpv-f6c116491a82314332f8ded74fc92b2b5cdf7e25.tar.gz',
     'https://github.com/mpv-player/mpv/archive/f6c116491a82314332f8ded74fc92b2b5cdf7e25.tar.gz',
     '0f2e34c6126bd1370cb797e0bd6b6fea4326ffdf7d16df978270cfc101fb9d52',
     'Core revision reported by the preserved DLL; full dependency/patch correspondence not certified.'),
    ('libtorrent-740a0b9aeabe00e762cc0efe4a0f27593db2550b.tar.gz',
     'https://github.com/arvidn/libtorrent/archive/740a0b9aeabe00e762cc0efe4a0f27593db2550b.tar.gz',
     'fa8e1544712fa3d24c4b345c120d6a85e2f36367877c34d500d355ed2a0d9e11',
     'Pinned native engine dependency. Local patches are in the Kovelyx source snapshot.'),
    ('try_signal-105cce59972f925a33aa6b1c3109e4cd3caf583d.tar.gz',
     'https://github.com/arvidn/try_signal/archive/105cce59972f925a33aa6b1c3109e4cd3caf583d.tar.gz',
     '6f111b0d77429a8051be4faed06cf23fb03cf6ee233967c84fdd9d8d3b42ba8e',
     'Pinned libtorrent submodule.'),
    ('openssl-3.5.7.tar.gz',
     'https://github.com/openssl/openssl/releases/download/openssl-3.5.7/openssl-3.5.7.tar.gz',
     'a8c0d28a529ca480f9f36cf5792e2cd21984552a3c8e4aa11a24aa31aeac98e8',
     'Pinned engine TLS dependency; not a claim about the old MPV DLL.'),
    ('boost_1_86_0.tar.bz2',
     'https://archives.boost.io/release/1.86.0/source/boost_1_86_0.tar.bz2',
     '1bed88e40401b2cb7a1f76d4bab499e352fa4d0c5f31c0dbae64e24d34d7513b',
     'Pinned engine Boost dependency.'),
]


def main(output):
    output = Path(output)
    output.mkdir(parents=True, exist_ok=True)
    records = []
    for name, url, expected, scope in SOURCES:
        path = output / name
        digest = hashlib.sha256()
        with urllib.request.urlopen(url, timeout=60) as response, path.open('wb') as target:
            while data := response.read(1024 * 1024):
                digest.update(data)
                target.write(data)
        if digest.hexdigest() != expected:
            path.unlink()
            raise RuntimeError(f'Unexpected source archive: {name}')
        path.with_name(name + '.sha256').write_text(f'{expected}  {name}\n', encoding='ascii')
        records.append({'name': name, 'url': url, 'size': path.stat().st_size, 'sha256': expected, 'scope': scope})
        print(f'Verified source archive: {name}')
    (output / 'native-source-references.json').write_text(json.dumps(records, indent=2) + '\n', encoding='utf-8')


if __name__ == '__main__':
    main(sys.argv[1])
