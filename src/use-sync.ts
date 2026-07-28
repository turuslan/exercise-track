import { dbDevices, dbMaxTime, dbMergeTime, dbSelectTimeRange } from "./db";
import { mdns } from "./mdns";
import { mapGroup, TimesByDevice } from "./types";
import { subscribeDeviceAdded } from "./use-device";
import { notifyTimeAdded, subscribeTimeAdded } from "./use-time";

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
export function toDiffGroup(group: TimesByDevice) {
  return mapGroup(group, (uid, times) => [uid, toDiff(times)]);
}
function fromDiffGroup(group: TimesByDevice) {
  return mapGroup(group, (uid, times) => [uid, fromDiff(times)]);
}
export function toResponse(times: TimesByDevice) {
  const diff = false;
  if (diff) {
    return { times_diff: toDiffGroup(times) };
  }
  return { times };
}
function fromResponse(
  res: { times: TimesByDevice; times_diff: TimesByDevice },
) {
  if (res.times_diff) return fromDiffGroup(res.times_diff);
  return res.times;
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
  if (max !== null) api_url.searchParams.set("min", `${max}`);
  const res = await fetch(api_url);
  if (!res.ok) {
    return;
  }
  const times = fromResponse(await res.json());
  const added = await dbMergeTime(device_uid, times[device_uid] ?? []);
  notifyTimeAdded({ [device_uid]: added });
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

// TODO: updating DbDevice.last_sync should update ui, but should not cause sync
async function syncAll() {
  for (const device of await dbDevices()) {
    // TODO: WebSocket if not yet
    await sync(device.uid);
  }
}

// TODO: request all devices from all devices
// TODO: DbDevice.last_sync
export function useSync() {
  return {
    sync,
  };
}

// TODO: re-subscribe message {device_uid,min}[] instead of query
export async function wsServerSession(
  ws: WebSocket,
  min: number,
  device_uid: string,
) {
  function send(times: TimesByDevice) {
    ws.send(JSON.stringify(toResponse(times)));
  }
  let queue: TimesByDevice[] = [];
  const unsubscribe = subscribeTimeAdded((times) => {
    if (queue !== null) {
      queue.push(times);
    } else {
      send(times);
    }
  });
  const times = await dbSelectTimeRange(min, Date.now(), device_uid);
  send(times);
  for (const times of queue) {
    send(times);
  }
  queue = null!;
  ws.onclose = () => unsubscribe();
}

mdns.on(syncAll);
subscribeDeviceAdded(syncAll);
