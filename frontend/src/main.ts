import { createApp } from 'vue';
import App from './App.vue';
import './styles/tokens.css';
import './styles/base.css';
import './styles/components.css';
import { initTheme } from './theme';

// Apply the saved theme before mounting so the first paint already has the right colours.
initTheme();
createApp(App).mount('#app');

// The web view's own context menu (Back, Reload, Inspect...) has no place in a desktop app; inputs and the editor keep theirs.
document.addEventListener('contextmenu', (event) => {
  const target = event.target as HTMLElement | null;
  if (!target?.closest('input, textarea, .cm-content')) event.preventDefault();
});

// Jolt accepts a file drag over the whole page (dropEffect "copy"), so the OS would show "copy" even over the toolbar or the
// tree. Only a [data-jolt-drop] area takes files: everywhere else say "none", which gives the system's not-allowed cursor.
// This listener is added after Jolt's own (its script runs first), so its answer is the one the system gets.
for (const type of ['dragenter', 'dragover']) {
  window.addEventListener(type, (event) => {
    const e = event as DragEvent;
    if (!e.dataTransfer || !Array.from(e.dataTransfer.types ?? []).includes('Files')) return;
    if (!(e.target as Element | null)?.closest?.('[data-jolt-drop]')) e.dataTransfer.dropEffect = 'none';
  }, true);
}
