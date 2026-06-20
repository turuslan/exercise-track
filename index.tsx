import * as React from "npm:react";

import {
  addDays,
  addMinutes,
  addSeconds,
  differenceInMilliseconds,
  differenceInMinutes,
  differenceInSeconds,
  isEqual as dateEq,
  startOfDay,
  startOfMinute,
  startOfSecond,
} from "npm:date-fns";
import { Box, render, Text, useInput } from "npm:ink";
// @deno-types="npm:@types/react"
import { useEffect, useReducer, useRef } from "npm:react";
import { dbInsertTime, dbSelectTimeRange } from "./src/db.ts";

function roundUp(x: number, y: number) {
  return x - x % y + y;
}
function arrayLast<T>(xs: T[]) {
  return xs[xs.length - 1] ?? null;
}

function pad02(n: number) {
  return `${n}`.padStart(2, "0");
}

function hhmm(time: Date) {
  return `${pad02(time.getHours())}:${pad02(time.getMinutes())}`;
}
function mmdd(time: Date) {
  return `${pad02(time.getMonth() + 1)}.${pad02(time.getDate())}`;
}

function cmp<T>(l: T, r: T) {
  return l < r ? -1 : l > r ? +1 : 0;
}

const DUPLICATE_TIME_MS = 5000;
const TARGET = 10;

interface TimeScale {
  getGroup(time: Date): Date;
  nextGroup(time: Date): Date;
  nextTick(time: Date): Date;
  ago(time1: Date, time2: Date): string;
}
const time_scale_debug: TimeScale = {
  getGroup(time) {
    return startOfMinute(time);
  },
  nextGroup(time) {
    return addMinutes(this.getGroup(time), 1);
  },
  nextTick(time) {
    return addSeconds(startOfSecond(time), 1);
  },
  ago(time1, time2) {
    return `${differenceInSeconds(time2, time1)}s ago`;
  },
};
const time_scale_real: TimeScale = {
  getGroup(time) {
    return startOfDay(time);
  },
  nextGroup(time) {
    return addDays(this.getGroup(time), 1);
  },
  nextTick(time) {
    // timers seem to freeze when computer sleeps
    if (true) {
      const minute = startOfMinute(time);
      const seconds = differenceInSeconds(time, minute);
      return addSeconds(minute, roundUp(seconds, 5));
    }
    return addMinutes(startOfMinute(time), 1);
  },
  ago(time1, time2) {
    const minutes = differenceInMinutes(time2, time1);
    const hours = minutes / 60 | 0;
    return `${pad02(hours)}:${pad02(minutes % 60)} ago`;
  },
};
const time_scale = time_scale_real;

interface State {
  day: Date;
  count: number;
  target: number;
  last: Date | null;
}

type ReducerArg = "key" | "timer";

function App() {
  const [state, dispatch] = useReducer<State, null, [ReducerArg]>(
    (state, arg) => {
      const now = new Date();
      if (!dateEq(state.day, time_scale.getGroup(now))) {
        state = {
          ...state,
          day: time_scale.getGroup(now),
          count: 0,
          target: TARGET,
          last: null,
        };
      }
      if (arg === "key") {
        if (
          state.last !== null &&
          differenceInMilliseconds(now, state.last) < DUPLICATE_TIME_MS
        ) {
          return state;
        }
        dbInsertTime(now);
        return {
          ...state,
          count: state.count + 1,
          last: now,
        };
      }
      if (arg === "timer") {
        return { ...state };
      }
      throw new Error();
    },
    null,
    () => {
      const now = new Date();
      const day = time_scale.getGroup(now);
      const times = dbSelectTimeRange(
        time_scale.getGroup(day),
        time_scale.nextGroup(day),
      );
      const day_times = times
        .filter((time) => dateEq(time_scale.getGroup(time), day));
      return {
        day,
        count: day_times.length,
        target: TARGET,
        last: arrayLast(day_times),
      };
    },
  );
  const timer = useRef<number>(null);
  useEffect(() => {
    (function f() {
      const now = new Date();
      timer.current = setTimeout(() => {
        f();
        dispatch("timer");
      }, differenceInMilliseconds(time_scale.nextTick(now), now));
    })();
    return () => {
      if (timer.current !== null) {
        clearTimeout(timer.current);
        timer.current = null;
      }
    };
  }, []);
  useInput((input, key) => {
    if (key.return) {
      dispatch("key");
    }
  });
  const now = new Date();
  return (
    <Box flexDirection="column">
      <Box>
        <Text>
          {`${mmdd(now)} ${hhmm(now)}, ${state.count}/${state.target} `}
        </Text>
        <Text>
          {`[${"#".repeat(Math.min(state.count, state.target))}${
            ".".repeat(Math.max(0, state.target - state.count))
          }${"+".repeat(Math.max(0, state.count - state.target))}]`}
        </Text>
        {state.last && <Text>, {time_scale.ago(state.last, new Date())}</Text>}
      </Box>
    </Box>
  );
}

render(<App />);

await Deno.serve({ port: 8087, onListen() {} }, async (req) => {
  const url = new URL(req.url);
  if (url.pathname === "/api/list") {
    const times = dbSelectTimeRange(new Date(0), new Date())
      .map((x) => x.getTime());
    return Response.json({ times });
  }
  return new Response(null, { status: 404 });
});
