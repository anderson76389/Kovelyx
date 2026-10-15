#!/usr/bin/env bash
# Encrypted logical backup of the Kovelyx PostgreSQL database.
# Intended for a managed server/VM: configure off-site storage separately.
# Required: pg_dump, age; PostgreSQL PGHOST/PGPORT/PGDATABASE/PGUSER and
# PGPASSWORD or a protected .pgpass, plus a public age recipient.
set -euo pipefail
umask 077

: "${PGDATABASE:?PGDATABASE doit contenir le nom de la base PostgreSQL}"
: "${KOVELYX_BACKUP_DIR:?Définir un dossier de sauvegarde dédié}"
: "${KOVELYX_BACKUP_AGE_RECIPIENT:?Définir une clé publique age de chiffrement}"

command -v pg_dump >/dev/null || { echo "Erreur : pg_dump absent" >&2; exit 1; }
command -v age >/dev/null || { echo "Erreur : age absent" >&2; exit 1; }
command -v pg_restore >/dev/null || { echo "Erreur : pg_restore absent" >&2; exit 1; }

install -d -m 700 -- "$KOVELYX_BACKUP_DIR"
temp_dir="$(mktemp -d)"
trap 'rm -rf -- "$temp_dir"' EXIT
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
file="$KOVELYX_BACKUP_DIR/kovelyx-$stamp.dump.age"
if [[ -e "$file" ]]; then
  echo "Erreur : sauvegarde existante, aucun remplacement" >&2
  exit 1
fi

# Include auth.users as well as public schemas when permissions permit.
# A consistent dump includes a point-in-time snapshot; use a database user
# authorized to back up all required schemas.
pg_dump --format=custom --no-owner --no-acl --file="$temp_dir/database.dump" "$PGDATABASE"
pg_restore --list "$temp_dir/database.dump" > "$temp_dir/toc.txt"
test -s "$temp_dir/toc.txt"
age --encrypt --recipient "$KOVELYX_BACKUP_AGE_RECIPIENT" \
  --output "$temp_dir/database.dump.age" "$temp_dir/database.dump"
test -s "$temp_dir/database.dump.age"
mv -n -- "$temp_dir/database.dump.age" "$file"
test -s "$file"

printf 'Sauvegarde chiffrée créée : %s\n' "$file"
printf '%s\n' 'À faire : transférer hors du serveur et tester la restauration périodiquement.'
# No automatic deletion here: retention must follow the administrator's
# verified off-site backup policy, never accidentally erase the only copy.
