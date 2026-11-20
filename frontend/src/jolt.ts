// The typed bridge to the Java side. This is the only file that knows about window.jolt.
//
// Request/response and pushed events are Jolt's built-in RPC: `jolt.call(method, params)` runs a handler registered in
// `api/` (`Rpc.on`) and `jolt.onEvent(name, callback)` receives what Java pushes with `rpc.emit`. A handler that throws
// rejects the promise with a `JoltRpcError` whose `message` is the exception's message (the page shows it as a toast).

export interface CallOptions {
  /** Abort the call: the Java handler's thread is interrupted. */
  signal?: AbortSignal;
  /** Give up after this many milliseconds. */
  timeout?: number;
  /** Receives what the handler sends with `RpcCall.progress`. */
  onProgress?: (data: unknown) => void;
}

declare global {
  interface Window {
    jolt?: {
      postMessage(message: string): void;
      /** Returns a function that removes the listener. */
      onMessage(callback: (message: string) => void): () => void;
      call(method: string, params?: unknown, options?: CallOptions): Promise<unknown>;
      /** Returns a function that removes the listener. */
      onEvent(name: string, callback: (data: unknown) => void): () => void;
      /** Asks Java for another window showing an address of this app; false when it refused (see App.java). */
      openWindow?(path: string, options?: { key?: string; title?: string; width?: number; height?: number }): Promise<boolean>;
    };
  }
}

/** Calls a Java handler registered with `Rpc.on(method, ...)` and resolves with its result. */
export function call<T>(method: string, params: Record<string, unknown> = {}, options?: CallOptions): Promise<T> {
  const bridge = window.jolt;
  if (!bridge) {
    return Promise.reject(new Error('Not running inside a Jolt window, so there is no Java side to call.'));
  }
  return bridge.call(method, params, options) as Promise<T>;
}

/** Subscribes to an event pushed by Java with `Rpc.emit(name, data)`. Returns an unsubscribe function. */
export function onEvent<T>(name: string, callback: (data: T) => void): () => void {
  if (!window.jolt) return () => {};
  return window.jolt.onEvent(name, callback as (data: unknown) => void);
}

/** Opens another window on an address of this app. Java decides whether to; resolves false if it refused or cannot. */
export async function openWindow(path: string, options: { key?: string; title?: string; width?: number; height?: number }): Promise<boolean> {
  const open = window.jolt?.openWindow;
  if (!open) return false;
  return open.call(window.jolt, path, options);
}
