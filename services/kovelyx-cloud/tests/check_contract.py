#!/usr/bin/env python3
"""Static release guard for the not-yet-deployed Kovelyx Cloud SQL schema.

This does NOT exercise PostgreSQL, Auth, or RLS at runtime. Integration
tests against a real test backend with two distinct JWTs remain mandatory.
"""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
file = ROOT / "supabase/migrations/202610150001_kovelyx_accounts_and_sync.sql"
sql = file.read_text(encoding="utf-8").lower()
tables = (
    "kovelyx_account_preferences",
    "kovelyx_profiles",
    "kovelyx_sync_items",
)
for table in tables:
    for fragment in (
        f"create table public.{table}",
        f"alter table public.{table} enable row level security",
        f"revoke all on public.{table} from anon",
    ):
        if fragment not in sql:
            sys.exit("SECURITY CONTRACT FAILED: " + fragment)
    for suffix in ("read", "create", "update", "delete"):
        short = {"kovelyx_account_preferences": "kv_account", "kovelyx_profiles": "kv_profile", "kovelyx_sync_items": "kv_sync"}[table]
        if f"create policy {short}_{suffix} on public.{table}" not in sql:
            sys.exit(f"SECURITY CONTRACT FAILED: {table} {suffix}")
if sql.count("account_id = (select auth.uid())") < 12:
    sys.exit("SECURITY CONTRACT FAILED: owner policies not present everywhere")
if "grant all on" in sql or "grant select on public.kovelyx_sync_items to anon" in sql:
    sys.exit("SECURITY CONTRACT FAILED: overly broad grants")
for fragment in ("on delete cascade", "kovelyx_sync_profile_fk", "profile_id uuid", "revision bigint", "131072"):
    if fragment not in sql:
        sys.exit("SECURITY CONTRACT FAILED: " + fragment)
print("OK: checks for RLS, user ownership, profile isolation, size limit.")
print("NOTICE: requires live PostgreSQL and two-user negative security tests before launch.")
