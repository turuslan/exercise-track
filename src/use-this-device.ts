import { useEffect, useState } from "react";
import { thisDeviceUid } from "./db";

export function useThisDeviceUid() {
  const [state, setState] = useState<string | null>(null);
  useEffect(() => {
    thisDeviceUid().then(setState);
  }, []);
  return state;
}
