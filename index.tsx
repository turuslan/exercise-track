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
import { Box, Key, render, Text, useInput } from "ink";
import {
  StrictMode,
  useEffect,
  useEffectEvent,
  useMemo,
  useRef,
  useState,
} from "react";
import {
  dbDevices,
  dbRemoveDevice,
  dbSelectTimeRange,
  dbUpsertDevice,
  thisDeviceUid,
} from "./src/db";
import Koa from "koa";
import KoaRouter from "@koa/router";
import * as Mdns from "./src/mdns";
import { useAddTime, useTimeRange } from "./src/use-time";
import { cmp } from "./src/cmp";
import { UncontrolledTextInput } from "ink-text-input";

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

interface PropOnInput {
  onInput?(input: string, key: Key): void;
}

function UiDay(props: PropOnInput) {
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
    props.onInput?.(input, key);
  });
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
    </Box>
  );
}

function multiIndexOf<T>(xss: T[][], x: T) {
  let offset = 0;
  for (const xs of xss) {
    const i = xs.indexOf(x);
    if (i !== -1) {
      return offset + i;
    }
    offset += xs.length;
  }
  return -1;
}
function multiLength<T>(xss: T[][]) {
  return xss.reduce((sum, xs) => sum + xs.length, 0);
}
function multiBound<T>(xss: T[][], i: number) {
  return 0 <= i && i < multiLength(xss);
}
function multiGet<T>(xss: T[][], i: number) {
  let begin = 0;
  if (i >= 0) {
    for (const xs of xss) {
      const end = begin + xs.length;
      if (begin <= i && i < end) return xs[i - begin];
      begin = end;
    }
  }
  throw new Error();
}

function cmpDevice<T extends { uid: string }>(a: T, b: T) {
  return cmp(a.uid, b.uid);
}
// TODO: move up, needed for sync
function useDbDevices() {
  const [state, setState] = useState({
    read_nonce: 0,
    devices: [] as { uid: string; name: string }[],
    upsert: null as { uid: string; name: string } | null,
    remove: null as string | null,
  });
  useEffect(() => {
    dbDevices().then((devices) => {
      setState((state) => ({ ...state, devices: devices.toSorted(cmpDevice) }));
    });
  }, [state.read_nonce]);
  useEffect(() => {
    const { upsert } = state;
    if (upsert === null) return;
    dbUpsertDevice(upsert.uid, upsert.name)
      .then(() =>
        setState((state) => ({
          ...state,
          upsert: null,
          read_nonce: state.read_nonce + 1,
        }))
      );
  }, [state.upsert]);
  useEffect(() => {
    const { remove } = state;
    if (remove === null) return;
    dbRemoveDevice(remove)
      .then(() =>
        setState((state) => ({
          ...state,
          remove: null,
          read_nonce: state.read_nonce + 1,
        }))
      );
  }, [state.remove]);
  return {
    devices: state.devices,
    upsert(uid: string, name: string) {
      setState((state) => ({ ...state, upsert: { uid, name } }));
    },
    remove(uid: string) {
      setState((state) => ({ ...state, remove: uid }));
    },
  };
}
function UiDevices(props: PropOnInput) {
  const db_devices = useDbDevices();
  const [state, setState] = useState({
    selected: null as { uid: string; multi_i: number } | null,
    editing: null as string | null,
  });
  const this_device_uid = useThisDeviceUid();
  const mdns_services = useMdnsServices();
  const [added_uids, unknown_uids] = useMemo(() => {
    const added_uids = db_devices.devices
      .map((device) => device.uid)
      .sort();
    const unknown_uids = Object.keys(mdns_services)
      .filter((uid) => !added_uids.includes(uid))
      .sort();
    return [added_uids, unknown_uids];
  }, [db_devices.devices, mdns_services]);
  const multi_uids = [added_uids, unknown_uids];
  function select(multi_i: number) {
    if (!multiBound(multi_uids, multi_i)) return null;
    const uid = multiGet(multi_uids, multi_i);
    return { uid, multi_i };
  }
  useEffect(() =>
    setState((state) => {
      if (state.selected === null) {
        const selected = select(0);
        if (selected === null) return state;
        return { ...state, selected };
      }
      let i = multiIndexOf(multi_uids, state.selected.uid);
      if (i === -1) {
        return { ...state, selected: select(state.selected.multi_i) };
      }
      if (state.selected.multi_i !== i) {
        return { ...state, selected: { uid: state.selected.uid, multi_i: i } };
      }
      return state;
    }), [state.selected, added_uids, unknown_uids]);
  useInput((input, key) => {
    if (state.editing !== null) {
      if (key.escape) {
        setState((state) => ({ ...state, editing: null }));
        return;
      }
      return;
    }
    if (key.upArrow || key.downArrow) {
      if (state.selected === null) return;
      const delta = key.upArrow ? -1 : +1;
      const i = state.selected.multi_i + delta;
      if (!multiBound(multi_uids, i)) return;
      setState((state) => ({ ...state, selected: select(i) }));
      return;
    }
    if (input === "=") {
      const { selected } = state;
      if (selected === null) return;
      if (added_uids.includes(selected.uid)) return;
      db_devices.upsert(selected.uid, selected.uid);
      return;
    }
    if (input === "-") {
      const { selected } = state;
      if (selected === null) return;
      if (!added_uids.includes(selected.uid)) return;
      db_devices.remove(selected.uid);
      return;
    }
    if (input === "e") {
      const { selected } = state;
      if (selected === null) return;
      if (!added_uids.includes(selected.uid)) return;
      setState((state) => ({ ...state, editing: selected.uid }));
      return;
    }
    props.onInput?.(input, key);
  });
  function arrow(uid: string) {
    return uid === state.selected?.uid ? ">" : " ";
  }
  return (
    <Box flexDirection="column">
      <Box>
        <Text>this device uid: {this_device_uid ?? "???"}</Text>
      </Box>
      <Box>
        <Text>added devices:</Text>
      </Box>
      {db_devices.devices
        .map((device) => (
          <Box key={device.uid}>
            <Text>
              {`${arrow(device.uid)} ${
                device.uid in mdns_services ? "+" : "-"
              } ${device.uid} `}
            </Text>
            {device.uid === state.editing
              ? (
                <>
                  <Text inverse>[</Text>
                  <UncontrolledTextInput
                    initialValue={device.name}
                    onSubmit={(name) => {
                      console.info("onSubmit", [device.uid, name]);
                      db_devices.upsert(device.uid, name);
                      setState((state) => ({ ...state, editing: null }));
                    }}
                  />
                  <Text inverse>]</Text>
                </>
              )
              : <Text>"{device.name}"</Text>}
          </Box>
        ))}
      <Box>
        <Text>unknown devices:</Text>
      </Box>
      {unknown_uids
        .map((uid) => (
          <Box key={uid}>
            <Text>{`${arrow(uid)} ? ${uid}`}</Text>
          </Box>
        ))}
    </Box>
  );
}

function App() {
  const [state, setState] = useState({ devices: false });
  const onInput = useEffectEvent((input: string, key: Key) => {
    if (input === "d") {
      setState((state) => ({ ...state, devices: !state.devices }));
      return;
    }
  });
  return (
    <Box flexDirection="column">
      {state.devices
        ? <UiDevices onInput={onInput} />
        : <UiDay onInput={onInput} />}
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
