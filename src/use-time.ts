import { useCallback, useEffect, useState } from "react";
import { dbInsertTime, dbSelectTimeRange } from "./db";
import { isEqual as dateEq } from "date-fns";
import { cmp } from "./cmp";
import { Subscribe } from "./subscribe";

const subscribe = new Subscribe<Date[]>();

export function notifyTimeAdded(times: Date[]) {
  subscribe.notify(times);
}

export function useTimeRange(min: Date, max: Date) {
  const [state, setState] = useState<Date[] | null>(null);
  useEffect(() => {
    setState(null);
    dbSelectTimeRange(min, max).then((times) => setState(times));
    return subscribe.subscribe((times) =>
      setState((state) => {
        const added = times.filter((time) =>
          min <= time && time <= max &&
          !state?.find((time2) => dateEq(time2, time))
        );
        if (added.length === 0) return state;
        return [...state ?? [], ...added].sort(cmp);
      })
    );
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
