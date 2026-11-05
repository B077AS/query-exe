// The accent colours the user can choose from. Each has a lighter variant for dark mode and the text colour that is
// readable on it. The keys must match SettingsStore.ACCENTS in Java.

export interface Accent {
  key: string;
  name: string;
  light: string;
  dark: string;
  onLight: string;
  onDark: string;
}

export const ACCENTS: Accent[] = [
  { key: 'blue', name: 'Blue', light: '#2a62d9', dark: '#3f7df2', onLight: '#ffffff', onDark: '#ffffff' },
  { key: 'teal', name: 'Teal', light: '#0b8575', dark: '#3cc2ad', onLight: '#ffffff', onDark: '#04201b' },
  { key: 'green', name: 'Green', light: '#2f7d3f', dark: '#6cc17e', onLight: '#ffffff', onDark: '#0a1f0e' },
  { key: 'orange', name: 'Orange', light: '#c2501a', dark: '#f08a55', onLight: '#ffffff', onDark: '#2a1004' },
  { key: 'red', name: 'Red', light: '#d8281e', dark: '#ff5347', onLight: '#ffffff', onDark: '#2a0710' },
  { key: 'pink', name: 'Pink', light: '#b8387b', dark: '#ec7fb4', onLight: '#ffffff', onDark: '#2b0a1c' },
  { key: 'violet', name: 'Violet', light: '#6a4bc4', dark: '#a58cf0', onLight: '#ffffff', onDark: '#150c33' },
  { key: 'graphite', name: 'Graphite', light: '#44484e', dark: '#c3c7ce', onLight: '#ffffff', onDark: '#14161a' },
];

export function accentByKey(key: string): Accent {
  return ACCENTS.find((a) => a.key === key) ?? ACCENTS[0];
}
