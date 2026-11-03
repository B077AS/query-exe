<script setup lang="ts">
import {
  mdiCheck, mdiFileTreeOutline, mdiInformationOutline, mdiKeyboardOutline, mdiLanConnect, mdiMagnify, mdiMonitor, mdiPaletteOutline, mdiCodeBraces,
  mdiWeatherNight, mdiWhiteBalanceSunny, mdiOpenInNew, mdiPlayOutline, mdiPencilOutline, mdiTabPlus, mdiTableLarge,
} from '@mdi/js';
import { computed, onMounted, ref } from 'vue';
import { ACCENTS } from '../accents';
import { api } from '../api';
import { app, dialogs } from '../store';
import { scheme, theme } from '../theme';
import { prefs, savePref } from '../ui';
import AppDialog from './AppDialog.vue';
import Icon from './Icon.vue';
import Logo from './Logo.vue';

type Section = typeof dialogs.settingsSection;
const sections: { key: Section; label: string; icon: string }[] = [
  { key: 'appearance', label: 'Appearance', icon: mdiPaletteOutline },
  { key: 'editor', label: 'Editor', icon: mdiCodeBraces },
  { key: 'connections', label: 'Connections', icon: mdiFileTreeOutline },
  { key: 'shortcuts', label: 'Shortcuts', icon: mdiKeyboardOutline },
  { key: 'about', label: 'About', icon: mdiInformationOutline },
];
const section = computed({ get: () => dialogs.settingsSection, set: (v: Section) => (dialogs.settingsSection = v) });
const heading = computed(() => sections.find((s) => s.key === section.value)?.label ?? '');

const swatch = (key: string): string => {
  const a = ACCENTS.find((x) => x.key === key)!;
  return scheme.value === 'dark' ? a.dark : a.light;
};
const onSwatch = (key: string): string => {
  const a = ACCENTS.find((x) => x.key === key)!;
  return scheme.value === 'dark' ? a.onDark : a.onLight;
};
const accentName = computed(() => ACCENTS.find((a) => a.key === theme.accent)?.name ?? '');

// ---- editor -------------------------------------------------------------------------------------------------------
const sizeMin = 11;
const sizeMax = 18;

// ---- connections tree ---------------------------------------------------------------------------------------------
function setTreeMode(mode: 'all' | 'focus'): void {
  savePref('treeMode', mode);
  // switching to the one-at-a-time tree starts on the list; switching back shows everything
  app.focused = null;
}

// ---- shortcuts ----------------------------------------------------------------------------------------------------
const query = ref('');
type Shortcut = [what: string, keys: string[], joiner?: string];
const groups: { title: string; icon: string; items: Shortcut[] }[] = [
  { title: 'Running', icon: mdiPlayOutline, items: [
    ['Run everything, or the selection', ['Ctrl', 'Enter']], ['Run the statement at the cursor', ['Ctrl', 'Shift', 'Enter']],
  ] },
  { title: 'Editing', icon: mdiPencilOutline, items: [
    ['Save', ['Ctrl', 'S']], ['Save as', ['Ctrl', 'Shift', 'S']], ['Format SQL', ['Ctrl', 'Shift', 'F']], ['Comment / uncomment', ['Ctrl', '/']],
    ['Find and replace', ['Ctrl', 'F']], ['Next / previous match', ['F3', 'Shift+F3'], '/'], ['Autocomplete', ['Ctrl', 'Space']], ['Undo', ['Ctrl', 'Z']],
  ] },
  { title: 'Tabs and files', icon: mdiTabPlus, items: [
    ['New query', ['Ctrl', 'T']], ['Open a file', ['Ctrl', 'O']], ['Close the tab', ['Ctrl', 'W']], ['Next tab', ['Ctrl', 'Tab']], ['Previous tab', ['Ctrl', 'Shift', 'Tab']],
    ['Settings', ['Ctrl', ',']],
  ] },
  { title: 'Result grid', icon: mdiTableLarge, items: [
    ['Move between cells', ['←', '↑', '→', '↓'], ' '], ['Edit the cell', ['Enter', 'F2'], '/'], ['Finish / cancel editing', ['Enter', 'Esc'], '/'], ['Copy the cell or rows', ['Ctrl', 'C']],
    ['Select all rows', ['Ctrl', 'A']], ['Delete the selected rows', ['Del']],
  ] },
];
const shown = computed(() => {
  const q = query.value.trim().toLowerCase();
  return groups.map((g) => ({ ...g, items: g.items.filter(([what, keys]) => q === '' || what.toLowerCase().includes(q) || keys.join(' ').toLowerCase().includes(q)) })).filter((g) => g.items.length > 0);
});
const shortcutCount = computed(() => shown.value.reduce((n, g) => n + g.items.length, 0));

