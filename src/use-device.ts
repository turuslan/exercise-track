import { useEffect, useState } from "react";
import { dbDevices, dbRemoveDevice, dbUpsertDevice } from "./db";
import { cmp } from "./cmp";
import { Subscribe, Subscriber } from "./subscribe";

const subscribe = new Subscribe<void>();

function cmpDevice<T extends { uid: string }>(a: T, b: T) {
  return cmp(a.uid, b.uid);
}

export function useDbDevices() {
  const [state, setState] = useState({
    read_nonce: 0,
    devices: [] as { uid: string; name: string }[],
    upsert: null as { uid: string; name: string } | null,
    remove: null as string | null,
  });
  useEffect(
    () =>
      subscribe.subscribe(() =>
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
      subscribe.notify();
      setState((state) => ({ ...state, upsert: null }));
    });
  }, [state.upsert]);
  useEffect(() => {
    const { remove } = state;
    if (remove === null) return;
    dbRemoveDevice(remove).then(() => {
      subscribe.notify();
      setState((state) => ({ ...state, remove: null }));
    });
  }, [state.remove]);
  return {
    devices: state.devices,
    upsert(uid: string, name: string) {
      setState((state) => ({ ...state, upsert: { uid, name } }));
    },
    remove(uid: string) {
      setState((state) => ({ ...state, remove: uid }));
    },
  };
}

export function subscribeDeviceAdded(subscriber: Subscriber<void>) {
  return subscribe.subscribe(subscriber);
}
