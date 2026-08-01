import { useEffect, useState } from "react";
import { thisDeviceUid, thisDeviceUidNull } from "./db";

export function useThisDeviceUid() {
  const [state, setState] = useState(thisDeviceUidNull());
  useEffect(() => {
    if (state !== null) return;
    thisDeviceUid().then(setState);
  }, []);
  return state;
}
