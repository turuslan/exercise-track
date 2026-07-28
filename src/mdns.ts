import * as Mdns2 from "mdns";
import { thisDeviceUid } from "./db";

const type = "exercise-track";

export type Services = Record<string, string[]>;
type ServicesCb = (services: Services) => void;

export class Mdns {
  advertisement: Mdns2.Advertisement = null!;
  browser: Mdns2.Browser = null!;
  services: Services = {};
  callbacks = new Set<ServicesCb>();

  setServices(services: Services) {
    this.services = services;
    for (const cb of this.callbacks) cb(services);
  }

  on(cb: (services: typeof this.services) => void) {
    this.callbacks.add(cb);
    return () => {
      this.callbacks.delete(cb);
    };
  }

  constructor() {
    this.browser = Mdns2.createBrowser(Mdns2.tcp(type));
    thisDeviceUid().then((this_device_uid) => {
      this.browser.on(
        "serviceUp",
        (service) => {
          const uid = service.name;
          if (uid === undefined) return;
          if (uid === this_device_uid) return;
          const addresses = service.addresses
            .filter((ip) => ip !== "127.0.0.1" && !ip.includes(":"))
            .map((ip) => `${ip}:${service.port}`);
          if (addresses.length === 0) return;
          this.setServices({ ...this.services, [uid]: addresses });
        },
      );
      this.browser.on(
        "serviceDown",
        (service) => {
          // TODO: duplicate serviceDown, use interfaceIndex/networkInterface
          const uid = service.name;
          if (uid === undefined) return;
          if (uid === this_device_uid) return;
          if (!(uid in this.services)) return;
          const services = { ...this.services };
          delete services[uid];
          this.setServices(services);
        },
      );
      this.browser.start();
    });
  }

  async advertise(port: number) {
    const this_device_uid = await thisDeviceUid();
    if (this.advertisement !== null) throw new Error();
    this.advertisement = Mdns2.createAdvertisement(
      Mdns2.tcp(type),
      port,
      { name: this_device_uid },
    );
    this.advertisement.start();
  }

  stop() {
    this.advertisement?.stop();
    this.browser?.stop();
  }
}

export const mdns = new Mdns();
