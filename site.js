(() => {
  "use strict";
  const repo = "anderson76389/Kovelyx";
  const github = "https://github.com/" + repo;
  const patterns = {
    desktop: /^kovelyx[-_](?:windows|desktop)[-_].+\.(?:exe|msi)$/i
  };
  const config = {
    desktop: {label:"01 / WINDOWS",title:"Kovelyx pour Windows.",desc:"L'application de bureau pour Windows 10 et 11. Téléchargez un installateur EXE complet, un MSI ou une archive portable selon vos besoins.",tags:["Windows x64","Installateur EXE","MSI et portable"],device:"desktop",suffix:"Installateur MSI"}
  };
  const tabs = [...document.querySelectorAll('[role="tab"][data-platform]')];
  const title = document.getElementById("panel-title");
  const label = document.getElementById("panel-label");
  const desc = document.getElementById("panel-description");
  const tags = document.getElementById("panel-tags");
  const visual = document.getElementById("panel-device");
  const panel = document.getElementById("platform-showcase");
  const link = document.getElementById("platform-download");
  const disabled = document.getElementById("download-disabled");
  const portableLink = document.getElementById("platform-portable");
  const details = document.getElementById("download-meta");
  const releaseItems = [...document.querySelectorAll("[data-release-status]")];
  let currentPlatform = "desktop";
  const versions = { desktop: null };
  let availabilityState = "loading";
  let portableAsset = null;
  const portablePattern = /^kovelyx[-_](?:windows|desktop)[-_]portable[-_].+\.zip$/i;
  const releaseAssetUrl = /^https:\/\/github\.com\/anderson76389\/Kovelyx\/releases\/download\//i;

  function sizeText(bytes) { return (bytes / 1048576).toLocaleString("fr-FR", {maximumFractionDigits:1}) + " Mo"; }
  function findMatchingRelease(releases, platform) {
    for (const release of releases) {
      if (release.draft || !Array.isArray(release.assets)) continue;
      const candidates = platform === "desktop"
        ? [...release.assets].sort((a, b) => Number(/\.exe$/i.test(b.name || "")) - Number(/\.exe$/i.test(a.name || "")))
        : release.assets;
      for (const asset of candidates) {
        const url = asset.browser_download_url || "";
        if (asset.state === "uploaded" && asset.size > 100000 &&
            patterns[platform].test(asset.name) &&
            releaseAssetUrl.test(url)) {
          return {release, asset};
        }
      }
    }
    return null;
  }
  function findPortableAsset(release) {
    if (!release || !Array.isArray(release.assets)) return null;
    return release.assets.find(a => a.state === "uploaded" && a.size > 100000 && portablePattern.test(a.name) && releaseAssetUrl.test(a.browser_download_url || "")) || null;
  }
  function statusText(selected) {
    if (!selected) return availabilityState === "error" ? "Vérification indisponible" : availabilityState === "loading" ? "Vérification des versions…" : "Pas de version publiée";
    return selected.release.tag_name + " · " + (selected.release.prerelease ? "Préversion" : "Version publiée");
  }
  function setPlatform(platform, focus = false) {
    const item = config[platform];
    if (!item) return;
    currentPlatform = platform;
    tabs.forEach(t => {
      const isActive = t.dataset.platform === platform;
      t.setAttribute("aria-selected", String(isActive));
      t.tabIndex = isActive ? 0 : -1;
      if (focus && isActive) t.focus();
    });
    panel.setAttribute("aria-labelledby", "tab-" + platform);
    label.textContent = item.label;
    title.textContent = item.title;
    desc.textContent = item.desc;
    tags.replaceChildren(...item.tags.map(tag => { const span = document.createElement("span"); span.textContent = tag; return span; }));
    visual.className = "device " + item.device;
    const matched = versions[platform];
    details.textContent = matched ? statusText(matched) + " · " + sizeText(matched.asset.size) : statusText(null);
    if (portableLink) {
      const showPortable = platform === "desktop" && matched && portableAsset;
      portableLink.hidden = !showPortable;
      if (showPortable) portableLink.href = portableAsset.browser_download_url;
      else portableLink.removeAttribute("href");
    }
    if (matched) {
      link.href = matched.asset.browser_download_url;
      link.textContent = "↓ Télécharger " +
        (platform === "desktop" && /\.exe$/i.test(matched.asset.name) ? "l'installateur EXE" : item.suffix);
      link.hidden = false;
      disabled.hidden = true;
    } else {
      link.hidden = true;
      link.removeAttribute("href");
      disabled.hidden = false;
      disabled.textContent = availabilityState === "loading" ? "Vérification en cours…" : "Bientôt disponible";
    }
  }
  tabs.forEach((tab,index) => {
    tab.addEventListener("click", () => setPlatform(tab.dataset.platform));
    tab.addEventListener("keydown", event => {
      let next;
      if (event.key === "ArrowRight") next = (index + 1) % tabs.length;
      else if (event.key === "ArrowLeft") next = (index + tabs.length - 1) % tabs.length;
      else if (event.key === "Home") next = 0;
      else if (event.key === "End") next = tabs.length - 1;
      else return;
      event.preventDefault();
      setPlatform(tabs[next].dataset.platform,true);
    });
  });
  function renderReleaseCards() {
    releaseItems.forEach(card => {
      const p = card.dataset.releaseStatus;
      const match = versions[p];
      const state = card.querySelector(".release-status");
      const action = card.querySelector(".release-link");
      const portableAction = card.querySelector(".portable-link");
      if (portableAction) {
        portableAction.hidden = !(p === "desktop" && match && portableAsset);
        if (!portableAction.hidden) portableAction.href = portableAsset.browser_download_url;
        else portableAction.removeAttribute("href");
      }
      if (match) {
        state.textContent = statusText(match);
        action.textContent = p === "desktop" && /\.exe$/i.test(match.asset.name)
          ? "Télécharger EXE →" : "Télécharger →";
        action.href = match.asset.browser_download_url;
        action.hidden = false;
      } else {
        state.textContent = statusText(null);
        action.textContent = "Voir les Releases →";
        action.href = github + "/releases";
        action.hidden = false;
      }
    });
  }
  const mode = document.getElementById("release-loading");
  setPlatform(currentPlatform);
  renderReleaseCards();
  fetch("https://api.github.com/repos/" + repo + "/releases?per_page=50", {
    headers: {"Accept":"application/vnd.github+json"}
  })
    .then(r => { if (!r.ok) throw new Error("Échec du chargement des Releases"); return r.json(); })
    .then(releases => {
      if (!Array.isArray(releases)) throw new Error("Réponse GitHub inattendue");
      releases.sort((a,b) => new Date(b.published_at || b.created_at || 0) - new Date(a.published_at || a.created_at || 0));
      for (const platform of Object.keys(versions)) versions[platform] = findMatchingRelease(releases,platform);
      portableAsset = findPortableAsset(versions.desktop?.release);
      availabilityState = "loaded";
      if (mode) mode.textContent = releases.length ? "Versions vérifiées depuis GitHub Releases." : "Aucune Release Kovelyx publiée pour le moment.";
      setPlatform(currentPlatform);
      renderReleaseCards();
    })
    .catch(() => {
      availabilityState = "error";
      if (mode) mode.textContent = "GitHub est momentanément inaccessible : consultez directement les Releases.";
      setPlatform(currentPlatform);
      renderReleaseCards();
    });
})();
