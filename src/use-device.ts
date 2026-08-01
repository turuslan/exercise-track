import { useEffect, useState } from "react";
import { DbDevice, dbDevices, dbRemoveDevice, dbUpsertDevice } from "./db";
import { cmp } from "./cmp";
import { eventDevicesChange } from "./events";
import { DeviceUid } from "./types";

function cmpDevice<T extends { uid: DeviceUid }>(a: T, b: T) {
  return cmp(a.uid, b.uid);
}

export function useDbDevices() {
  const [state, setState] = useState({
    read_nonce: 0,
    devices: [] as DbDevice[],
  });
  useEffect(
    () =>
      eventDevicesChange.subscribe(() =>
        setState((state) => ({ ...state, read_nonce: state.read_nonce + 1 }))
      ),
    [],
  );
  useEffect(() => {
    dbDevices().then((devices) => {
      setState((state) => ({ ...state, devices: devices.toSorted(cmpDevice) }));
    });
  }, [state.read_nonce]);
  return state.devices;
}

export async function upsertDevice(uid: DeviceUid, name: string) {
  await dbUpsertDevice(uid, name);
  eventDevicesChange.notify();
}

export async function removeDevice(uid: DeviceUid) {
  await dbRemoveDevice(uid);
  eventDevicesChange.notify();
}
