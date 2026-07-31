import { useEffect, useState } from "react";
import { thisDeviceUid } from "./db";
import { DeviceUid } from "./types";

export function useThisDeviceUid() {
  const [state, setState] = useState<DeviceUid | null>(null);
  useEffect(() => {
    thisDeviceUid().then(setState);
  }, []);
  return state;
}
