#!/usr/bin/env python3
"""Create an OFFLINE, private SQLite database for the portable Kovelyx server.

Never touches a Cloudflare account, D1, or an existing database. A D1 export
must be verified before import; plaintext SQL should only live on the owner's
machine in a temporary private directory.
"""
from __future__ import annotations

import argparse
import os
from pathlib import Path
import sqlite3
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from backup_validation import REQUIRED_TABLES, validate_export

MIGRATIONS = ROOT / "migrations"


def check_database(db: sqlite3.Connection, expected: dict | None = None) -> dict:
    if db.execute("PRAGMA integrity_check").fetchone() != ("ok",):
        raise ValueError("SQLite integrity check failed")
    if db.execute("PRAGMA foreign_key_check").fetchone() is not None:
        raise ValueError("SQLite foreign key check failed")
    tables = {row[0] for row in db.execute(
        "SELECT name FROM sqlite_master WHERE type='table'")}
    if not set(REQUIRED_TABLES).issubset(tables):
        raise ValueError("Missing account/profile/media/PIN tables")
    counts = {name: db.execute(
        'SELECT count(*) FROM "' + name + '"').fetchone()[0]
        for name in REQUIRED_TABLES}
    if expected is not None and counts != expected:
        raise ValueError("Restored table counts differ from verified export")
    return counts


def create_database(output: Path, sql_path: Path | None = None) -> dict:
    output = output.absolute()
    if not output.parent.is_dir():
        raise ValueError("Output directory does not exist")
    expected = None
    if sql_path is not None:
        # Validate in memory before writing any persistent private data.
        expected = validate_export(sql_path)["tables"]
    flags = os.O_CREAT | os.O_EXCL | os.O_RDWR
    if hasattr(os, "O_NOFOLLOW"):
        flags |= os.O_NOFOLLOW
    fd = os.open(output, flags, 0o600)
    os.close(fd)
    try:
        with sqlite3.connect(output) as db:
            # D1 migrations 0002/0004 replace parent tables: defer FK checks
            # until the full migration sequence has completed.
            db.execute("PRAGMA foreign_keys=OFF")
            if sql_path is not None:
                db.executescript(sql_path.read_text(encoding="utf-8"))
            else:
                for migration in sorted(MIGRATIONS.glob("*.sql")):
                    db.executescript(migration.read_text(encoding="utf-8"))
            db.execute("PRAGMA foreign_keys=ON")
            counts = check_database(db, expected)
            db.commit()
        return counts
    except BaseException:
        output.unlink(missing_ok=True)
        raise


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Prepare a private SQLite copy without altering Cloudflare production")
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--empty", action="store_true",
                        help="Create a new isolated database from checked-in migrations")
    source.add_argument("--from-sql", type=Path,
                        help="Import a verified decrypted D1 SQL export OFFLINE")
    parser.add_argument("--output", type=Path, required=True,
                        help="Path to a NEW database file (existing files are refused)")
    args = parser.parse_args()
    try:
        if args.from_sql and not args.from_sql.is_file():
            raise ValueError("SQL export file not found")
        counts = create_database(args.output, args.from_sql)
        print("PASS: portable database prepared and checked; " +
              str(len(counts)) + " required tables verified. No remote access.")
    except Exception as error:
        print("FAIL: portable database not prepared (" +
              type(error).__name__ + ").", file=sys.stderr)
        raise SystemExit(1)


if __name__ == "__main__":
    main()
