import { useCallback, useEffect, useState } from "react";
import { dbInsertTime, dbSelectTimeRange } from "./db";
import { isEqual as dateEq } from "date-fns";
import { cmp } from "./cmp";

type Subscriber = (times: Date[]) => void;

const subscribers = new Set<Subscriber>();

export function notifyTimeAdded(times: Date[]) {
  for (const subscriber of subscribers) {
    subscriber(times);
  }
}

export function useTimeRange(min: Date, max: Date) {
  const [state, setState] = useState<Date[] | null>(null);
  useEffect(() => {
    setState(null);
    dbSelectTimeRange(min, max).then((times) => setState(times));
    const subscriber: Subscriber = (times) =>
      setState((state) => {
        const added = times.filter((time) =>
          min <= time && time <= max &&
          !state?.find((time2) => dateEq(time2, time))
        );
        if (added.length === 0) return state;
        return [...state ?? [], ...added].sort(cmp);
      });
    subscribers.add(subscriber);
    return () => {
      subscribers.delete(subscriber);
    };
  }, [setState, min.getTime(), max.getTime()]);
  return state;
}

// TODO: useOnce
export function useAddTime() {
  const [state, setState] = useState<Date | null>(null);
  useEffect(() => {
    const time = state;
    if (time === null) return;
    dbInsertTime(time).then(() => {
      notifyTimeAdded([time]);
      setState(null);
    });
  }, [setState, state?.getTime()]);
  return useCallback((time: Date) => setState(time), [setState]);
}
