import { cmp } from "./cmp";
import { dbDevices, dbMaxTime, dbMergeTime, dbSelectTimeRange } from "./db";
import { mdns } from "./mdns";
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
  min: Date,
  device_uid: string,
) {
  function send(times: Date[]) {
    ws.send(
      JSON.stringify({
        times: times.map((time) => time.getTime()).sort(cmp),
      }),
    );
  }
  let queue: Date[][] = [];
  const unsubscribe = subscribeTimeAdded((times) => {
    if (queue !== null) {
      queue.push(times);
    } else {
      send(times);
    }
  });
  const times = await dbSelectTimeRange(min, new Date(), device_uid);
  send(times);
  for (const times of queue) {
    send(times);
  }
  queue = null!;
  ws.onclose = () => unsubscribe();
}

mdns.on(syncAll);
subscribeDeviceAdded(syncAll);
