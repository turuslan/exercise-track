import { useCallback, useEffect, useState } from "react";
import { dbInsertTime, dbSelectTimeRange } from "./db";
import { cmp } from "./cmp";
import { flatGroup, Time } from "./types";
import { eventTimesAdded } from "./events";

export function useTimeRange(min: Time, max: Time) {
  const [state, setState] = useState<Time[]>([]);
  useEffect(() => {
    dbSelectTimeRange(min, max, null)
      .then((times) => setState(flatGroup(times).sort(cmp)));
    return eventTimesAdded.subscribe((times) =>
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
  const [state, setState] = useState<Time | null>(null);
  useEffect(() => {
    const time = state;
    if (time === null) return;
    dbInsertTime(time).then((added) => {
      eventTimesAdded.notify(added);
      setState(null);
    });
  }, [setState, state]);
  return useCallback((time: Time) => setState(time), [setState]);
}