// ---- about --------------------------------------------------------------------------------------------------------
const info = ref<{ version: string; os: string; java: string } | null>(null);
const version = computed(() => info.value?.version ?? window.__QUERYEXE__?.version ?? '');
onMounted(async () => {
  info.value = await api.appInfo().catch(() => null);
});
const stack = ['Java 25', 'Jolt', 'Vue 3', 'CodeMirror 6', 'Reka UI', 'Material Design Icons'];
</script>

<template>
  <AppDialog title="Settings" :width="900" flush @close="dialogs.settings = false">
    <div class="settings">
      <nav class="rail">
        <button v-for="s in sections" :key="s.key" class="rail-item" :class="{ on: section === s.key }" @click="section = s.key">
          <Icon :path="s.icon" :size="18" />{{ s.label }}
        </button>
      </nav>

      <div class="page">
        <h3>{{ heading }}</h3>

        <!-- ================= appearance ================= -->
        <template v-if="section === 'appearance'">
          <div class="label">Theme</div>
          <div class="cards three">
            <button class="card" :class="{ on: theme.preference === 'system' }" @click="theme.preference = 'system'">
              <div class="mini split"><div class="mini-layer light"><i class="mside" /><i class="mmain"><b /><b /><b /></i></div><div class="mini-layer dark"><i class="mside" /><i class="mmain"><b /><b /><b /></i></div></div>
              <span class="cap"><Icon :path="mdiMonitor" :size="15" /> System</span>
            </button>
            <button class="card" :class="{ on: theme.preference === 'light' }" @click="theme.preference = 'light'">
              <div class="mini light"><i class="mside" /><i class="mmain"><b /><b /><b /></i></div>
              <span class="cap"><Icon :path="mdiWhiteBalanceSunny" :size="15" /> Light</span>
            </button>
            <button class="card" :class="{ on: theme.preference === 'dark' }" @click="theme.preference = 'dark'">
              <div class="mini dark"><i class="mside" /><i class="mmain"><b /><b /><b /></i></div>
              <span class="cap"><Icon :path="mdiWeatherNight" :size="15" /> Dark</span>
            </button>
          </div>

          <div class="label" style="margin-top: 22px">Accent colour <span class="hint">· {{ accentName }}</span></div>
          <div class="swatches">
            <button v-for="a in ACCENTS" :key="a.key" class="sw" :class="{ on: theme.accent === a.key }" :style="{ background: swatch(a.key), color: onSwatch(a.key) }" :title="a.name" @click="theme.accent = a.key">
              <Icon v-if="theme.accent === a.key" :path="mdiCheck" :size="18" />
            </button>
          </div>

          <div class="label" style="margin-top: 22px">Preview</div>
          <div class="live">
            <span class="btn primary">Primary action</span>
            <span class="btn">Button</span>
            <span class="tabdemo">Query 1</span>
            <span class="badge">PG</span>
            <span class="selrow">Selected row</span>
          </div>
        </template>

        <!-- ================= editor ================= -->
        <template v-else-if="section === 'editor'">
          <div class="label">Font size <span class="hint">· {{ prefs.editorFontSize }} px</span></div>
          <div class="slider">
            <span class="mono">{{ sizeMin }}</span>
            <input type="range" :min="sizeMin" :max="sizeMax" step="1" :value="prefs.editorFontSize" @input="savePref('editorFontSize', +($event.target as HTMLInputElement).value)" />
            <span class="mono">{{ sizeMax }}</span>
          </div>
          <pre class="code" :style="{ fontSize: prefs.editorFontSize + 'px' }"><span class="c">-- revenue by customer, last 30 days</span>
