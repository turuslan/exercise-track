import { useCallback, useEffect, useState } from "react";
import { dbInsertTime, dbSelectTimeRange } from "./db";
import { cmp } from "./cmp";
import { Subscribe, Subscriber } from "./subscribe";
import { flatGroup, TimesByDevice } from "./types";
import { useThisDeviceUid } from "./use-this-device";

type SubscribeArg = TimesByDevice;
const subscribe = new Subscribe<SubscribeArg>();

export function notifyTimeAdded(times: SubscribeArg) {
  subscribe.notify(times);
}

export function useTimeRange(min: number, max: number) {
  const [state, setState] = useState<number[] | null>(null);
  useEffect(() => {
    setState(null);
    dbSelectTimeRange(min, max, null)
      .then((times) => setState(flatGroup(times).sort(cmp)));
    return subscribe.subscribe((times) =>
      setState((state) => {
        const added = flatGroup(times).filter((time) =>
          min <= time && time <= max &&
          !state?.includes(time)
        );
        if (added.length === 0) return state;
        return [...state ?? [], ...added].sort(cmp);
      })
    );
  }, [setState, min, max]);
  return state;
}

// TODO: useOnce
export function useAddTime() {
  const [state, setState] = useState<number | null>(null);
  const this_device_uid = useThisDeviceUid();
  useEffect(() => {
    if (this_device_uid === null) return;
    const time = state;
    if (time === null) return;
    dbInsertTime(time).then(() => {
      notifyTimeAdded({ [this_device_uid]: [time] });
      setState(null);
    });
  }, [setState, state, this_device_uid]);
  return useCallback((time: number) => setState(time), [setState]);
}

export function subscribeTimeAdded(subscriber: Subscriber<SubscribeArg>) {
  return subscribe.subscribe(subscriber);
}
