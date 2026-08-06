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
import { dbSelectTimeRange } from "./src/db";
import Koa from "koa";
import KoaRouter from "@koa/router";
import { mdns, Services } from "./src/mdns";
import { addTime, useTimeRange } from "./src/use-time";
import { UncontrolledTextInput } from "ink-text-input";
import { removeDevice, upsertDevice, useDbDevices } from "./src/use-device";
import { closeAllWs, toResponse, wsServerSession } from "./src/use-sync";
import { debugPort } from "./src/debug";
import koaWs from "koa-websocket";
import { useThisDeviceUid } from "./src/use-this-device";
import { eventMdnsChange } from "./src/events";

const port = debugPort() ?? 8087;

function nullMap<T, U>(value: T | null, f: (value: T) => U) {
  return value === null ? null : f(value);
}
function koaQuery(ctx: Koa.ExtendableContext, key: string) {
  let value = ctx.request.query[key];
  if (Array.isArray(value)) [value] = value;
  return value ?? null;
}
function koaQueryInt(ctx: Koa.ExtendableContext, key: string) {
  return nullMap(koaQuery(ctx, key), (s) => {
    const i = +s;
    if (isNaN(i)) throw new Error();
    return i;
  });
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
  const [state, setState] = useState<Services>(mdns.services);
  useEffect(() => eventMdnsChange.subscribe(() => setState(mdns.services)), []);
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

function UiDay(props: PropOnInput & { focus: boolean }) {
  useAlignedTimer((now) => time_scale.nextTick(now));
  const now = new Date();
  const day = time_scale.getGroup(now);
  const times = useTimeRange(
    time_scale.getGroup(day).getTime(),
    time_scale.nextGroup(day).getTime(),
  );
  const count = times.length;
  const last = arrayLast(times);
  useInput((input, key) => {
    if (key.return) {
      const now = new Date();
      if (
        last === null ||
        differenceInMilliseconds(now, last) >= DUPLICATE_TIME_MS
      ) {
        addTime(now.getTime());
      }
      return;
    }
    props.onInput?.(input, key);
  }, { isActive: props.focus ?? true });
  const bar1 = "#".repeat(Math.min(count, TARGET));
  const bar2 = ".".repeat(Math.max(0, TARGET - count));
  const bar3 = "+".repeat(Math.max(0, count - TARGET));
  const ago = last !== null ? time_scale.ago(new Date(last), now) : "";
  return (
    <Box flexDirection="column">
      <Box>
        <Text color={props.focus ? undefined : "gray"}>
          {`${mmdd(now)} ${
            hhmm(now)
          }, ${count}/${TARGET} [${bar1}${bar2}${bar3}] ${ago}`}
        </Text>
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

function UiDevices(props: PropOnInput) {
  const db_devices = useDbDevices();
  const [state, setState] = useState({
    selected: null as { uid: string; multi_i: number } | null,
    editing: null as string | null,
  });
  const this_device_uid = useThisDeviceUid();
  const mdns_services = useMdnsServices();
  const [added_uids, unknown_uids] = useMemo(() => {
    const added_uids = db_devices
      .map((device) => device.uid)
      .sort();
    const unknown_uids = Object.keys(mdns_services)
      .filter((uid) => !added_uids.includes(uid))
      .sort();
    return [added_uids, unknown_uids];
  }, [db_devices, mdns_services]);
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
      upsertDevice(selected.uid, selected.uid);
      return;
    }
    if (input === "-") {
      const { selected } = state;
      if (selected === null) return;
      if (!added_uids.includes(selected.uid)) return;
      removeDevice(selected.uid);
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
      {db_devices
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
                      upsertDevice(device.uid, name);
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
    if (state.devices && key.escape) {
      setState((state) => ({ ...state, devices: false }));
      return;
    }
  });
  return (
    <Box flexDirection="column">
      <UiDay onInput={onInput} focus={!state.devices} />
      {state.devices ? <UiDevices onInput={onInput} /> : null}
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
  const arg_min = koaQueryInt(ctx, "min") ?? 0;
  const arg_max = koaQueryInt(ctx, "max");
  const arg_device_uid = koaQuery(ctx, "device_uid");
  const times = await dbSelectTimeRange(arg_min, arg_max, arg_device_uid);
  ctx.body = toResponse(times);
});

const ws_router = new KoaRouter();
ws_router.get("/ws", async (ctx, next) => {
  const ws = ctx.websocket as WebSocket;
  wsServerSession(ws);
  return next;
});

const app = koaWs(new Koa());
app.use(router.routes());
app.ws.use(ws_router.routes());
const server = app.listen(port);

mdns.advertise(port);

ink.waitUntilExit().then(() => {
  closeAllWs();
  server.close();
  mdns.stop();
});
