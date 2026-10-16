(() => {
  "use strict";

  // Source unique des téléchargements : les assets de Releases réellement publiées.
  // Jamais un tag figé, jamais un lien ZIP GitHub Actions ni le « latest » global
  // (les Releases Windows, Android et TV sont publiées indépendamment).
  const repo = "anderson76389/Kovelyx";
  const github = "https://github.com/" + repo;
  const releasesPage = github + "/releases";
  const api = "https://api.github.com/repos/" + repo + "/releases?per_page=100";
  const validDownload = /^https:\/\/github\.com\/anderson76389\/Kovelyx\/releases\/download\/[^/]+\/[^/?#]+$/i;
  const patterns = {
    desktop: /^Kovelyx-Windows-Setup-.+\.exe$/i,
    windowsMsi: /^Kovelyx-Windows-.+\.msi$/i,
    windowsZip: /^Kovelyx-Windows-Portable-.+\.zip$/i,
    android: /^Kovelyx-(?:Android|Mobile)-.+\.apk$/i,
    tv: /^Kovelyx-(?:TV|Android-TV|AndroidTV)-.+\.apk$/i
  };
  const platformOrder = ["desktop", "android", "tv"];
  const config = {
    desktop: {
      label: "01 / WINDOWS",
      title: "Kovelyx pour Windows.",
      desc: "Pour Windows 10 et 11 en 64 bits. Installateur EXE, MSI et version portable ZIP suivant les fichiers réellement disponibles.",
      tags: ["Windows 10 / 11", "64 bits", "EXE · MSI · Portable"],
      device: "desktop",
      button: "Télécharger Windows EXE"
    },
    android: {
      label: "02 / ANDROID MOBILE",
      title: "Kovelyx pour Android.",
      desc: "Pour téléphone ou tablette Android 7 et versions ultérieures. APK distinct de la version télévision ; installation et fonctions encore en test.",
      tags: ["Android 7+", "Téléphones et tablettes", "APK"],
      device: "phone",
      button: "Télécharger l'APK Android"
    },
    tv: {
      label: "03 / ANDROID TV",
      title: "Kovelyx pour Android TV.",
      desc: "Pour Android TV, Google TV et TV Box Android compatibles, avec une interface conçue pour la télécommande. APK séparé de la version mobile.",
      tags: ["Android TV / Google TV", "TV Box", "Télécommande"],
      device: "tv",
      button: "Télécharger l'APK TV"
    }
  };

  const $ = id => document.getElementById(id);
  const tabs = [...document.querySelectorAll('[role="tab"][data-platform]')];
  const releaseCards = [...document.querySelectorAll("[data-release-status]")];
  const versions = {desktop: null, android: null, tv: null};
  let availabilityState = "loading";
  // Liens du README : ?plateforme=desktop|android|tv#plateformes.
  const requestedPlatform = new URLSearchParams(window.location.search).get("plateforme");
  let currentPlatform = platformOrder.includes(requestedPlatform) ? requestedPlatform : "desktop";

  function sizeText(bytes) {
    return (bytes / 1048576).toLocaleString("fr-FR", {maximumFractionDigits: 1}) + " Mo";
  }
  function releaseTime(release) {
    const time = Date.parse(release.published_at || release.created_at || "");
    return Number.isFinite(time) ? time : 0;
  }
  function validAsset(asset, pattern) {
    return asset && typeof asset.name === "string" &&
      asset.state === "uploaded" && Number.isFinite(asset.size) &&
      asset.size > 1000000 &&
      pattern.test(asset.name) && validDownload.test(asset.browser_download_url || "");
  }
  function findAsset(release, pattern) {
    return (release.assets || []).find(asset => validAsset(asset, pattern)) || null;
  }
  function findForPlatform(releases, platform) {
    for (const release of releases) {
      if (release.draft || !Array.isArray(release.assets)) continue;
      // Préférer l'installateur EXE au MSI pour Windows. Ne jamais confondre APK TV et mobile.
      let asset = findAsset(release, patterns[platform]);
      if (platform === "desktop" && !asset) asset = findAsset(release, patterns.windowsMsi);
      if (asset) {
        return {
          release,
          asset,
          extras: platform === "desktop" ? {
            msi: findAsset(release, patterns.windowsMsi),
            portable: findAsset(release, patterns.windowsZip)
          } : {}
        };
      }
    }
    return null;
  }
  function versionStatus(match) {
    if (!match) {
      if (availabilityState === "loading") return "Recherche des versions publiées…";
      if (availabilityState === "error") return "Vérification indisponible : voir GitHub Releases";
      return "Aucun fichier téléchargeable publié pour cette plateforme.";
    }
    return match.release.tag_name + " · " +
      (match.release.prerelease ? "Préversion expérimentale" : "Version publiée") +
      " · " + sizeText(match.asset.size);
  }
  function safeLink(node, match, text, fallbackText) {
    if (!node) return;
    node.href = match ? match.asset.browser_download_url : releasesPage;
    node.textContent = match ? text : fallbackText;
    node.removeAttribute("download"); // Autoriser les redirections officielles GitHub.
    node.setAttribute("rel", "noopener noreferrer");
  }
  function updateHero() {
    const items = [
      ["hero-windows-download", "desktop", "↓ Télécharger Windows EXE"],
      ["hero-android-download", "android", "↓ Télécharger Android APK"],
      ["hero-tv-download", "tv", "↓ Télécharger Android TV APK"]
    ];
    for (const [id, platform, text] of items) {
      const match = versions[platform];
      const title = match && platform === "desktop" && /\.msi$/i.test(match.asset.name)
        ? "↓ Télécharger Windows MSI" : text;
      safeLink($(id), match, title, "Voir les Releases " + (platform === "desktop" ? "Windows" : platform === "android" ? "Android" : "TV") + " ↗");
    }
    const note = $("hero-download-note");
    if (note) {
      note.textContent = availabilityState === "loaded"
        ? "Liens récupérés depuis les fichiers publiés sur GitHub. Chaque plateforme évolue indépendamment ; les versions sont encore expérimentales."
        : availabilityState === "error"
          ? "GitHub ne répond pas pour le moment. Les boutons ouvrent la liste officielle des Releases."
          : "Vérification des téléchargements publiés sur GitHub…";
    }
  }
  function setPlatform(platform, focus = false) {
    const item = config[platform];
    if (!item) return;
    currentPlatform = platform;
    tabs.forEach(tab => {
      const selected = tab.dataset.platform === platform;
      tab.setAttribute("aria-selected", String(selected));
      tab.tabIndex = selected ? 0 : -1;
      if (selected && focus) tab.focus();
    });
    const panel = $("platform-showcase");
    if (panel) panel.setAttribute("aria-labelledby", "tab-" + platform);
    $("panel-label").textContent = item.label;
    $("panel-title").textContent = item.title;
    $("panel-description").textContent = item.desc;
    $("panel-tags").replaceChildren(...item.tags.map(tag => {
      const span = document.createElement("span");
      span.textContent = tag;
      return span;
    }));
    $("panel-device").className = "device " + item.device;
    const match = versions[platform];
    $("download-meta").textContent = versionStatus(match);
    const primary = $("platform-download");
    const disabled = $("download-disabled");
    const portable = $("platform-portable");
    const msi = $("platform-msi");
    const release = $("platform-release");
    if (release) release.href = match ? match.release.html_url : releasesPage;
    if (portable) {
      const asset = platform === "desktop" ? match?.extras?.portable : null;
      portable.hidden = !asset;
      if (asset) portable.href = asset.browser_download_url;
      else portable.removeAttribute("href");
    }
    if (msi) {
      const asset = platform === "desktop" && /\.exe$/i.test(match?.asset?.name || "") ? match?.extras?.msi : null;
      msi.hidden = !asset;
      if (asset) msi.href = asset.browser_download_url;
      else msi.removeAttribute("href");
    }
    if (match) {
      primary.hidden = false;
      primary.href = match.asset.browser_download_url;
      primary.textContent = platform === "desktop" && /\.msi$/i.test(match.asset.name)
        ? "↓ Télécharger Windows MSI" : "↓ " + item.button;
      primary.setAttribute("rel", "noopener noreferrer");
      disabled.hidden = true;
    } else {
      primary.hidden = true;
      primary.removeAttribute("href");
      disabled.hidden = false;
      disabled.textContent = availabilityState === "loading"
        ? "Vérification des fichiers…" : "Consulter les Releases ↗";
      if (disabled.tagName === "A") disabled.href = releasesPage;
    }
  }
  function renderReleaseCards() {
    releaseCards.forEach(card => {
      const platform = card.dataset.releaseStatus;
      const match = versions[platform];
      if (!Object.hasOwn(config, platform)) return;
      const status = card.querySelector(".release-status");
      const action = card.querySelector(".release-link");
      const name = card.querySelector(".release-filename");
      const more = card.querySelector(".release-more");
      const portable = card.querySelector(".portable-link");
      const msi = card.querySelector(".msi-link");
      if (status) status.textContent = versionStatus(match);
      if (name) name.textContent = match ? match.asset.name : "Fichier en cours de vérification";
      safeLink(action, match, "↓ Télécharger le fichier " + (platform === "desktop" ? (/\.exe$/i.test(match?.asset.name || "") ? "EXE" : "MSI") : "APK"), "Voir les Releases ↗");
      if (more) more.href = match ? match.release.html_url : releasesPage;
      for (const [node, asset] of [[portable, match?.extras?.portable],
                                  [msi, platform === "desktop" && /\.exe$/i.test(match?.asset.name || "") ? match?.extras?.msi : null]]) {
        if (node) {
          node.hidden = !asset;
          if (asset) node.href = asset.browser_download_url;
          else node.removeAttribute("href");
        }
      }
    });
  }
  function render() {
    setPlatform(currentPlatform);
    renderReleaseCards();
    updateHero();
    const mode = $("release-loading");
    if (mode) {
      mode.textContent = availabilityState === "loaded"
        ? "Fichiers vérifiés parmi les Releases GitHub ; les trois versions sont suivies indépendamment."
        : availabilityState === "error"
          ? "Impossible de consulter les Releases en direct. Les liens ouvrent la liste GitHub officielle."
          : "Recherche des dernières Releases…";
    }
  }
  tabs.forEach((tab, index) => {
    tab.addEventListener("click", () => setPlatform(tab.dataset.platform));
    tab.addEventListener("keydown", event => {
      let next;
      if (event.key === "ArrowRight") next = (index + 1) % tabs.length;
      else if (event.key === "ArrowLeft") next = (index + tabs.length - 1) % tabs.length;
      else if (event.key === "Home") next = 0;
      else if (event.key === "End") next = tabs.length - 1;
      else return;
      event.preventDefault();
      setPlatform(tabs[next].dataset.platform, true);
    });
  });

  render(); // Liens de secours vers /releases tant que la requête n'a pas abouti.
  fetch(api, {headers: {"Accept": "application/vnd.github+json"}, cache: "no-store"})
    .then(response => {
      if (!response.ok) throw new Error("GitHub Releases indisponible");
      return response.json();
    })
    .then(releases => {
      if (!Array.isArray(releases)) throw new Error("Format de Releases inattendu");
      // La dernière publication de chaque plateforme, pas la dernière Release globale.
      releases.sort((a, b) => releaseTime(b) - releaseTime(a));
      platformOrder.forEach(platform => { versions[platform] = findForPlatform(releases, platform); });
      availabilityState = "loaded";
      render();
    })
    .catch(() => {
      availabilityState = "error";
      render();
    });
})();
