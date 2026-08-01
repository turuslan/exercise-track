export type Time = number;
export type DeviceUid = string;
export type TimeByDevice = Record<DeviceUid, Time>;
export type MaxTimeByDevice = Record<DeviceUid, Time | null>;
export type TimesByDevice = Record<DeviceUid, Time[]>;

export function mapGroup<T, U>(
  group: Record<string, T>,
  f: (k: string, v: T) => [string, U],
) {
  return Object.fromEntries(Object.entries(group).map(([k, v]) => f(k, v)));
}
export function filterGroup<T>(
  group: Record<string, T>,
  f: (k: string, v: T) => boolean,
) {
  return Object.fromEntries(Object.entries(group).filter(([k, v]) => f(k, v)));
}
export function flatGroup<T>(group: Record<string, T[]>) {
  return Object.values(group).flat();
}