<span class="k">SELECT</span> c.name, <span class="f">count</span>(o.id) <span class="k">AS</span> orders
<span class="k">FROM</span> orders o <span class="k">JOIN</span> customers c <span class="k">ON</span> c.id = o.customer_id
<span class="k">WHERE</span> o.status = <span class="s">'paid'</span> <span class="k">LIMIT</span> <span class="n">50</span>;</pre>

          <div class="switch-row" @click="savePref('wordWrap', !prefs.wordWrap)">
            <div><div class="t">Wrap long lines</div><div class="hint">Lines that do not fit continue on the next row instead of scrolling sideways.</div></div>
            <span class="switch" :class="{ on: prefs.wordWrap }"><i /></span>
          </div>
        </template>

        <!-- ================= connections ================= -->
        <template v-else-if="section === 'connections'">
          <div class="label">How the tree on the left works</div>
          <div class="cards two">
            <button class="card tall" :class="{ on: prefs.treeMode === 'all' }" @click="setTreeMode('all')">
              <div class="diagram">
                <div class="r"><b class="lv" /><u>shop_prod</u></div>
                <div class="r i1"><b /><u>shop</u></div>
                <div class="r i2"><b /><u>orders</u></div>
                <div class="r"><b class="dim" /><u class="dim">erp</u></div>
                <div class="r"><b class="lv" /><u>app.db</u></div>
                <div class="r i1"><b /><u>main</u></div>
              </div>
              <span class="cap strong">All connections</span>
              <span class="hint">Every connection is a node of one tree; open ones expand in place.</span>
            </button>
            <button class="card tall" :class="{ on: prefs.treeMode === 'focus' }" @click="setTreeMode('focus')">
              <div class="diagram">
                <div class="r back"><b class="lv" />&larr; <u>shop_prod</u></div>
                <div class="r i1"><b /><u>shop</u></div>
                <div class="r i2"><b /><u>customers</u></div>
                <div class="r i2"><b /><u>orders</u></div>
                <div class="r i2"><b /><u>products</u></div>
                <div class="r i1"><b /><u>audit</u></div>
              </div>
              <span class="cap strong">One at a time</span>
              <span class="hint">Opening a connection makes it the whole tree, with a button to go back to the list.</span>
            </button>
          </div>
          <div class="note"><Icon :path="mdiLanConnect" :size="16" /> The list of open connections, with Refresh, Reconnect and Disconnect for each, is behind the network icon above the tree.</div>
        </template>

        <!-- ================= shortcuts ================= -->
        <template v-else-if="section === 'shortcuts'">
          <div class="sc-top">
            <label class="search"><Icon :path="mdiMagnify" /><input v-model="query" placeholder="Find a shortcut" /></label>
            <span class="hint">{{ shortcutCount }} shortcuts</span>
          </div>
          <section v-for="g in shown" :key="g.title" class="sc-card">
            <header><Icon :path="g.icon" :size="17" />{{ g.title }}</header>
            <div v-for="[what, keys, joiner] in g.items" :key="what" class="sc-row">
              <span class="what">{{ what }}</span>
              <span class="keys"><template v-for="(k, i) in keys" :key="k"><kbd>{{ k }}</kbd><i v-if="i < keys.length - 1 && joiner !== ' '">{{ joiner ?? '+' }}</i></template></span>
            </div>
          </section>
          <p v-if="shown.length === 0" class="empty">No shortcut matches “{{ query }}”.</p>
        </template>

        <!-- ================= about ================= -->
        <template v-else>
          <div class="about">
            <Logo :size="84" />
            <div class="ab-name">QueryExe</div>
            <div class="hint">Version {{ version }}</div>
            <p class="ab-text">A database client for PostgreSQL, MySQL, MariaDB, SQL Server, H2 and SQLite. Java runs the connections and the queries, a Vue page draws the window, and both live in one process.</p>
            <div class="chips"><span v-for="s in stack" :key="s" class="chip">{{ s }}</span></div>
            <button class="btn" @click="api.openExternal('https://github.com/B077AS/query-exe')"><Icon :path="mdiOpenInNew" /> Source on GitHub</button>
            <div v-if="info" class="hint mono sys">{{ info.os }} · Java {{ info.java }}</div>
          </div>
        </template>
      </div>
    </div>
    <template #footer><button class="btn primary" @click="dialogs.settings = false">Done</button></template>
  </AppDialog>
