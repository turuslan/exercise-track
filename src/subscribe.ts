type Subscriber<T> = T extends void ? () => void : (arg: T) => void;
export class Subscribe<T> {
  #subscribers = new Set<Subscriber<T>>();

  subscribe(subscriber: Subscriber<T>) {
    this.#subscribers.add(subscriber);
    return () => {
      this.#subscribers.delete(subscriber);
    };
  }

  notify(...args: Parameters<Subscriber<T>>) {
    for (const subscriber of this.#subscribers) {
      (subscriber as any)(...args);
    }
  }
}
