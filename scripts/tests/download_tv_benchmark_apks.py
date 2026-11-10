#!/usr/bin/env python3
"""Download exact, successful build artifacts for an isolated emulator comparison."""
import json
import os
from pathlib import Path
import subprocess
import time

REPO = 'anderson76389/Kovelyx'
BASE_RUN = 42835163700
BASE_SHA = '83c81a84cce6630ae6c69e042751298e5416b163'


def api(path):
    return json.loads(subprocess.check_output(['gh', 'api', f'repos/{REPO}/{path}'], text=True))


def main():
    event = json.loads(Path(os.environ['GITHUB_EVENT_PATH']).read_text())
    pr = event['pull_request']
    assert pr['head']['repo']['full_name'] == REPO
    expected = pr['head']['sha']
    # Baseline source must be identical to the PR base for the measured TV app.
    subprocess.run(['git', 'diff', '--exit-code', BASE_SHA, pr['base']['sha'],
                    '--', 'apps/tv'], check=True)
    proofs = {}
    for label in ('baseline', 'candidate'):
        ready = None
        for _ in range(240):
            if label == 'baseline':
                run = api(f'actions/runs/{BASE_RUN}')
                assert run['head_sha'] == BASE_SHA and run['event'] == 'push'
            else:
                runs = api('actions/workflows/build.yml/runs?event=pull_request&per_page=100')['workflow_runs']
                matching = [r for r in runs if r['head_sha'] == expected
                            and r['head_repository']['full_name'] == REPO
                            and any(p['number'] == pr['number'] for p in r['pull_requests'])]
                run = matching[0] if matching else None
            if run:
                jobs = api(f"actions/runs/{run['id']}/jobs?per_page=100")['jobs']
                tv = [j for j in jobs if j['name'] == 'Android TV - APK optimisé (identité existante)']
                if tv and tv[0]['conclusion'] == 'success':
                    ready = run
                    break
                if tv and tv[0]['conclusion'] not in (None, 'success'):
                    raise RuntimeError(f'{label} TV build failed')
            time.sleep(10)
        if not ready:
            raise RuntimeError(f'{label} exact APK not ready')
        folder = Path('/tmp/kv-benchmark-apks') / label
        subprocess.run(['gh', 'run', 'download', str(ready['id']), '--repo', REPO,
                        '--name', 'kovelyx-tv-debug', '--dir', str(folder)], check=True)
        apks = list(folder.glob('*universal*.apk'))
        assert len(apks) == 1
        tools = sorted((Path(os.environ['ANDROID_HOME']) / 'build-tools').iterdir(),
                       key=lambda p: tuple(int(n) for n in p.name.split('.') if n.isdigit()))[-1]
        subprocess.run([str(tools / 'apksigner'), 'verify', str(apks[0])], check=True)
        badging = subprocess.check_output([str(tools / 'aapt'), 'dump', 'badging', str(apks[0])], text=True)
        assert "package: name='com.kovelyx.tv.debug'" in badging
        assert 'application-debuggable' not in badging
        apks[0].rename(folder / 'app.apk')
        proofs[label] = dict(run_id=ready['id'], source_sha=ready['head_sha'])
    Path('/tmp/kv-benchmark-apks/provenance.json').write_text(json.dumps(proofs, indent=2))


if __name__ == '__main__':
    main()
