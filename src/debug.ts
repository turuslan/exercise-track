import * as Url from "node:url";
import { DeviceUid } from "./types";

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

export function debugUid(): DeviceUid | null {
  if (debug === null) return null;
  return `_debug${debugPort()}`;
}

export function debugTypeormSynchronize() {
  return debug !== null;
}
