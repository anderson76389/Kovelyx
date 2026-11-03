"""Fill the owner's private stable-release draft from exact, successful CI jobs.

Does not create tags or publish a Release. The signed-in owner flow publishes
only after all ten files are present. No broader token permission is requested.
"""
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import time

REPO = 'anderson76389/Kovelyx'
SOURCE = '290decdc2d912c6a7dd056fbfa3771ef27aa802d'
TAG = 'v1.1.777'
DRAFT_ID = 430217518
ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('publisher', ROOT/'scripts/publish-verified-preview.py')
p = importlib.util.module_from_spec(spec)
spec.loader.exec_module(p)


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def release():
    r = p.api(f'releases/{DRAFT_ID}')
    require(r['tag_name'] == TAG and r['target_commitish'] == 'release/1.1.777', 'Brouillon inattendu.')
    require(p.api('git/ref/heads/release/1.1.777')['object']['sha'] == SOURCE, 'Source de publication modifiée.')
    return r


def main():
    require(os.environ.get('GITHUB_REPOSITORY') == REPO, 'Dépôt inattendu.')
    if os.environ.get('WAIT_FOR_PUBLICATION') == '1':
        for _ in range(60):
            r = release()
            if not r['draft']:
                require(not r['prerelease'] and len(r['assets']) == 10, 'Release stable complète requise.')
                ref = p.api(f'git/ref/tags/{TAG}')['object']
                require(ref['type'] == 'commit' and ref['sha'] == SOURCE, 'Tag publié différent du code testé.')
                subprocess.run(['python3','scripts/prune-releases.py'], check=True, cwd=ROOT)
                print('OK : Release stable publiée et conservation des trois dernières versions appliquée.')
                return
            time.sleep(6)
        raise RuntimeError('Brouillon complet : publication stable par la session propriétaire encore nécessaire.')
    require(release()['draft'], 'Le remplissage concerne uniquement un brouillon privé.')
    for run_id, expected_jobs in [
        (41245388962, {'Android TV - APK Full Debug','Android mobile - APK Full Debug'}),
        (41245388977, {'Compiler Kovelyx Windows'}),
        (41245388983, {'Android TV - ensemble des tests unitaires','Android mobile - tests JVM de la cible Android'}),
    ]:
        run = p.api(f'actions/runs/{run_id}')
        require(run['head_sha'] == SOURCE and run['event'] == 'push' and run['head_repository']['full_name'] == REPO, 'Provenance CI différente.')
        jobs = p.api(f'actions/runs/{run_id}/jobs?per_page=100')['jobs']
        require(expected_jobs.issubset({j['name'] for j in jobs if j['conclusion'] == 'success'}), 'Paquets ou tests non validés.')
    french = p.api('actions/runs/41245388979')
    require(french['head_sha'] == SOURCE and french['conclusion'] == 'success', 'Français non validé.')
    with tempfile.TemporaryDirectory(prefix='kovelyx-draft-') as directory:
        root = Path(directory)
        for run, artifact, folder in [(41245388962,'kovelyx-mobile-debug','mobile'), (41245388962,'kovelyx-tv-debug','tv'), (41245388977,'Kovelyx-Windows-installers-experimental','windows')]:
            p.gh('run','download',str(run),'--repo',REPO,'--name',artifact,'--dir',str(root/folder))
        out = root/'publication'
        out.mkdir()
        sdk = sorted((Path(os.environ['ANDROID_HOME'])/'build-tools').iterdir(), key=lambda path:tuple(int(x) if x.isdigit() else 0 for x in path.name.split('.')))[-1]
        certificates = []
        for folder, package, pattern, name in [('mobile','com.kovelyx.mobile.debug','*.apk',f'Kovelyx-Android-{TAG}.apk'), ('tv','com.kovelyx.tv.debug','*universal*.apk',f'Kovelyx-TV-{TAG}-universal.apk')]:
            apk = p.unique_file(root/folder, pattern)
            cert = subprocess.check_output([str(sdk/'apksigner'),'verify','--print-certs',str(apk)],text=True)
            certificates.append([line for line in cert.splitlines() if 'certificate SHA-256 digest:' in line])
            badging = subprocess.check_output([str(sdk/'aapt'),'dump','badging',str(apk)],text=True)
            require(f"package: name='{package}'" in badging and "versionName='1.1.777'" in badging and "versionCode='100777'" in badging, 'Identité APK incorrecte.')
            shutil.copyfile(apk,out/name)
        require(certificates[0] and certificates[0] == certificates[1], 'Certificats APK différents.')
        for original, public in p.windows_package_names(SOURCE, TAG):
            shutil.copyfile(p.unique_file(root/'windows',original),out/public)
        for path in list(out.iterdir()):
            path.with_name(path.name+'.sha256').write_text(f'{p.digest(path)}  {path.name}\n')
        expected = {path.name:'sha256:'+p.digest(path) for path in out.iterdir()}
        for path in sorted(out.iterdir()):
            r = release()
            require(r['draft'], 'Le brouillon a été publié avant la fin des envois.')
            assets = {asset['name']:asset for asset in r['assets']}
            if path.name in assets:
                require(assets[path.name].get('digest') == expected[path.name], 'Un fichier différent existe : aucun écrasement.')
            else:
                p.gh('release','upload',TAG,str(path),'--repo',REPO)
        r = release()
        require(r['draft'] and {a['name']:a.get('digest') for a in r['assets']} == expected, 'Ensemble des fichiers incomplet.')
        print('OK : dix fichiers vérifiés ajoutés au brouillon privé. Publication stable prête.', flush=True)


if __name__ == '__main__':
    main()