</template>

<style scoped>
.settings { display: grid; grid-template-columns: 210px 1fr; height: 540px; min-height: 0; }
.page { padding: 22px 28px; overflow: auto; min-width: 0; }
h3 { font-size: 18px; font-weight: 600; margin-bottom: 18px; }
.label .hint { font-weight: 400; }

/* theme cards */
.cards { display: grid; gap: 14px; }
.cards.three { grid-template-columns: repeat(3, 1fr); }
.cards.two { grid-template-columns: repeat(2, 1fr); }
.card { display: flex; flex-direction: column; gap: 8px; padding: 10px; border: 1px solid var(--border); border-radius: var(--radius-lg); background: var(--bg-raised); text-align: left; }
.card:hover { border-color: var(--border-strong); }
.card.on { border-color: var(--accent); box-shadow: 0 0 0 1px var(--accent); }
.cap { display: flex; align-items: center; gap: 8px; font-weight: 500; }
.cap.strong { font-weight: 600; }
.mini { position: relative; display: grid; grid-template-columns: 26% 1fr; height: 92px; border-radius: var(--radius); overflow: hidden; border: 1px solid var(--border); }
.mini.light, .mini-layer.light { --m-bg: #fcfcfb; --m-side: #f0f0ed; --m-line: #d8d8d3; }
.mini.dark, .mini-layer.dark { --m-bg: #2b2d30; --m-side: #25272a; --m-line: #494c52; }
.mini .mside, .mini-layer .mside { background: var(--m-side); }
.mini .mmain, .mini-layer .mmain { background: var(--m-bg); display: flex; flex-direction: column; gap: 7px; padding: 12px 10px; }
.mini b, .mini-layer b { display: block; height: 6px; border-radius: 3px; background: var(--m-line); }
.mini b:first-child, .mini-layer b:first-child { width: 55%; background: var(--accent); }
.mini b:nth-child(2), .mini-layer b:nth-child(2) { width: 80%; }
.mini b:nth-child(3), .mini-layer b:nth-child(3) { width: 40%; }
.mini.split { display: block; }
.mini-layer { position: absolute; inset: 0; display: grid; grid-template-columns: 26% 1fr; }
.mini-layer.dark { clip-path: polygon(55% 0, 100% 0, 100% 100%, 35% 100%); }

.swatches { display: flex; gap: 12px; margin-top: 6px; }
.sw { width: 40px; height: 40px; border-radius: var(--radius-lg); display: grid; place-items: center; border: 2px solid transparent; }
.sw:hover { filter: brightness(1.08); }
.sw.on { outline: 2px solid var(--text-3); outline-offset: 3px; }
.live { display: flex; align-items: center; gap: 12px; padding: 14px; border: 1px solid var(--border); border-radius: var(--radius-lg); background: var(--bg-side); flex-wrap: wrap; }
.live .btn { pointer-events: none; }
.tabdemo { padding: 6px 12px; background: var(--bg); border-top: 2px solid var(--accent); }
.selrow { padding: 4px 12px; background: var(--accent-tint-strong); box-shadow: inset 3px 0 0 var(--accent); }

/* editor */
.slider { display: flex; align-items: center; gap: 12px; margin: 8px 0 14px; color: var(--text-3); }
.slider input { flex: 1; accent-color: var(--accent); }
.code { margin: 0 0 18px; padding: 14px 16px; border: 1px solid var(--border); border-radius: var(--radius-lg); background: var(--bg); font-family: var(--mono); line-height: 1.6; overflow: auto; user-select: text; }
.code .k { color: var(--syn-keyword); font-weight: 600; }
.code .s { color: var(--syn-string); }
.code .n { color: var(--syn-number); }
.code .c { color: var(--syn-comment); font-style: italic; }
.code .f { color: var(--syn-function); }
.switch-row { display: flex; align-items: center; justify-content: space-between; gap: 20px; padding: 14px 16px; border: 1px solid var(--border); border-radius: var(--radius-lg); background: var(--bg-raised); cursor: pointer; }
.switch-row:hover { border-color: var(--border-strong); }
.switch-row .t { font-weight: 600; }
.switch { width: 38px; height: 22px; border-radius: 11px; background: var(--border-strong); position: relative; flex: none; }
.switch i { position: absolute; top: 3px; left: 3px; width: 16px; height: 16px; border-radius: 50%; background: #fff; }
.switch.on { background: var(--accent); }
.switch.on i { left: 19px; }

/* connections */
.tall .hint { line-height: 1.4; }
.diagram { height: 150px; border: 1px solid var(--border); border-radius: var(--radius); background: var(--bg-side); padding: 10px; display: flex; flex-direction: column; gap: 5px; overflow: hidden; }
.diagram .r { display: flex; align-items: center; gap: 6px; height: 16px; font-size: 11px; font-family: var(--mono); color: var(--text); }
.diagram .i1 { padding-left: 14px; }
.diagram .i2 { padding-left: 28px; }
.diagram b { width: 8px; height: 8px; border-radius: 2px; background: var(--text-3); flex: none; }
.diagram b.lv { background: var(--accent); }
.diagram b.dim, .diagram u.dim { opacity: 0.45; }
.diagram u { text-decoration: none; }
.diagram .back { color: var(--accent); font-weight: 600; }
.note { display: flex; gap: 10px; align-items: flex-start; margin-top: 18px; padding: 12px 14px; border-radius: var(--radius-lg); background: var(--accent-tint); color: var(--text-2); }
.note .icon { color: var(--accent); margin-top: 1px; }

/* shortcuts */
.sc-top { display: flex; align-items: center; gap: 14px; margin-bottom: 14px; }
.sc-top .search { flex: 1; margin: 0; }
.sc-card { border: 1px solid var(--border); background: var(--bg-raised); margin-bottom: 14px; }
.sc-card header { display: flex; align-items: center; gap: 10px; padding: 9px 14px; background: var(--bg-side); border-bottom: 1px solid var(--border); font-weight: 600; }
.sc-card header .icon { color: var(--accent); }
.sc-row { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 8px 14px; border-top: 1px solid var(--border); }
.sc-row:first-of-type { border-top: 0; }
.sc-row:hover { background: var(--bg-hover); }
.keys { display: flex; align-items: center; gap: 5px; flex: none; }
.keys i { font-style: normal; color: var(--text-3); font-size: 12px; }
kbd { min-width: 28px; height: 26px; padding: 0 9px; display: inline-grid; place-items: center; font: 600 12px var(--mono); color: var(--text); background: var(--bg); border: 1px solid var(--border-strong); border-bottom-width: 3px; border-radius: 4px; }
.search { display: flex; align-items: center; gap: 8px; height: 32px; padding: 0 10px; border: 1px solid var(--border-strong); border-radius: var(--radius); background: var(--bg); color: var(--text-3); }
.search:focus-within { border-color: var(--accent); }
.search input { flex: 1; border: 0; outline: 0; background: none; }

/* about */
.about { display: flex; flex-direction: column; align-items: center; text-align: center; gap: 8px; padding-top: 10px; }
.ab-name { font-size: 24px; font-weight: 700; margin-top: 8px; }
.ab-text { max-width: 480px; color: var(--text-2); margin: 10px 0 6px; line-height: 1.55; }
.chips { display: flex; flex-wrap: wrap; justify-content: center; gap: 8px; margin: 8px 0 12px; max-width: 520px; }
.chip { padding: 3px 10px; border: 1px solid var(--border-strong); border-radius: 99px; color: var(--text-2); font-size: 12px; }
.sys { margin-top: 10px; }
</style>
