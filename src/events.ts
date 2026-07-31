import { Subscribe } from "./subscribe";
import { TimesByDevice } from "./types";

export const eventMdnsChange = new Subscribe<void>();

export const eventTimesAdded = new Subscribe<TimesByDevice>();
