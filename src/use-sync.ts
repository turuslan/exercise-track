import {
  dbDevices,
  dbMaxTimeAll,
  dbMergeTimeMany,
  dbSelectTimeRangeMany,
} from "./db";
import { mdns } from "./mdns";
import { mapGroup, TimeByDevice, TimesByDevice } from "./types";
import { subscribeDeviceAdded } from "./use-device";
import { notifyTimeAdded, subscribeTimeAdded } from "./use-time";

function getAddress(device_uid: string) {
  const addresses = mdns.services[device_uid] ?? [];
  if (addresses.length === 0) {
    return null;
  }
  return addresses[0];
}

async function merge(times: TimesByDevice) {
  const added = await dbMergeTimeMany(times);
  notifyTimeAdded(added);
}

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
  const address = getAddress(device_uid);
  if (address === null) return;
  const api_url = new URL(`http://${address}/api/sync`);
  const devices = await dbMaxTimeAll();
  api_url.searchParams.set("devices", JSON.stringify(devices));
  const res = await fetch(api_url);
  if (!res.ok) {
    return;
  }
  const times = fromResponse(await res.json());
  await merge(times);
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

const wss = new Map<string, WebSocket>();

function openWs(device_uid: string) {
  if (wss.has(device_uid)) return;
  const address = getAddress(device_uid);
  if (address === null) return;
  const ws = new WebSocket(`ws://${address}/ws`);
  wss.set(device_uid, ws);
  ws.onopen = async () => {
    const devices = await dbMaxTimeAll();
    ws.send(JSON.stringify(devices));
  };
  ws.onmessage = async (e) => {
    const times = fromResponse(JSON.parse(e.data));
    await merge(times);
  };
  ws.onclose = () => wss.delete(device_uid);
}

async function syncAll() {
  for (const device of await dbDevices()) {
    openWs(device.uid);
  }
}

export function useSync() {
  return {
    sync,
  };
}

export async function wsServerSession(ws: WebSocket) {
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
  ws.onclose = () => unsubscribe();
  const devices = await new Promise<TimeByDevice>((resolve) => {
    ws.onmessage = (e) => {
      resolve(JSON.parse(e.data));
      ws.onmessage = null;
    };
  });
  const times = await dbSelectTimeRangeMany(devices);
  send(times);
  for (const times of queue) {
    send(times);
  }
  queue = null!;
}

mdns.on(syncAll);
subscribeDeviceAdded(syncAll);
