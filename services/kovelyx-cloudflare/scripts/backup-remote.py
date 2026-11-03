"""Export the existing D1 database and publish only an authenticated encrypted copy.

The recovery private key is never available to this script or to GitHub Actions.
"""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
from datetime import datetime, timezone
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
from backup_validation import validate_export


def run_private(command, label):
    result = subprocess.run(command, cwd=ROOT, capture_output=True)
    if result.returncode:
        # Wrangler can return a signed SQL export URL. Never send its output to public logs.
        raise RuntimeError(label + ' a échoué (sortie masquée pour protéger les données).')


def main():
    account = os.environ.get('CLOUDFLARE_ACCOUNT_ID', '').strip()
    database = os.environ.get('KOVELYX_D1_DATABASE_ID', '').strip()
    token = os.environ.get('CLOUDFLARE_API_TOKEN', '').strip()
    if not token or not re.fullmatch('[0-9a-fA-F]{32}', account) or not re.fullmatch(r'[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}', database):
        raise RuntimeError('Accès Cloudflare incomplet.')
    request = urllib.request.Request('https://api.cloudflare.com/client/v4/accounts/' + account + '/workers/scripts/kovelyx-cloud/settings',
        headers={'Authorization': 'Bearer ' + token, 'User-Agent': 'Kovelyx-private-backup', 'Accept': 'application/json'})
    with urllib.request.urlopen(request, timeout=25) as response:
        body = json.load(response)
    if not body.get('success'):
        raise RuntimeError('Métadonnées du Worker non confirmées.')
    bindings = body['result'].get('bindings', [])
    bound = [str(b.get('database_id') or b.get('id') or '').lower() for b in bindings if b.get('name') == 'DB' and b.get('type') == 'd1']
    if bound != [database.lower()]:
        raise RuntimeError('La cible D1 ne correspond pas au Worker existant.')
    if not any(b.get('name') == 'BETTER_AUTH_SECRET' and b.get('type') == 'secret_text' for b in bindings):
        raise RuntimeError('Le secret Better Auth existant n’est pas confirmé ; aucune modification.')

    out = Path(os.environ['BACKUP_OUTPUT_DIR'])
    out.mkdir(mode=0o700, parents=True, exist_ok=True)
    certificate = ROOT.parents[1] / '.github/cloudflare-backup-recipient.pem'
    config = ROOT / '.wrangler-private-backup.toml'
    try:
        config.write_text((ROOT / 'wrangler.toml').read_text().replace('REPLACE_WITH_YOUR_D1_DATABASE_ID', database))
        config.chmod(0o600)
        with tempfile.TemporaryDirectory(prefix='kovelyx-private-d1-', dir=os.environ.get('RUNNER_TEMP')) as directory:
            sql = Path(directory) / 'backup.sql'
            run_private(['npx', '--no-install', 'wrangler', 'd1', 'export', 'DB', '--remote', '--skip-confirmation', '--output', str(sql), '--config', str(config)], 'Export D1')
            sql.chmod(0o600)
            encrypted = out / 'kovelyx-d1.sql.cms'
            run_private(['openssl', 'cms', '-encrypt', '-binary', '-aes-256-gcm', '-in', str(sql), '-out', str(encrypted), '-outform', 'DER', str(certificate)], 'Chiffrement de la sauvegarde')
            signing_bundle = os.environ.get('KOVELYX_ANDROID_SIGNING_BUNDLE', '')
            signing_saved = False
            if signing_bundle:
                signing = Path(directory) / 'android-signing-bundle.txt'
                signing.write_text(signing_bundle, encoding='utf-8')
                signing.chmod(0o600)
                run_private(['openssl', 'cms', '-encrypt', '-binary', '-aes-256-gcm', '-in', str(signing), '-out', str(out / 'android-signing-bundle.cms'), '-outform', 'DER', str(certificate)], 'Sauvegarde chiffrée de la signature Android')
                signing.unlink()
                signing_saved = True
            validation_error = None
            counts = None
            try:
                counts = validate_export(sql)
            except Exception as error:
                validation_error = type(error).__name__
            manifest = {
                'format': 'CMS-AES-256-GCM-RSA4096',
                'android_signing_bundle_saved': signing_saved,
                'better_auth_secret_exported': False,
                'created_at': datetime.now(timezone.utc).isoformat(),
                'source_sha': os.environ.get('GITHUB_SHA'),
                'run_id': os.environ.get('GITHUB_RUN_ID'),
                'target_fingerprint': hashlib.sha256((account.lower() + ':' + database.lower()).encode()).hexdigest(),
                'sql_sha256': hashlib.sha256(sql.read_bytes()).hexdigest(),
                'encrypted_sha256': hashlib.sha256(encrypted.read_bytes()).hexdigest(),
                'certificate_sha256': hashlib.sha256(certificate.read_bytes()).hexdigest(),
                'counts': counts,
                'validation_status': 'failed' if validation_error else 'passed',
                'validation_error_type': validation_error,
            }
            lock = ROOT / 'package-lock.json'
            if lock.is_file():
                (out / 'package-lock.json').write_bytes(lock.read_bytes())
                manifest['package_lock_sha256'] = hashlib.sha256(lock.read_bytes()).hexdigest()
            (out / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
            if validation_error:
                raise RuntimeError('Copie chiffrée conservée, mais validation de récupération échouée ; ne pas la considérer comme une sauvegarde validée.')
            print('Export SQL restauré en mémoire : intégrité, références, médias et catalogues vérifiés.')
            print('Copie chiffrée créée. Aucun export SQL lisible ni URL privée publié.')
            print('Aucune migration ni aucun déploiement exécuté.')
    finally:
        config.unlink(missing_ok=True)


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        # Avoid exception URLs, response bodies and SQL snippets in public build logs.
        print('::error::Sauvegarde interrompue : ' + (str(error) if isinstance(error, RuntimeError) else type(error).__name__))
        raise SystemExit(1)
