import { reactive } from 'vue';

// Small shared UI state: toasts, a confirmation dialog, and the saved UI preferences.
import { api } from './api';

export interface Toast {
  id: number;
  kind: 'info' | 'ok' | 'error';
  title: string;
  text?: string;
}

export const toasts = reactive<Toast[]>([]);
let nextToast = 1;

export function toast(kind: Toast['kind'], title: string, text?: string): void {
  const item: Toast = { id: nextToast++, kind, title, text };
  toasts.push(item);
  setTimeout(() => dismissToast(item.id), kind === 'error' ? 9000 : 4000);
}

export function dismissToast(id: number): void {
  const index = toasts.findIndex((t) => t.id === id);
  if (index >= 0) toasts.splice(index, 1);
}

export function errorText(e: unknown): string {
  return e instanceof Error ? e.message : String(e);
}

export function fail(title: string, e: unknown): void {
  toast('error', title, errorText(e));
}

// ---- confirm ------------------------------------------------------------------------------------------------------

export const confirmState = reactive({
  open: false,
  title: '',
  text: '',
  action: 'Confirm',
  danger: false,
  resolve: null as null | ((ok: boolean) => void),
});

export function confirm(title: string, text: string, action = 'Confirm', danger = false): Promise<boolean> {
  return new Promise((resolve) => {
    Object.assign(confirmState, { open: true, title, text, action, danger, resolve });
  });
}

export function answerConfirm(ok: boolean): void {
  const resolve = confirmState.resolve;
  confirmState.open = false;
  confirmState.resolve = null;
  resolve?.(ok);
}

// ---- remembered UI preferences ------------------------------------------------------------------------------------

const saved = window.__QUERYEXE__?.ui ?? {};

export const prefs = reactive({
  treeWidth: typeof saved.treeWidth === 'number' ? saved.treeWidth : 256,
  resultHeight: typeof saved.resultHeight === 'number' ? saved.resultHeight : 300,
  connectionsView: (saved.connectionsView === 'list' ? 'list' : 'grid') as 'grid' | 'list',
  editorFontSize: typeof saved.editorFontSize === 'number' ? saved.editorFontSize : 13,
  wordWrap: saved.wordWrap === true,
  /** 'all': every connection is a root of the tree. 'focus': one connection at a time fills the tree, with a way back. */
  treeMode: (saved.treeMode === 'focus' ? 'focus' : 'all') as 'all' | 'focus',
});

export function savePref<K extends keyof typeof prefs>(key: K, value: (typeof prefs)[K]): void {
  prefs[key] = value;
  api.setUi(key, value as string | number | boolean).catch((e) => console.warn('Could not save the setting:', e));
}

// ---- prompt (one line of text) ------------------------------------------------------------------------------------

export const promptState = reactive({
  open: false,
  title: '',
  label: '',
  value: '',
  action: 'OK',
  resolve: null as null | ((value: string | null) => void),
});

export function prompt(title: string, label: string, value = '', action = 'OK'): Promise<string | null> {
  return new Promise((resolve) => {
    Object.assign(promptState, { open: true, title, label, value, action, resolve });
  });
}

export function answerPrompt(value: string | null): void {
  const resolve = promptState.resolve;
  promptState.open = false;
  promptState.resolve = null;
  resolve?.(value);
}

/** The two letters shown for an engine (tree, tabs). */
export function engineBadge(type: string): string {
  return ({ PostgreSQL: 'PG', MySQL: 'MY', MariaDB: 'MA', SQLServer: 'MS', SQLite: 'SQ', H2: 'H2' } as Record<string, string>)[type] ?? type.slice(0, 2).toUpperCase();
}
