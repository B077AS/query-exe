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
