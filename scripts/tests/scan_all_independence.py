#!/usr/bin/env python3
"""Exhaustive tracked-tree inventory, including native bytes and zipped members.

Scans current Git checkout, not erased Git history, external binaries fetched at
build time, dynamically assembled URLs or actual device traffic. Reports never
print source lines or credentials: only file paths, line numbers and match class.
"""
from __future__ import annotations

from collections import Counter, defaultdict
from pathlib import Path
from fnmatch import fnmatch
import argparse
import json
import re
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[2]
ACCOUNT = re.compile(rb"(?i)(?:[a-z0-9-]+\.)*(?:nuvio\.tv|nuvioapp\.space|supabase\.co)")
OLD_REPO = re.compile(rb"(?i)(?:github\.com|raw\.githubusercontent\.com|api\.github\.com/repos)/NuvioMedia")
ACCOUNT_SDK = re.compile(rb"(?i)(?:io\.github\.jan\.supabase|libs\.supabase|SupabaseClient|SupabaseProvider|SUPABASE_URL|SUPABASE_KEY|Postgrest)")
NAME = re.compile(r"(?i)nuvio|supabase")
TEXT_SUFFIXES = frozenset((".kt",".kts",".java",".xml",".json",".toml",".sh",".ps1",".bat",".cmd",".py",".js",".mjs",
                          ".ts",".tsx",".swift",".plist",".html",".css",".md",".txt",".yml",".yaml",".gradle",
                          ".properties",".pro",".h",".c",".cc",".cpp",".def",".cmake",".xcconfig",".sql"))
ARCHIVE_SUFFIXES = frozenset((".zip",".jar",".aar",".apk"))
ARCHIVE_SCAN_SUFFIXES = TEXT_SUFFIXES | frozenset((".class",".dex",".so",".dll",".dylib",".jar",".aar",".apk",".zip",".exe"))
MAX_ARCHIVE_MEMBER = 175_000_000
MAX_ARCHIVE_UNCOMPRESSED = 800_000_000
BUFFER = 2**17

def tracked():
    proc = subprocess.check_output(["git","ls-files","-s","-z"],cwd=ROOT)
    entries = []
    for record in proc.split(b"\0"):
        if not record: continue
        props, path = record.split(b"\t", 1)
        mode = props.decode().split()[0]
        entries.append((mode,path.decode("utf-8","surrogateescape")))
    return entries

def platform(path):
    if path.startswith("apps/tv/"): return "Android TV"
    if path.startswith("apps/mobile/"): return "Android mobile"
    if path.startswith("apps/desktop/"): return "Windows/Desktop"
    if path.startswith("services/"): return "Cloudflare Worker"
    if path.startswith("vendor/"): return "Vendored engine"
    if path.startswith(".github/"): return "GitHub automation"
    return "Repository tooling"

def zone(path):
    if path.startswith("docs/") or path.endswith(".md") or path.startswith("apps/mobile/Docs/"):
        return "documentation"
    if ("/test" in path.lower() or "/androidtest/" in path.lower() or path.startswith("tests/")
        or "/examples/" in path.lower() or "test_" in Path(path).name.lower()):
        return "test"
    if path.startswith("vendor/") or "/MPVKit/" in path:
        return "vendored"
    if "/src/main/" in path or "/src/commonMain/" in path or "/src/androidMain/" in path or \
       "/src/desktopMain/" in path or "/src/androidFull/" in path or "/src/fullCommonMain/" in path:
        return "runtime"
    if path.endswith((".gradle",".gradle.kts")) or "/scripts/" in path or path.startswith("scripts/") or \
       path.startswith(".github/workflows/") or path.endswith(".toml"):
        return "build-and-ci"
    return "other"

def find_bytes(file, regexes):
    # Streaming scan of EVERY tracked file, including images, native libs and archives.
    matched = defaultdict(int)
    with file.open("rb") as handle:
        prefix = b""
        while True:
            chunk = handle.read(BUFFER)
            if not chunk: break
            data = prefix + chunk
            for key, pattern in regexes.items():
                matched[key] += sum(1 for m in pattern.finditer(data) if m.end() > len(prefix))
            prefix = data[-160:]
    return dict(matched)

def scan_zip(path, hits, errors, depth=0):
    # Inspect relevant executable/manifest members, never assume APKs are ordinary text.
    if depth > 1: return
    try:
        with zipfile.ZipFile(path) as z:
            total = 0
            for entry in z.infolist():
                if entry.is_dir(): continue
                total += entry.file_size
                if entry.file_size > MAX_ARCHIVE_MEMBER or total > MAX_ARCHIVE_UNCOMPRESSED:
                    errors.append(f"Skipped oversized member: {str(path)}!{entry.filename}")
                    break
                if Path(entry.filename).suffix.lower() not in ARCHIVE_SCAN_SUFFIXES:
                    continue
                if NAME.search(entry.filename):
                    hits["old_archive_member"].append(f"{path}!{entry.filename}"[:250])
                if entry.file_size > 0:
                    with z.open(entry) as stream:
                        prefix=b""
                        while True:
                            buf=stream.read(BUFFER)
                            if not buf: break
                            data=prefix+buf
                            for key,regex in {"legacy_host":ACCOUNT, "upstream_repo":OLD_REPO}.items():
                                if regex.search(data):
                                    hits[key].append(f"{path}!{entry.filename}"[:250])
                                    break
                            prefix=data[-160:]
    except (OSError,zipfile.BadZipFile,RuntimeError,ValueError,NotImplementedError) as exc:
        errors.append(f"Unreadable archive {path}: {type(exc).__name__}")

