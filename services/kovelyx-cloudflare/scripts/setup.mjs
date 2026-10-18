#!/usr/bin/env node
// Installe une instance Kovelyx Cloudflare personnelle. Aucun token n'est stocké dans Git.
import { execFileSync, spawnSync } from "node:child_process";
import { randomBytes } from "node:crypto";
import { readFileSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(fileURLToPath(new URL("../", import.meta.url)));
process.chdir(root);
const wrangler = ["wrangler"];
const name = "kovelyx-accounts";

function call(args, input) {
  console.log("\n▶ npx " + args.join(" "));
  const result = spawnSync("npx", args, {
    encoding: "utf8",
    stdio: input === undefined ? "inherit" : ["pipe", "inherit", "inherit"],
    input,
    env: process.env,
  });
  if (result.error) throw result.error;
  if (result.status !== 0) throw Error("La commande a échoué : npx " + args.join(" "));
}
function output(args) {
  return execFileSync("npx", args, {encoding: "utf8", maxBuffer: 1_000_000});
}
function parseArray(text) {
  const start = text.indexOf("[");
  if (start < 0) throw Error("La réponse Cloudflare ne contient pas la liste JSON attendue");
  return JSON.parse(text.slice(start).trim());
}
function getDatabase() {
  const all = parseArray(output(["wrangler", "d1", "list", "--json"]));
  return all.find(row => row.name === name);
}
console.log("Kovelyx Cloud — création d'un Worker et d'une base D1 personnelle.");
console.log("Les inscriptions resteront fermées après la mise en place.");
console.log("Aucune clé privée ne sera affichée ni enregistrée dans Git.\n");
call(["wrangler", "whoami"]);

let database = getDatabase();
if (!database) {
  console.log("\nCréation d'une nouvelle base D1 limitée à l'Union européenne.");
  call(["wrangler", "d1", "create", name, "--jurisdiction=eu"]);
  database = getDatabase();
}
const id = database?.uuid ?? database?.database_id ?? database?.id;
if (typeof id !== "string" || !/^[a-f0-9]{8}-(?:[a-f0-9]{4}-){3}[a-f0-9]{12}$/i.test(id)) {
  throw Error("Identifiant D1 invalide. Relance la commande : l'ID D1 doit être une UUID.");
}

const configPath = "wrangler.toml";
const config = readFileSync(configPath, "utf8");
const updated = config.replace(
  /(^database_id\s*=\s*")[^"]*(")/m, (_, before, after) => before + id + after
);
if (updated === config && !config.includes(id))
  throw Error("Configuration wrangler.toml introuvable ou inattendue.");
if (updated !== config) {
  writeFileSync(configPath, updated);
  console.log("✅ Liaison D1 sauvegardée dans wrangler.toml (ID public uniquement).");
} else {
  console.log("✅ Base D1 déjà liée : aucune modification.");
}

console.log("\nApplication du schéma de comptes.");
call(["wrangler", "d1", "migrations", "apply", "DB", "--remote"]);

console.log("\nPublication du Worker. Cloudflare peut demander de confirmer votre sous-domaine workers.dev.");
call(["wrangler", "deploy"]);

let existing = [];
try {
  existing = parseArray(output(["wrangler", "secret", "list", "--format=json"]));
} catch (error) {
  console.error("Impossible de lire la liste des secrets. Le Worker existe, mais la connexion restera désactivée.");
  throw error;
}
if (existing.some(row => row.name === "BETTER_AUTH_SECRET")) {
  console.log("✅ Secret d'authentification déjà présent : préservé, aucune rotation.");
} else {
  const secret = randomBytes(32).toString("hex");
  call(["wrangler", "secret", "put", "BETTER_AUTH_SECRET"], secret + "\n");
  console.log("✅ Nouveau secret d'authentification généré, jamais affiché.");
}
console.log("\n✅ Serveur installé. Les inscriptions restent fermées par défaut.");
console.log("Copie uniquement l'URL publique *.workers.dev affichée par Wrangler.");
console.log("Vérifie cette URL en ajoutant /health, puis configure GitHub Actions :");
console.log("Settings → Secrets and variables → Actions → Variables");
console.log("Nom : KOVELYX_CLOUDFLARE_URL ; valeur : URL complète https://...workers.dev");
console.log("NE PAS partager vos jetons, mots de passe ni secrets.");
