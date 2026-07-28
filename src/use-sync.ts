import { dbMaxTime, dbMergeTime } from "./db";
import { mdns } from "./mdns";
import { notifyTimeAdded } from "./use-time";

export function toDiff(times: number[]) {
  return times.map((x, i, xs) => i === 0 ? x : x - xs[i - 1]);
}
function fromDiff(times_diff: number[]) {
  const times: number[] = [];
  let sum = 0;
  for (const diff of times_diff) {
    sum += diff;
    times.push(sum);
  }
  return times;
}

const syncing = new Set<string>();

async function syncLocked(device_uid: string) {
  const addresses = mdns.services[device_uid] ?? [];
  if (addresses.length === 0) {
    return;
  }
  const address = addresses[0];
  const api_url = new URL(`http://${address}/api/list`);
  api_url.searchParams.set("device_uid", device_uid);
  const max = await dbMaxTime(device_uid);
  if (max !== null) api_url.searchParams.set("min", `${max.getTime()}`);
  const res = await fetch(api_url);
  if (!res.ok) {
    return;
  }
  const jres = await res.json() as {
    times: number[];
    times_diff: number[];
  };
  const times = jres.times_diff ? fromDiff(jres.times_diff) : jres.times;
  const added = await dbMergeTime(
    device_uid,
    times.map((time) => new Date(time)),
  );
  notifyTimeAdded(added);
}
async function sync(device_uid: string) {
  if (syncing.has(device_uid)) return;
  syncing.add(device_uid);
  try {
    await syncLocked(device_uid);
  } finally {
    syncing.delete(device_uid);
  }
}

// TODO: request all devices from all devices
// TODO: DbDevice.last_sync
export function useSync() {
  return {
    sync,
  };
}
