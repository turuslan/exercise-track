import {
  dbDevices,
  dbMaxTimeAll,
  dbMergeTimeMany,
  dbSelectTimeRangeMany,
} from "./db";
import { eventDevicesChange, eventMdnsChange, eventTimesAdded } from "./events";
import { mdns } from "./mdns";
import {
  DeviceUid,
  filterGroup,
  mapGroup,
  Time,
  TimeByDevice,
  TimesByDevice,
} from "./types";

function objectEmpty(x: any) {
  for (const _ in x) return false;
  return true;
}

function getAddress(device_uid: DeviceUid) {
  return mdns.services[device_uid] ?? null;
}

async function merge(times: TimesByDevice) {
  const added = await dbMergeTimeMany(times);
  eventTimesAdded.notify(added);
}

export function toDiff(times: Time[]) {
  return times.map((x, i, xs) => i === 0 ? x : x - xs[i - 1]);
}
function fromDiff(times_diff: Time[]) {
  const times: Time[] = [];
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

const syncing = new Set<DeviceUid>();

async function syncLocked(device_uid: DeviceUid) {
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
async function sync(device_uid: DeviceUid) {
  if (syncing.has(device_uid)) return;
  syncing.add(device_uid);
  try {
    await syncLocked(device_uid);
  } finally {
    syncing.delete(device_uid);
  }
}

const wss = new Map<DeviceUid, WebSocket>();

function openWs(device_uid: DeviceUid) {
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
  let devices: TimeByDevice = null!;
  function send(times: TimesByDevice) {
    times = filterGroup(
      times,
      (device_uid, times) => times.length !== 0 && (device_uid in devices),
    );
    if (objectEmpty(times)) return;
    ws.send(JSON.stringify(toResponse(times)));
  }
  let queue: TimesByDevice[] = [];
  const unsubscribe = eventTimesAdded.subscribe((times) => {
    if (queue !== null) {
      queue.push(times);
    } else {
      send(times);
    }
  });
  ws.onclose = () => unsubscribe();
  ws.onmessage = async (e) => {
    devices = JSON.parse(e.data);
    const times = await dbSelectTimeRangeMany(devices);
    send(times);
    if (queue !== null) {
      for (const times of queue) {
        send(times);
      }
      queue = null!;
    }
  };
}

eventMdnsChange.subscribe(syncAll);
eventDevicesChange.subscribe(syncAll);
