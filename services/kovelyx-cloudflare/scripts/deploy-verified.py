"""Deploy profile media only after verification of the owner's recovered D1 backup."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import time
import urllib.request
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT.parents[1]
TABLES = ('user', 'account', 'kv_profiles', 'kv_sync', 'kv_profile_media', 'kv_profile_media_chunks')


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def main():
    # Owner-authorized deployment after backup run 41244593115 was recovered and
    # restored outside GitHub. No private backup metadata is published here.
    account = os.environ.get('CLOUDFLARE_ACCOUNT_ID', '').strip()
    database = os.environ.get('KOVELYX_D1_DATABASE_ID', '').strip()
    token = os.environ.get('CLOUDFLARE_API_TOKEN', '').strip()
    require(token and re.fullmatch('[0-9a-fA-F]{32}', account) and re.fullmatch(r'[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}', database), 'Accès Cloudflare incomplet.')
    for path, expected in [
        ('package-lock.json', '6b12cf978aa050408737b8b7ffabd38b5bdf67a0ec9aa8d17ff6e48aac49f05f'),
        ('migrations/0003_profile_media.sql', 'cb74e4326ad512454147181df31074a8d0ea0ed3ec7f82ad3d4ebca7afa42256'),
        ('migrations/0004_profile_media_gifs.sql', '10ef60499bfbfcf37cdda86c607a4f1960aead25ce375b0b7a7163595f24d31c'),
    ]:
        require(hashlib.sha256((ROOT/path).read_bytes()).hexdigest() == expected, 'Empreinte des fichiers publics différente des versions vérifiées.')
    backup_dir = Path(os.environ['BACKUP_OUTPUT_DIR'])
    backup = json.loads((backup_dir/'manifest.json').read_text())
    require(backup.get('source_sha') == os.environ.get('GITHUB_SHA') and backup.get('run_id') == os.environ.get('GITHUB_RUN_ID'), 'Sauvegarde du déploiement courant absente.')
    require(backup.get('target_fingerprint') == hashlib.sha256((account.lower()+':'+database.lower()).encode()).hexdigest(), 'Cible de sauvegarde différente.')
    require(0 <= (datetime.now(timezone.utc)-datetime.fromisoformat(backup['created_at'])).total_seconds() < 1800, 'Sauvegarde trop ancienne.')
    require(hashlib.sha256((backup_dir/'kovelyx-d1.sql.cms').read_bytes()).hexdigest() == backup.get('encrypted_sha256'), 'Intégrité de la copie chiffrée non confirmée.')
    base = 'https://api.cloudflare.com/client/v4/accounts/'+account

    def api(path, payload=None):
        request = urllib.request.Request(base+path, data=None if payload is None else json.dumps(payload).encode(), headers={'Authorization':'Bearer '+token, 'User-Agent':'Kovelyx-verified-deployment', 'Content-Type':'application/json'})
        with urllib.request.urlopen(request, timeout=40) as response:
            result = json.load(response)
        require(result.get('success'), 'Vérification Cloudflare refusée.')
        return result['result']

    def bindings():
        entries = api('/workers/scripts/kovelyx-cloud/settings').get('bindings', [])
        require([str(b.get('database_id') or b.get('id') or '').lower() for b in entries if b.get('name') == 'DB' and b.get('type') == 'd1'] == [database.lower()], 'Liaison D1 du Worker différente.')
        require(any(b.get('name') == 'BETTER_AUTH_SECRET' and b.get('type') == 'secret_text' for b in entries), 'Secret Better Auth absent.')
        return {b['name']:b for b in entries if b.get('type') in ('plain_text','json','secret_text')}

    def query(sql):
        result = api('/d1/database/'+database+'/query', {'sql':sql})
        require(len(result) == 1 and result[0].get('success'), 'Contrôle D1 refusé.')
        return result[0].get('results', [])

    original_bindings = bindings()
    migrations = {r['name'] for r in query('SELECT name FROM d1_migrations')}
    previous = {'0001_accounts_and_sync.sql', '0002_watched_history.sql', '0003_profile_media.sql'}
    require(previous.issubset(migrations), 'Les migrations 0001 à 0003 doivent déjà être enregistrées.')
    allowed = previous | {'0004_profile_media_gifs.sql'}
    require(migrations.issubset(allowed), 'Historique D1 inattendu ; aucune migration appliquée.')
    require({p.name for p in (ROOT/'migrations').glob('*.sql')} == allowed, 'Liste des migrations inattendue.')
    before = {table:query('SELECT count(*) AS total FROM "'+table+'"')[0]['total'] for table in TABLES}
    print('OK : sauvegarde récupérée, cible D1, migrations précédentes et secret existant vérifiés.', flush=True)
    config = ROOT/'.wrangler-verified-deploy.toml'
    try:
        config.write_text((ROOT/'wrangler.toml').read_text().replace('REPLACE_WITH_YOUR_D1_DATABASE_ID', database))
        config.chmod(0o600)
        def run(args, message):
            result = subprocess.run(['npx','--no-install','wrangler',*args,'--config',str(config)], cwd=ROOT, capture_output=True)
            require(result.returncode == 0, message+' a échoué ; sortie privée masquée.')
        run(['d1','migrations','apply','DB','--remote'], 'Migration 0004 avec conservation des images')
        require('0004_profile_media_gifs.sql' in {r['name'] for r in query('SELECT name FROM d1_migrations')}, 'Migration 0004 non enregistrée.')
        require(not query('PRAGMA foreign_key_check'), 'Références D1 invalides après migration.')
        print('OK : migration 0004 enregistrée, références vérifiées.', flush=True)
        run(['deploy','--keep-vars'], 'Déploiement du Worker')
        print('OK : Worker publié en conservant les variables et secrets existants.', flush=True)
    finally:
        config.unlink(missing_ok=True)
    current_bindings = bindings()
    require(all(current_bindings.get(name) == value for name,value in original_bindings.items()), 'Les réglages existants doivent être vérifiés.')
    after = {table:query('SELECT count(*) AS total FROM "'+table+'"')[0]['total'] for table in TABLES}
    require(all(after[t] >= before[t] for t in TABLES), 'Variation du nombre de comptes/profils : contrôle manuel nécessaire.')
    require({r['name'] for r in query("SELECT name FROM sqlite_master WHERE type='table' AND name IN ('kv_profile_media','kv_profile_media_chunks')")} == {'kv_profile_media','kv_profile_media_chunks'}, 'Tables des images absentes.')
    for attempt in range(6):
        try:
            req = urllib.request.Request('https://kovelyx-cloud.mailpromahe.workers.dev/ready', headers={'User-Agent':'Kovelyx-verified-deployment','Accept':'application/json'})
            with urllib.request.urlopen(req, timeout=25) as response:
                ready = json.load(response)
            if ready.get('status') == 'ok' and ready.get('schema') == '0004_profile_media_gifs' and ready.get('maxProfileMediaBytes') == 18_000_000:
                break
        except Exception:
            pass
        time.sleep(3)
    else:
        raise RuntimeError('Worker publié mais /ready 0004 non confirmé ; vérifier la production.')
    print('OK : serveur réel prêt avec schéma 0004 et images privées de 18 Mo ; comptes, profils, images, liaison D1 et réglages conservés.', flush=True)


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print('::error::'+(str(error) if isinstance(error, RuntimeError) else type(error).__name__))
        raise SystemExit(1)
