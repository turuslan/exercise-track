import { useCallback, useEffect, useState } from "react";
import { dbInsertTime, dbSelectTimeRange } from "./db";
import { cmp } from "./cmp";
import { flatGroup } from "./types";
import { useThisDeviceUid } from "./use-this-device";
import { eventTimesAdded } from "./events";

export function useTimeRange(min: number, max: number) {
  const [state, setState] = useState<number[]>([]);
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
  const [state, setState] = useState<number | null>(null);
  const this_device_uid = useThisDeviceUid();
  useEffect(() => {
    if (this_device_uid === null) return;
    const time = state;
    if (time === null) return;
    dbInsertTime(time).then(() => {
      eventTimesAdded.notify({ [this_device_uid]: [time] });
      setState(null);
    });
  }, [setState, state, this_device_uid]);
  return useCallback((time: number) => setState(time), [setState]);
}
