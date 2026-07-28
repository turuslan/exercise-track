import { useCallback, useEffect, useState } from "react";
import { dbInsertTime, dbSelectTimeRange } from "./db";
import { cmp } from "./cmp";
import { Subscribe, Subscriber } from "./subscribe";

type SubscribeArg = number[];
const subscribe = new Subscribe<SubscribeArg>();

export function notifyTimeAdded(times: SubscribeArg) {
  subscribe.notify(times);
}

export function useTimeRange(min: number, max: number) {
  const [state, setState] = useState<number[] | null>(null);
  useEffect(() => {
    setState(null);
    dbSelectTimeRange(min, max, null).then((times) => setState(times));
    return subscribe.subscribe((times) =>
      setState((state) => {
        const added = times.filter((time) =>
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
  useEffect(() => {
    const time = state;
    if (time === null) return;
    dbInsertTime(time).then(() => {
      notifyTimeAdded([time]);
      setState(null);
    });
  }, [setState, state]);
  return useCallback((time: number) => setState(time), [setState]);
}

export function subscribeTimeAdded(subscriber: Subscriber<SubscribeArg>) {
  return subscribe.subscribe(subscriber);
}
