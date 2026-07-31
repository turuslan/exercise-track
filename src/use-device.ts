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
    upsert: null as { uid: DeviceUid; name: string } | null,
    remove: null as DeviceUid | null,
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
  useEffect(() => {
    const { upsert } = state;
    if (upsert === null) return;
    dbUpsertDevice(upsert.uid, upsert.name).then(() => {
      eventDevicesChange.notify();
      setState((state) => ({ ...state, upsert: null }));
    });
  }, [state.upsert]);
  useEffect(() => {
    const { remove } = state;
    if (remove === null) return;
    dbRemoveDevice(remove).then(() => {
      eventDevicesChange.notify();
      setState((state) => ({ ...state, remove: null }));
    });
  }, [state.remove]);
  return {
    devices: state.devices,
    upsert(uid: DeviceUid, name: string) {
      setState((state) => ({ ...state, upsert: { uid, name } }));
    },
    remove(uid: DeviceUid) {
      setState((state) => ({ ...state, remove: uid }));
    },
  };
}
