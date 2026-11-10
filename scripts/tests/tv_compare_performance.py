#!/usr/bin/env python3
"""Compare two exact APKs on one disposable emulator; never touch a real device.

Three interleaved repetitions measure cold activity launch, PSS, CPU and frames.
This guest-navigation workload does not measure 4K playback or network addons.
"""
import argparse
import json
from pathlib import Path
import re
import statistics
import subprocess
import time

PACKAGE = 'com.kovelyx.tv.debug'


def adb(*args, check=True):
    return subprocess.run(['adb', *args], text=True, capture_output=True,
                          timeout=120, check=check).stdout.strip()


def cpu_samples(raw, pid):
    index = None
    values = []
    for line in raw.splitlines():
        cols = line.replace('[', ' ').replace(']', ' ').split()
        if 'PID' in cols:
            index = next((i for i, c in enumerate(cols)
                          if c.strip('[]').replace('%', '') == 'CPU'), None)
        elif index is not None and cols and cols[0] == pid:
            values.append(float(cols[index].strip('%')))
    # First top sample is not a timed interval. Require subsequent samples.
    if len(values) < 2:
        raise AssertionError('No interval CPU samples from Android top')
    return values[1:]


def measure(label, apk, folder):
    folder.mkdir(parents=True)
    # Clean installation is only for benchmark isolation, NOT an OTA test.
    adb('uninstall', PACKAGE, check=False)
    adb('install', str(apk))
    adb('logcat', '-c')
    adb('shell', 'monkey', '-p', PACKAGE, '-c',
        'android.intent.category.LEANBACK_LAUNCHER', '1')
    time.sleep(10)
    subprocess.run(['python3', 'scripts/tests/tv_guest_smoke.py'], check=True, timeout=180)
    activity = adb('shell', 'cmd', 'package', 'resolve-activity', '--brief',
                   '-a', 'android.intent.action.MAIN', '-c',
                   'android.intent.category.LEANBACK_LAUNCHER', PACKAGE).splitlines()[-1]
    if not activity.startswith(PACKAGE + '/'):
        raise AssertionError('Unexpected TV activity: ' + activity)
    starts = []
    for n in range(3):
        adb('shell', 'am', 'force-stop', PACKAGE)
        out = adb('shell', 'am', 'start', '-W', '-n', activity)
        (folder / f'launch-{n}.txt').write_text(out)
        match = re.search(r'^TotalTime:\s*(\d+)', out, re.M)
        if not match:
            raise AssertionError('Cold launch timing missing')
        starts.append(int(match.group(1)))
        time.sleep(2)
    pid = adb('shell', 'pidof', PACKAGE)
    if not pid.isdigit():
        raise AssertionError('App process missing or ambiguous')
    idle = adb('shell', 'top', '-b', '-n', '4', '-d', '1', '-p', pid, '-o', 'PID,%CPU')
    (folder / 'idle-cpu.txt').write_text(idle)
    adb('shell', 'dumpsys', 'gfxinfo', PACKAGE, 'reset')
    top = subprocess.Popen(['adb', 'shell', 'top', '-b', '-n', '12', '-d', '1', '-p', pid, '-o', 'PID,%CPU'],
                           stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    try:
        for n in range(32):
            adb('shell', 'input', 'keyevent',
                ('KEYCODE_DPAD_RIGHT', 'KEYCODE_DPAD_DOWN',
                 'KEYCODE_DPAD_LEFT', 'KEYCODE_DPAD_UP')[n % 4])
            time.sleep(0.25)
        raw, err = top.communicate(timeout=60)
        if top.returncode:
            raise AssertionError(err)
    finally:
        if top.poll() is None:
            top.kill()
            top.communicate()
    (folder / 'navigation-cpu.txt').write_text(raw)
    mem = adb('shell', 'dumpsys', 'meminfo', PACKAGE)
    gfx = adb('shell', 'dumpsys', 'gfxinfo', PACKAGE, 'framestats')
    (folder / 'meminfo.txt').write_text(mem)
    (folder / 'gfxinfo.txt').write_text(gfx)
    pss = re.search(r'^\s*TOTAL PSS:\s*([\d,]+)', mem, re.M)
    frames = re.search(r'Total frames rendered:\s*(\d+)', gfx)
    janky = re.search(r'Janky frames:\s*(\d+)', gfx)
    if not pss or not frames or not janky:
        raise AssertionError('Missing PSS or frame metrics')
    events = adb('logcat', '-d', '-b', 'events', '-v', 'brief')
    (folder / 'events.txt').write_text(events)
    if adb('shell', 'pidof', PACKAGE) != pid or re.search(
            r'(?:am_crash|am_anr).*com\.kovelyx\.tv\.debug', events, re.I):
        raise AssertionError('App restarted or crashed during benchmark')
    return dict(label=label, apk_bytes=apk.stat().st_size,
                cold_launch_ms=statistics.median(starts),
                pss_kib=int(pss.group(1).replace(',', '')),
                idle_cpu_percent=statistics.median(cpu_samples(idle, pid)),
                navigation_cpu_percent=statistics.median(cpu_samples(raw, pid)),
                frames=int(frames.group(1)), janky_frames=int(janky.group(1)))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--baseline', type=Path, required=True)
    parser.add_argument('--candidate', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if adb('shell', 'getprop', 'ro.kernel.qemu') != '1':
        raise AssertionError('Benchmark may run only on a disposable emulator')
    args.output.mkdir(parents=True, exist_ok=True)
    samples = []
    report = dict(environment='One Google TV API 34 emulator, 2 GiB RAM',
                  workload='Clean guest mode, cold launch, idle, 32 DPAD events',
                  limitations=['No physical-device claim', 'No playback or poster-network workload',
                               'Three repetitions; small differences may be noise'], samples=samples)
    try:
        for n, label in enumerate(('baseline', 'candidate', 'candidate', 'baseline', 'baseline', 'candidate')):
            samples.append(measure(label, getattr(args, label), args.output / f'{n}-{label}'))
            print(json.dumps(samples[-1]), flush=True)
        metrics = ('apk_bytes', 'cold_launch_ms', 'pss_kib', 'idle_cpu_percent', 'navigation_cpu_percent')
        report['medians'] = {label: {key: statistics.median(s[key] for s in samples if s['label'] == label)
                                    for key in metrics} for label in ('baseline', 'candidate')}
        report['regressions'] = []
        # Broad bounds avoid treating minor emulator variation as a real gain/regression.
        for key, relative, absolute in (('cold_launch_ms', 0.30, 200),
                                        ('pss_kib', 0.25, 32768),
                                        ('idle_cpu_percent', 0.30, 5),
                                        ('navigation_cpu_percent', 0.30, 5)):
            a, b = (report['medians'][label][key] for label in ('baseline', 'candidate'))
            if b - a > max(a * relative, absolute):
                report['regressions'].append(key)
        print(json.dumps(report, indent=2), flush=True)
        if report['regressions']:
            raise AssertionError('Performance regression requires investigation: ' + str(report['regressions']))
    finally:
        (args.output / 'report.json').write_text(json.dumps(report, indent=2) + '\n')


if __name__ == '__main__':
    main()
