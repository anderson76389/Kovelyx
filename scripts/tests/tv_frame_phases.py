#!/usr/bin/env python3
"""Separate UI-phase elapsed time from render/presentation waits in Android framestats.

Uses named timestamps, ignores unsupported GPU histogram buckets and rejects
incomplete/sentinel rows. This does not identify an individual Compose node.
"""
import argparse
import json
import math
from pathlib import Path
import statistics

TIMES = ('IntendedVsync', 'HandleInputStart', 'SyncQueued',
         'IssueDrawCommandsStart', 'SwapBuffers', 'FrameCompleted')
PHASES = {
    'total': ('IntendedVsync', 'FrameCompleted'),
    'ui': ('HandleInputStart', 'SyncQueued'),
    'render_queue': ('SyncQueued', 'IssueDrawCommandsStart'),
    'draw_submission': ('IssueDrawCommandsStart', 'SwapBuffers'),
    'after_swap': ('SwapBuffers', 'FrameCompleted'),
}


def analyse(raw):
    header = None
    seen = set()
    rejected = 0
    timings = {name: [] for name in PHASES}
    for line in raw.splitlines():
        if line.startswith('Flags,'):
            header = line.rstrip(',').split(',')
            continue
        if not header or not line[:1].isdigit() or ',' not in line:
            continue
        try:
            row = {key: int(value) for key, value in zip(header, line.split(','))}
            if row['Flags'] != 0:
                continue
            values = [row[key] for key in TIMES]
            if not all(0 < value < 10**18 for value in values) or values != sorted(values):
                rejected += 1
                continue
        except (ValueError, KeyError):
            rejected += 1
            continue
        key = (row['IntendedVsync'], row['FrameCompleted'])
        if key in seen:
            continue
        seen.add(key)
        for name, (start, end) in PHASES.items():
            timings[name].append((row[end] - row[start]) / 1_000_000)
    if not seen:
        raise ValueError('No complete regular frame timestamps')
    return dict(valid_frames=len(seen), rejected_frames=rejected,
                phases={name: dict(p50_ms=round(statistics.median(values), 3),
                                   p95_ms=round(sorted(values)[math.ceil(len(values) * .95) - 1], 3))
                        for name, values in timings.items()})


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--root', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    reports = {p.parent.name: analyse(p.read_text()) for p in sorted(args.root.glob('*/gfxinfo.txt'))}
    if not reports:
        raise ValueError('No frame dumps found')
    result = dict(source_workflow=42837729850, renderer='SwiftShader software GPU, confirmed in workflow log',
                  limitations=['Guest navigation only', 'Not physical-TV GPU measurements',
                               'after_swap includes synchronization/presentation; it is not pure GPU execution time'],
                  samples=reports)
    args.output.write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    main()
