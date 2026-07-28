import * as Url from "node:url";

const debug = process.env.DEBUG ? +process.env.DEBUG : null;
if (debug !== null && isNaN(debug)) throw new Error();

export function debugPort() {
  if (debug === null) return null;
  return 1000 + debug;
}

export function debugDb() {
  if (debug === null) return null;
  return Url.fileURLToPath(
    Url.resolve(import.meta.url, `../tmp/debug${debugPort()}.db`),
  );
}

export function debugUid() {
  if (debug === null) return null;
  return `_debug${debugPort()}`;
}

export function debugTypeormSynchronize() {
  return debug !== null;
}
