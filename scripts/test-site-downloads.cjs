// Tests sans navigateur pour la selection de Releases independantes.
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");

const script = fs.readFileSync("docs/site.js", "utf8");
const origin = "https://github.com/anderson76389/Kovelyx";
const published = (tag, publishedAt, assets) => ({
  tag_name: tag, published_at: publishedAt, prerelease: true, draft: false,
  html_url: origin + "/releases/tag/" + tag,
  assets: assets.map(([name, size = 120000000, url]) => ({
    name, size, state: "uploaded",
    browser_download_url: url || origin + "/releases/download/" + tag + "/" + name
  }))
});

function element(dataset = {}) {
  return {
    dataset, hidden: false, textContent: "", href: "", style: {},
    className: "", tagName: "A", attributes: {}, childNodes: [],
    setAttribute(k, v) { this.attributes[k] = String(v); },
    removeAttribute(k) { delete this.attributes[k]; },
    replaceChildren(...nodes) { this.childNodes = nodes; },
    addEventListener() {},
    focus() { this.focused = true; }
  };
}
async function simulate(releases, options = {}) {
  const ids = new Map();
  const byId = id => {
    if (!ids.has(id)) ids.set(id, element());
    return ids.get(id);
  };
  const tabs = ["desktop", "android", "tv"].map(p => {
    const node = element({platform: p});
    return node;
  });
  const cards = ["desktop", "android", "tv"].map(platform => {
    const card = element({releaseStatus: platform});
    const children = new Map();
    card.querySelector = selector => {
      if (!children.has(selector)) children.set(selector, element());
      return children.get(selector);
    };
    card.nodes = children;
    return card;
  });
  const document = {
    querySelectorAll(selector) {
      if (selector === '[role="tab"][data-platform]') return tabs;
      if (selector === "[data-release-status]") return cards;
      throw Error("Unexpected selector: " + selector);
    },
    getElementById: byId,
    createElement: () => element()
  };
  const sandbox = {
    document,
    window: {location: {search: options.search || ""}},
    URLSearchParams,
    Date, Number, Object, console,
    fetch: () => options.error
      ? Promise.reject(new Error("network down"))
      : Promise.resolve({ok: true, json: () => Promise.resolve(releases)})
  };
  vm.runInNewContext(script, sandbox, {timeout: 2000});
  for (let i = 0; i < 6; i++) await new Promise(resolve => setImmediate(resolve));
  return {ids, cards, tabs, byId};
}

(async () => {
  const releases = [
    published("v0.1.377-alpha", "2026-10-12T10:00:00Z", [
      ["Kovelyx-Windows-v0.1.377-alpha.msi"],
      ["Kovelyx-Windows-Setup-v0.1.377-alpha.exe"],
      ["Kovelyx-Windows-Portable-v0.1.377-alpha.zip"]
    ]),
    published("android-preview-old", "2026-10-13T10:00:00Z", [
      ["Kovelyx-Android-android-preview-old.apk"]
    ]),
    published("android-preview-new", "2026-10-14T10:00:00Z", [
      ["Kovelyx-Android-android-preview-new.apk"]
    ]),
    published("tv-preview-new", "2026-10-15T10:00:00Z", [
      ["Kovelyx-TV-tv-preview-new-universal.apk"]
    ]),
    // Un fichier sans correspondance GitHub, meme avec le bon nom, est ignore.
    published("malicious", "2026-10-16T10:00:00Z", [
      ["Kovelyx-Android-malicious.apk", 130000000, "https://untrusted.invalid/example.apk"]
    ])
  ].reverse();

  const result = await simulate(releases, {search: "?plateforme=tv"});
  assert.equal(result.byId("hero-windows-download").href, origin + "/releases/download/v0.1.377-alpha/Kovelyx-Windows-Setup-v0.1.377-alpha.exe");
  assert.equal(result.byId("hero-android-download").href, origin + "/releases/download/android-preview-new/Kovelyx-Android-android-preview-new.apk");
  assert.equal(result.byId("hero-tv-download").href, origin + "/releases/download/tv-preview-new/Kovelyx-TV-tv-preview-new-universal.apk");
  assert.match(result.byId("panel-title").textContent, /Android TV/);
  assert.equal(result.tabs[2].attributes["aria-selected"], "true");
  assert.equal(result.cards[2].nodes.get(".release-link").href, result.byId("hero-tv-download").href);
  assert.equal(result.cards[1].nodes.get(".release-link").href, result.byId("hero-android-download").href);

  const win = await simulate(releases);
  assert.equal(win.byId("platform-portable").href, origin + "/releases/download/v0.1.377-alpha/Kovelyx-Windows-Portable-v0.1.377-alpha.zip");
  assert.equal(win.byId("platform-msi").href, origin + "/releases/download/v0.1.377-alpha/Kovelyx-Windows-v0.1.377-alpha.msi");

  const signed = await simulate([
    ...releases,
    published("v0.5.11-beta.1", "2026-10-18T10:00:00Z", [["Kovelyx-Android-v0.5.11-beta.1-universal.apk"]]),
    published("v1.1.1-beta.1", "2026-10-19T10:00:00Z", [["Kovelyx-TV-v1.1.1-beta.1-universal.apk"]])
  ]);
  assert.match(signed.byId("hero-android-download").href, /v0\.5\.11-beta\.1/);
  assert.match(signed.byId("hero-tv-download").href, /v1\.1\.1-beta\.1/);

  const missing = await simulate([]);
  for (const id of ["hero-windows-download", "hero-android-download", "hero-tv-download"]) {
    assert.equal(missing.byId(id).href, origin + "/releases");
  }
  assert.equal(missing.byId("platform-download").hidden, true);

  const error = await simulate([], {error: true});
  assert.equal(error.byId("hero-android-download").href, origin + "/releases");
  assert.match(error.byId("release-loading").textContent, /Impossible/);

  // La page HTML ne doit pas conserver un APK/EXE obsolète si JavaScript échoue.
  const html = fs.readFileSync("docs/index.html", "utf8");
  assert(!/releases\/download\/|actions\/runs\/\d+\/artifacts/.test(html), "Lien fige ou artefact perime dans le HTML");
  for (const tag of ["desktop", "android", "tv"]) {
    assert(html.includes('data-release-status="' + tag + '"'), "Carte manquante: " + tag);
    assert(html.includes('data-platform="' + tag + '"'), "Onglet manquant: " + tag);
  }
  console.log("OK : trois plateformes, liens directs, publications independantes, signatures futures, secours GitHub.");
})().catch(error => {console.error(error); process.exitCode = 1;});
