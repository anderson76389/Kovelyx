#!/usr/bin/env node
/**
 * Portable Kovelyx backend: same authenticated Worker handlers, local SQLite.
 *
 * Default: loopback only. For device access, put this server behind your own
 * HTTPS reverse proxy and set KOVELYX_PUBLIC_URL to that exact public origin.
 * Never run publicly without a firewall, HTTPS and a protected secret file.
 */
import {createServer} from "node:http";
import {readFileSync, statSync} from "node:fs";
import {DatabaseSync} from "node:sqlite";
import {once} from "node:events";
import {resolve} from "node:path";
import worker from "../src/index.mjs";
import {createLocalD1} from "./sqlite-d1.mjs";

const MAX_BODY_BYTES = 18_000_000;
function privateFile(path, label) {
  if (!path) throw Error(label + " file path is required");
  const location = resolve(path);
  const stat = statSync(location);
  if (!stat.isFile()) throw Error(label + " must be a regular file");
  if (process.platform !== "win32" && (stat.mode & 0o077) !== 0)
    throw Error(label + " must not be accessible by group/others (chmod 600)");
  return location;
}
function publicOrigin(host, port) {
  const advertised = process.env.KOVELYX_PUBLIC_URL ||
    "http://127.0.0.1:" + port;
  const url = new URL(advertised);
  if (url.username || url.password || url.pathname !== "/" || url.search || url.hash)
    throw Error("KOVELYX_PUBLIC_URL must be an origin without a path or credentials");
  if (url.protocol !== "https:" &&
      !(url.protocol === "http:" && ["localhost","127.0.0.1","[::1]"].includes(url.hostname)))
    throw Error("A non-local public URL must use HTTPS");
  if (!["127.0.0.1","::1","localhost"].includes(host) && url.protocol !== "https:")
    throw Error("Public binding requires an HTTPS KOVELYX_PUBLIC_URL");
  return url.origin;
}
async function bodyBytes(request) {
  const pieces=[];
  let length=0;
  for await (const piece of request) {
    length+=piece.length;
    if (length>MAX_BODY_BYTES) throw RangeError("Request body too large");
    pieces.push(piece);
  }
  return Buffer.concat(pieces,length);
}
async function serveRequest(nodeRequest, nodeResponse, env, origin) {
  try {
    const rawPath = nodeRequest.url || "";
    if (!rawPath.startsWith("/") || rawPath.startsWith("//"))
      throw RangeError("Invalid request target");
    const method=nodeRequest.method || "GET";
    const headers=new Headers();
    for(const [name,value] of Object.entries(nodeRequest.headers))
      if (value!==undefined) headers.set(name,Array.isArray(value)?value.join(", "):value);
    let body;
    if(!["GET","HEAD"].includes(method)) {
      if(Number(nodeRequest.headers["content-length"] || 0)>MAX_BODY_BYTES)
        throw RangeError("Request body too large");
      body=await bodyBytes(nodeRequest);
    }
    const target=new URL(rawPath,origin);
    if(target.origin!==origin) throw RangeError("Invalid request origin");
    const request=new Request(target,{method,headers,body});
    const result=await worker.fetch(request,env);
    nodeResponse.statusCode=result.status;
    for(const [name,value] of result.headers) {
      if(name.toLowerCase()!=="set-cookie")nodeResponse.setHeader(name,value);
    }
    const cookies=result.headers.getSetCookie?.() || [];
    if(cookies.length)nodeResponse.setHeader("set-cookie",cookies);
    if(method==="HEAD" || !result.body){nodeResponse.end();return;}
    for await (const chunk of result.body) {
      if(!nodeResponse.write(Buffer.from(chunk)))
        await once(nodeResponse,"drain");
    }
    nodeResponse.end();
  } catch(error) {
    if(!nodeResponse.headersSent) {
      const code=error instanceof RangeError ? 413 : 500;
      nodeResponse.writeHead(code,{"content-type":"application/json","cache-control":"no-store"});
      nodeResponse.end(JSON.stringify({error:code===413?"Requête invalide ou trop volumineuse":"Erreur interne"}));
    } else nodeResponse.destroy();
    // Do not log URLs, tokens, request bodies or SQLite records.
    console.error("Kovelyx portable request failed:",error?.name || "Error");
  }
}

async function main() {
  process.umask(0o077);
  const host=process.env.KOVELYX_BIND_HOST || "127.0.0.1";
  const port=Number(process.env.KOVELYX_PORT || "8788");
  if(!Number.isInteger(port) || port<0 || port>65535)
    throw Error("Invalid KOVELYX_PORT");
  const origin=publicOrigin(host,port);
  const databasePath=privateFile(process.env.KOVELYX_DB_FILE,"KOVELYX_DB_FILE");
  const secretPath=privateFile(process.env.KOVELYX_AUTH_SECRET_FILE,"KOVELYX_AUTH_SECRET_FILE");
  const secret=readFileSync(secretPath,"utf8").trim();
  if(secret.length<32)throw Error("Authentication secret must be at least 32 characters");
  const db=new DatabaseSync(databasePath,{timeout:5000});
  db.exec("PRAGMA foreign_keys=ON");
  const integrity=db.prepare("PRAGMA quick_check").get();
  if(Object.values(integrity)[0]!=="ok")throw Error("Database integrity check failed");
  const env={
    DB:createLocalD1(db),AUTH_DB:db,
    BETTER_AUTH_SECRET:secret,
    KOVELYX_PROVIDER:"SQLite portable",
    KOVELYX_REGISTRATION_OPEN:process.env.KOVELYX_REGISTRATION_OPEN==="true" ? "true" : "false"
  };
  const server=createServer((req,res)=>{void serveRequest(req,res,env,origin)});
  server.requestTimeout=30_000;
  server.headersTimeout=15_000;
  server.keepAliveTimeout=5_000;
  server.listen(port,host,()=>{
    console.log("Kovelyx portable ready on "+host+":"+server.address().port+
      " (public origin: "+origin+")");
  });
  const stop=()=>server.close(()=>{db.close();process.exit(0)});
  process.once("SIGINT",stop);
  process.once("SIGTERM",stop);
}
main().catch(error=>{
  console.error("Kovelyx portable startup refused:",error?.name || "Error",
    // Startup errors contain only configuration failures, never request payloads.
    error?.message || "");
  process.exitCode=1;
});
