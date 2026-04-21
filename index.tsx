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
import { DatabaseSync } from "node:sqlite";

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

const db = new DatabaseSync("exercise.db");
db.exec(`
  CREATE TABLE IF NOT EXISTS time (
    time_ms INTEGER UNIQUE
  );
`);
const db_insert_time = db.prepare(`
  INSERT INTO time (time_ms) VALUES (?)
`);
const db_select_time = db.prepare(`
  SELECT time_ms FROM time
  ORDER BY time_ms
`);
const db_select_time_range = db.prepare(`
  SELECT time_ms FROM time
  WHERE ? <= time_ms AND time_ms <= ?
  ORDER BY time_ms
`);

function App() {
  const [state, dispatch] = useReducer<State, null, [ReducerArg]>(
    (state, arg) => {
      const now = new Date();
      if (arg === "key") {
        if (
          state.last !== null &&
          differenceInMilliseconds(now, state.last) < DUPLICATE_TIME_MS
        ) {
          return state;
        }
        db_insert_time.run(now.getTime());
        return {
          ...state,
          count: state.count + 1,
          last: now,
        };
      }
      if (arg === "timer") {
        if (!dateEq(state.day, time_scale.getGroup(now))) {
          return {
            ...state,
            day: time_scale.getGroup(now),
            count: 0,
            target: TARGET,
            last: null,
          };
        }
        return { ...state };
      }
      throw new Error();
    },
    null,
    () => {
      const now = new Date();
      const day = time_scale.getGroup(now);
      const times = db_select_time_range.all(
        time_scale.getGroup(day).getTime(),
        time_scale.nextGroup(day).getTime(),
      )
        .map((row) => new Date(row.time_ms as number));
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
          {`${mmdd(now)} ${hhmm(now)}, ${state.count}/${state.target}`}
        </Text>
        {state.last && <Text>, {time_scale.ago(state.last, new Date())}</Text>}
      </Box>
    </Box>
  );
}

render(<App />);
