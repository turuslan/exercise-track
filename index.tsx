import * as React from "react";

import {
  addDays,
  addMinutes,
  addSeconds,
  differenceInMilliseconds,
  differenceInMinutes,
  differenceInSeconds,
  startOfDay,
  startOfMinute,
  startOfSecond,
} from "date-fns";
import { Box, render, Text, useInput } from "ink";
import { StrictMode, useEffect, useRef, useState } from "react";
import { dbSelectTimeRange, thisDeviceUid } from "./src/db";
import Koa from "koa";
import KoaRouter from "@koa/router";
import * as Mdns from "./src/mdns";
import { useAddTime, useTimeRange } from "./src/use-time";

const port = 8087;

const mdns = new Mdns.Mdns(port);

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

function useMdnsServices() {
  const [state, setState] = useState<Mdns.Services>(mdns.services);
  useEffect(() => mdns.on(setState), [setState]);
  return state;
}

function useThisDeviceUid() {
  const [state, setState] = useState<string | null>(null);
  useEffect(() => {
    thisDeviceUid().then(setState);
  }, []);
  return state;
}

function useAlignedTimer(next: (now: Date) => Date) {
  const [state, setState] = useState(0);
  const timer = useRef<NodeJS.Timeout>(null);
  useEffect(() => {
    (function f() {
      const now = new Date();
      timer.current = setTimeout(() => {
        f();
        setState((state) => state + 1);
      }, differenceInMilliseconds(next(now), now));
    })();
    return () => {
      if (timer.current !== null) {
        clearTimeout(timer.current);
        timer.current = null;
      }
    };
  }, []);
}

function App() {
  const this_device_uid = useThisDeviceUid();
  useAlignedTimer((now) => time_scale.nextTick(now));
  const now = new Date();
  const day = time_scale.getGroup(now);
  const times = useTimeRange(
    time_scale.getGroup(day),
    time_scale.nextGroup(day),
  );
  const count = times === null ? null : times.length;
  const last = times === null ? null : arrayLast(times);
  const add_time = useAddTime();
  useInput((input, key) => {
    if (key.return) {
      const now = new Date();
      if (
        last === null ||
        differenceInMilliseconds(now, last) >= DUPLICATE_TIME_MS
      ) {
        add_time(now);
      }
      return;
    }
  });

  const mdns_services = useMdnsServices();

  return (
    <Box flexDirection="column">
      <Box>
        <Text>
          {`${mmdd(now)} ${hhmm(now)}, ${count ?? "??"}/${TARGET} `}
        </Text>
        <Text>
          {`[${"#".repeat(Math.min(count ?? 0, TARGET))}${
            ".".repeat(Math.max(0, TARGET - (count ?? 0)))
          }${"+".repeat(Math.max(0, (count ?? 0) - TARGET))}]`}
        </Text>
        {last !== null && <Text>, {time_scale.ago(last, new Date())}</Text>}
      </Box>
      <Box>
        <Text>device uid: {this_device_uid ?? "???"}</Text>
      </Box>
      {Object.entries(mdns_services)
        .map(([uid, addresses]) => (
          <Box key={uid}>
            <Text>- {uid} {JSON.stringify(addresses)}</Text>
          </Box>
        ))}
    </Box>
  );
}

const ink = render(
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
const server = app.listen(port);

ink.waitUntilExit().then(() => {
  server.close();
  mdns.stop();
});

// TODO: timer+mdns => sync
