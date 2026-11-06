/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue';
  const component: DefineComponent<object, object, unknown>;
  export default component;
}

interface Window {
  /** Injected by App.java before the page's scripts run: what the user chose last time. */
  __QUERYEXE__?: {
    preference: 'system' | 'light' | 'dark';
    accent: string;
    version: string;
    dev: boolean;
    ui: Record<string, unknown>;
  };
}
