// The typed bridge to the Java side. This is the only file that knows about window.jolt.
//
// Jolt injects `window.jolt` into every page it shows. It carries plain strings in both directions. On top of that,
// Rpc.java implements request/response ({id, method, params} -> {id, result | error}) and pushed events
// ({event, data}); `call` and `onEvent` below are the page half.

declare global {
  interface Window {
    jolt?: {
      postMessage(message: string): void;
      /** Returns a function that removes the listener. */
      onMessage(callback: (message: string) => void): () => void;
      /** Asks Java for another window showing an address of this app; false when it refused (see App.java). */
      openWindow?(path: string, options?: { key?: string; title?: string; width?: number; height?: number }): Promise<boolean>;
    };
  }
}

interface Waiter {
  resolve: (value: unknown) => void;
  reject: (error: Error) => void;
}

interface Incoming {
  id?: number;
  result?: unknown;
  error?: string;
  event?: string;
  data?: unknown;
}

const waiting = new Map<number, Waiter>();
const listeners = new Map<string, Set<(data: unknown) => void>>();
let nextId = 1;
let listening = false;

function listen(bridge: NonNullable<Window['jolt']>): void {
  if (listening) return;
  listening = true;
  bridge.onMessage((text) => {
    let message: Incoming;
    try {
      message = JSON.parse(text) as Incoming;
    } catch {
      return;
    }
    if (message.event !== undefined) {
      listeners.get(message.event)?.forEach((callback) => callback(message.data));
      return;
    }
    if (message.id === undefined) return;
    const waiter = waiting.get(message.id);
    if (!waiter) return;
    waiting.delete(message.id);
    if (message.error !== undefined) waiter.reject(new Error(message.error));
    else waiter.resolve(message.result);
  });
}

/** Calls a Java handler registered with `Rpc.on(method, ...)` and resolves with its result. */
export function call<T>(method: string, params: Record<string, unknown> = {}): Promise<T> {
  const bridge = window.jolt;
  if (!bridge) {
    return Promise.reject(new Error('Not running inside a Jolt window, so there is no Java side to call.'));
  }
  listen(bridge);
  return new Promise<T>((resolve, reject) => {
    const id = nextId++;
    waiting.set(id, { resolve: resolve as (value: unknown) => void, reject });
    bridge.postMessage(JSON.stringify({ id, method, params }));
  });
}

/** Subscribes to an event pushed by Java with `Rpc.emit(name, data)`. Returns an unsubscribe function. */
export function onEvent<T>(name: string, callback: (data: T) => void): () => void {
  if (window.jolt) listen(window.jolt);
  let set = listeners.get(name);
  if (!set) {
    set = new Set();
    listeners.set(name, set);
  }
  const wrapped = callback as (data: unknown) => void;
  set.add(wrapped);
  return () => {
    set.delete(wrapped);
  };
}

/** Opens another window on an address of this app. Java decides whether to; resolves false if it refused or cannot. */
export async function openWindow(path: string, options: { key?: string; title?: string; width?: number; height?: number }): Promise<boolean> {
  const open = window.jolt?.openWindow;
  if (!open) return false;
  return open.call(window.jolt, path, options);
}