def main():
    p=argparse.ArgumentParser()
    p.add_argument("--out",default="/tmp/kovelyx-independent-repo-audit.json")
    p.add_argument("--release-path", action="append",default=[],help="Downloaded release artifacts to inspect as well")
    args=p.parse_args()
    entries=tracked()
    paths={name for _,name in entries}
    hits=defaultdict(list)
    counts=Counter()
    binary_counts=Counter()
    errors=[]
    critical=[]
    by_zone=Counter()
    sample=defaultdict(list)
    for mode,name in entries:
        file=ROOT/name
        counts[platform(name)]+=1
        by_zone[zone(name)]+=1
        if mode=="160000":
            hits["submodule_gitlink"].append(name)
            continue
        if not file.is_file() and not file.is_symlink():
            errors.append(f"Tracked file missing from checkout: {name}")
            continue
        if NAME.search(name):
            hits["old_filename"].append(name)
        if file.is_symlink():
            hits["symlink"].append(name)
            continue
        size=file.stat().st_size
        counts["total_bytes"]+=size
        kind=zone(name)
        if file.suffix.lower() in (".dll",".so",".dylib",".aar",".jar",".zip",".apk",".exe",".msi"):
            binary_counts[platform(name)]+=1
        found=find_bytes(file,{"legacy_host":ACCOUNT,"upstream_repo":OLD_REPO,"retired_sdk":ACCOUNT_SDK})
        for category,num in found.items():
            if num:
                by_zone[f"{kind}:{category}"]+=num
                hits[f"{category}:{kind}"].append(name)
        if kind=="runtime" and found.get("retired_sdk",0):
            critical.append("Retired account SDK in runtime code/binary: "+name)
        if kind=="runtime" and found.get("legacy_host",0):
            # Explicit denylist code is an intentional rejection, not a connection.
            if name.endswith("ServerDiscovery.kt"):
                hits["reject_only_domains"].append(name)
            else:
                critical.append("Legacy account host in runtime (inspect whether executable): "+name)
        if file.suffix.lower() in ARCHIVE_SUFFIXES:
            scan_zip(file,hits,errors)
        if file.suffix.lower() in TEXT_SUFFIXES and size < 10_000_000:
            lines=file.read_text(encoding="utf-8",errors="replace").splitlines()
            for i,line in enumerate(lines,1):
                labels=[]
                if re.search(r"(?i)(?:nuvio\.tv|nuvioapp\.space|supabase\.co)",line): labels.append("host")
                if re.search(r"(?i)(?:github\.com|githubusercontent\.com)/NuvioMedia",line): labels.append("upstream-repository")
                if re.search(r"(?i)(?:SupabaseClient|io\.github\.jan\.supabase|Postgrest|supabase\.co)",line): labels.append("retired-account-sdk")
                if re.search(r"(?i)(?:nuvio_engine|NUVIO_[A-Z_]+|com\.nuvio)",line): labels.append("compatibility-or-native-abi")
                for label in labels:
                    if len(sample[(kind,label)]) < 30:
                        sample[(kind,label)].append(f"{name}:{i}")
    # The current independent clients must not contain direct Git submodules.
    for mode,name in entries:
        if mode=="160000" and name.startswith(("apps/","vendor/")):
            critical.append("Unvendored git submodule: "+name)
    artifacts=[]
    for arg in args.release_path:
        f=Path(arg)
        if not f.is_file():
            errors.append("Missing release artifact: "+str(f))
            continue
        record={"name":f.name,"size":f.stat().st_size,
                "legacy":find_bytes(f,{"legacy_host":ACCOUNT,"upstream_repo":OLD_REPO,"retired_sdk":ACCOUNT_SDK})}
        artifacts.append(record)
        if f.suffix.lower() in ARCHIVE_SUFFIXES: scan_zip(f,hits,errors)
    report={
      "head":subprocess.check_output(["git","rev-parse","HEAD"],cwd=ROOT,text=True).strip(),
      "tracked_files":len(entries),"files_by_area":dict(counts),"files_by_zone":dict(by_zone),
      "binary_packages_by_area":dict(binary_counts),
      "counts":{k:len(v) for k,v in hits.items()},
      "sample_files":{k:v[:45] for k,v in hits.items()},
      "line_samples":{f"{key[0]} / {key[1]}":v for key,v in sample.items()},
      "external_build_dependencies_note":"Gradle, JDK, MPV, Media3, GitHub releases, Cloudflare, TMDB, Trakt, Simkl, MDBList, user-installed addons remain third-party dependencies. This is not an offline independence proof.",
      "historic_git_note":"Only current tracked tree. Historical commits/tags/releases remain accessible.",
      "released_artifacts":artifacts,"critical_candidates":critical,"unscanned_or_errors":errors,
    }
    out=Path(args.out);out.parent.mkdir(parents=True,exist_ok=True)
    out.write_text(json.dumps(report,indent=2,ensure_ascii=False)+"\n",encoding="utf-8")
    print(f"HEAD {report['head']}")
    print(f"FILES {len(entries)} (working checkout), {sum(binary_counts.values())} tracked archive/native binaries")
    print("CATEGORIES:",json.dumps(report["counts"],ensure_ascii=False,sort_keys=True))
    print("OLD_FILENAMES examples:")
    for item in hits.get("old_filename",[])[:24]: print(" ",item)
    print("ZONE_TOTALS:",json.dumps({k:v for k,v in by_zone.items() if ":" in k},sort_keys=True))
    print("CRITICAL_CANDIDATES",len(critical))
    for item in critical[:30]: print("  REVIEW:",item)
    print("SCAN_ERRORS",len(errors))
    for item in errors[:15]: print("  ERROR:",item)
    print("RELEASE_ARTIFACTS",json.dumps(artifacts,ensure_ascii=False))
    print("Full non-sensitive report:",out)
    return 1 if errors else 0

if __name__=="__main__":
    sys.exit(main())
