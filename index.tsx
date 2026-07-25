import * as React from "react";

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
} from "date-fns";
import { Box, render, Text, useInput } from "ink";
import { StrictMode, useEffect, useReducer, useRef } from "react";
import { dbInsertTime, dbSelectTimeRange } from "./src/db";
import Koa from "koa";
import KoaRouter from "@koa/router";
import * as Mdns from "mdns";

const port = 8087;
const mdns_type = "exercise-track";

function nullMap<T, U>(value: T | null, f: (value: T) => U) {
  return value === null ? null : f(value);
}
function koaQuery(ctx: Koa.Context, key: string) {
  let value = ctx.request.query[key];
  if (Array.isArray(value)) [value] = value;
  return value ?? null;
}
function koaQueryInt(ctx: Koa.Context, key: string) {
  return nullMap(koaQuery(ctx, key), (s) => {
    const i = +s;
    if (isNaN(i)) throw new Error();
    return i;
  });
}
function koaQueryDate(ctx: Koa.Context, key: string) {
  const i = koaQueryInt(ctx, key);
  return i === null ? null : new Date(i);
}

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
  loading: boolean;
  day: Date;
  count: number;
  target: number;
  last: Date | null;
  insert: Date | null;
}

type ReducerArg =
  | { type: "key" }
  | { type: "timer" }
  | {
    type: "loaded";
    count: number;
    last: Date | null;
  }
  | { type: "inserted" };

function App() {
  const [state, dispatch] = useReducer<State, null, [ReducerArg]>(
    (state, arg) => {
      const now = new Date();
      if (arg.type === "loaded") {
        return { ...state, count: arg.count, last: arg.last };
      }
      if (arg.type === "inserted") {
        return { ...state, insert: null };
      }
      if (!dateEq(state.day, time_scale.getGroup(now))) {
        state = {
          ...state,
          day: time_scale.getGroup(now),
          count: null!,
          target: TARGET,
          last: null,
        };
      }
      if (arg.type === "key") {
        if (state.insert !== null) {
          return state;
        }
        if (
          state.last !== null &&
          differenceInMilliseconds(now, state.last) < DUPLICATE_TIME_MS
        ) {
          return state;
        }
        return {
          ...state,
          count: state.count + 1,
          last: now,
          insert: now,
        };
      }
      if (arg.type === "timer") {
        return { ...state };
      }
      throw new Error();
    },
    null,
    () => {
      const now = new Date();
      const day = time_scale.getGroup(now);
      return {
        loading: true,
        day,
        count: null!,
        target: TARGET,
        last: null,
        insert: null,
      };
    },
  );
  useEffect(() => {
    const abort = new AbortController();
    dbSelectTimeRange(
      time_scale.getGroup(state.day),
      time_scale.nextGroup(state.day),
    ).then((times) => {
      const day_times = times
        .filter((time) => dateEq(time_scale.getGroup(time), state.day));
      dispatch({
        type: "loaded",
        count: day_times.length,
        last: arrayLast(day_times),
      });
    });
    return () => abort.abort();
  }, [state.day]);
  useEffect(() => {
    if (state.insert === null) return;
    dbInsertTime(state.insert).then(() => {
      dispatch({ type: "inserted" });
    });
  }, [state.insert]);
  const timer = useRef<NodeJS.Timeout>(null);
  useEffect(() => {
    (function f() {
      const now = new Date();
      timer.current = setTimeout(() => {
        f();
        dispatch({ type: "timer" });
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
      dispatch({ type: "key" });
    }
  });
  const now = new Date();
  return (
    <Box flexDirection="column">
      <Box>
        <Text>
          {`${mmdd(now)} ${hhmm(now)}, ${state.count ?? "??"}/${state.target} `}
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

render(
  <StrictMode>
    <App />
  </StrictMode>,
);

const router = new KoaRouter();
router.get("/api/list", async (ctx) => {
  const arg_min = koaQueryDate(ctx, "min") ?? new Date(0);
  const arg_max = koaQueryDate(ctx, "max") ?? new Date();
  const times = (await dbSelectTimeRange(arg_min, arg_max))
    .map((x) => x.getTime());
  const diff = false;
  if (diff) {
    ctx.body = { times };
  } else {
    const times_diff = times.map((x, i, xs) => i === 0 ? x : x - xs[i - 1]);
    ctx.body = { times_diff };
  }
});

const app = new Koa();
app.use(router.routes());
app.listen(port);

// TODO: { name: "DEVICE_ID" }
const mdns_advertise = Mdns.createAdvertisement(Mdns.tcp(mdns_type), port);
mdns_advertise.start();
