import { computed, reactive, watch } from 'vue';
import { accentByKey } from './accents';
import { api } from './api';

// The user's theme choice (system, light or dark) and accent. Java remembers them and injects them as
// window.__QUERYEXE__ so the first paint is right; here they are applied to the page (data-theme + CSS variables) and
// pushed back so the native title bar matches the page.

export type Preference = 'system' | 'light' | 'dark';

const boot = window.__QUERYEXE__;
const darkQuery = matchMedia('(prefers-color-scheme: dark)');

export const theme = reactive({
  preference: (boot?.preference ?? 'system') as Preference,
  accent: boot?.accent ?? 'blue',
  systemDark: darkQuery.matches,
});

export const scheme = computed<'light' | 'dark'>(() =>
  theme.preference === 'system' ? (theme.systemDark ? 'dark' : 'light') : theme.preference,
);

const HEX = /^#[0-9a-f]{6}$/i;

function applyTheme(): void {
  const root = document.documentElement;
  const dark = scheme.value === 'dark';
  const accent = accentByKey(theme.accent);
  root.dataset.theme = scheme.value;
  root.style.setProperty('--accent', dark ? accent.dark : accent.light);
  root.style.setProperty('--on-accent', dark ? accent.onDark : accent.onLight);

  // The native title bar and the web view background are painted by Java with the page's own colours.
  const css = getComputedStyle(root);
  const background = css.getPropertyValue('--bg-side').trim();
  const text = css.getPropertyValue('--text').trim();
  const accentColor = css.getPropertyValue('--accent').trim();
  const onAccent = css.getPropertyValue('--on-accent').trim();
  if (![background, text, accentColor, onAccent].every((c) => HEX.test(c))) return;
  api
    .setTheme({ preference: theme.preference, accent: theme.accent, scheme: scheme.value, background, text, accentColor, onAccent })
    .catch((e) => console.warn('Could not update the window theme:', e));
}

let started = false;

export function initTheme(): void {
  if (started) return;
  started = true;
  darkQuery.addEventListener('change', (event) => {
    theme.systemDark = event.matches;
  });
  watch([scheme, () => theme.accent, () => theme.preference], applyTheme, { immediate: true, flush: 'sync' });
}
