#!/usr/bin/env python3
"""Delete only pinned branches proven ancestral to main; retain live work."""
import argparse
import json
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
REPO = 'anderson76389/Kovelyx'
KEEP = {'main', 'gh-pages', 'fix/cloudflare-network-status-and-settings'}


def command(*args):
    return subprocess.check_output(args, cwd=ROOT, text=True).strip()


def api(path):
    return json.loads(command('gh', 'api', 'repos/' + REPO + '/' + path))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    assert os.environ.get('GITHUB_REPOSITORY', REPO) == REPO
    manifest = json.loads((ROOT/'scripts/maintenance/merged-branches-20261110.json').read_text())
    opened = set()
    page = 1
    while True:
        pulls = api(f'pulls?state=open&per_page=100&page={page}')
        opened.update(p['head']['ref'] for p in pulls if p['head']['repo'] and p['head']['repo']['full_name'] == REPO)
        if len(pulls) < 100:
            break
        page += 1
    active = set()
    for status in ('queued', 'in_progress', 'waiting', 'pending', 'requested'):
        runs = api(f'actions/runs?status={status}&per_page=100')
        # If pagination is needed, conservatively stop rather than miss live work.
        if runs['total_count'] > 100:
            raise RuntimeError('Too many active runs to prove cleanup safety')
        active.update(r['head_branch'] for r in runs['workflow_runs'])
    results = []
    for item in manifest:
        name, sha = item['name'], item['sha']
        state = 'retained'
        if name in KEEP or name.startswith('release/') or name in opened or name in active:
            state = 'retained-active-or-protected'
        elif subprocess.run(['git','merge-base','--is-ancestor',item.get('merge_sha', sha),'origin/main'], cwd=ROOT, capture_output=True).returncode != 0:
            state = 'retained-not-ancestral'
        else:
            if 'pr' in item:
                proof = api('pulls/' + str(item['pr']))
                if not (proof['merged'] and proof['head']['sha'] == sha
                        and proof['merge_commit_sha'] == item['merge_sha']
                        and proof['base']['ref'] == 'main'):
                    results.append({**item, 'result': 'retained-merge-proof-changed'})
                    continue
            ref = 'refs/heads/' + name
            remote = command('git','ls-remote','--heads','origin',ref)
            if not remote:
                state = 'already-absent'
            elif remote.split()[0] != sha:
                state = 'retained-head-changed'
            elif args.apply:
                # Atomic compare-and-delete: a concurrently updated branch survives.
                subprocess.run(['git','push','--force-with-lease='+ref+':'+sha,'origin',':'+ref], cwd=ROOT, check=True)
                state = 'deleted'
            else:
                state = 'would-delete'
        results.append({**item, 'result':state})
    out = ROOT/'merged-branches-cleanup-result.json'
    out.write_text(json.dumps(results, indent=2)+'\n')
    print(json.dumps(results, indent=2))


if __name__ == '__main__':
    main()
