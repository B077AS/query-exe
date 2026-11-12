<script setup lang="ts">
import {
  mdiClose, mdiCogOutline, mdiContentSaveOutline, mdiDatabaseOutline, mdiFolderOpenOutline, mdiFormatAlignLeft, mdiLoading, mdiPlay, mdiPlayOutline,
  mdiPlus, mdiStop,
} from '@mdi/js';
import { DropdownMenuContent, DropdownMenuItem, DropdownMenuPortal, DropdownMenuRoot, DropdownMenuTrigger } from 'reka-ui';
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { app, activeTab, cancelTab, closeTab, dialogs, infoOf, isDirty, newQuery, newTab, type QueryTab } from '../store';
import { confirm, engineBadge, prefs, savePref } from '../ui';
import ConnectionsTree from './ConnectionsTree.vue';
import EmptyMain from './EmptyMain.vue';
import Icon from './Icon.vue';
import QueryTabView from './QueryTab.vue';
import Splitter from './Splitter.vue';

const views = ref<Record<string, InstanceType<typeof QueryTabView>>>({});
const width = ref(prefs.treeWidth);
const current = computed(() => activeTab());
const openSessions = computed(() => Object.values(app.sessions));
const view = (): InstanceType<typeof QueryTabView> | undefined => (current.value ? views.value[current.value.id] : undefined);
const sessionName = computed(() => (current.value ? infoOf(current.value.connectionId)?.name ?? '' : ''));
const currentDb = computed(() => (current.value ? app.sessions[current.value.connectionId]?.current ?? '' : ''));
const badge = (tab: QueryTab): string => engineBadge(infoOf(tab.connectionId)?.type ?? '');

async function close(tab: QueryTab): Promise<void> {
  if (isDirty(tab) && !(await confirm('Close without saving?', `"${tab.title}" has changes that are not saved.`, 'Close', true))) return;
  closeTab(tab.id);
}

function onMiddle(event: MouseEvent, tab: QueryTab): void {
  if (event.button === 1) {
    event.preventDefault();
    void close(tab);
  }
}

function onKey(event: KeyboardEvent): void {
  if (!(event.ctrlKey || event.metaKey) || dialogs.connection || dialogs.settings || dialogs.tableEditor) return;
  const key = event.key.toLowerCase();
  if (key === 't' && !event.shiftKey) {
    newQuery();
  } else if (key === 'w') {
    if (current.value) void close(current.value);
  } else if (key === 'o' && !event.shiftKey) {
    void view()?.openFile();
  } else if (key === 's' && event.shiftKey) {
    void view()?.save(true);
  } else if (key === ',') {
    dialogs.settings = true;
  } else if (key === 'tab' && app.tabs.length > 0) {
    const i = app.tabs.findIndex((t) => t.id === app.activeTab);
    app.activeTab = app.tabs[(i + (event.shiftKey ? -1 : 1) + app.tabs.length) % app.tabs.length].id;
  } else {
    return;
  }
  event.preventDefault();
}

onMounted(() => window.addEventListener('keydown', onKey));
onBeforeUnmount(() => window.removeEventListener('keydown', onKey));
</script>

<template>
  <div class="ws">
    <aside class="side" :style="{ width: width + 'px' }">
      <ConnectionsTree />
    </aside>
    <Splitter v-model="width" vertical :min="220" :max="560" @commit="savePref('treeWidth', $event)" />

    <section class="main">
      <div class="tools">
        <button class="btn" :disabled="!current || current.running" @click="view()?.runAll()">
          <Icon :path="current?.running ? mdiLoading : mdiPlay" class="play" :class="{ spin: current?.running }" />Run <span class="kbd">Ctrl ↵</span>
        </button>
        <button class="btn" :disabled="!current || current.running" @click="view()?.runStatement()"><Icon :path="mdiPlayOutline" class="play" />Run statement <span class="kbd">Ctrl ⇧ ↵</span></button>
        <button class="btn" :disabled="!current || !current.running" @click="current && cancelTab(current)"><Icon :path="mdiStop" />Stop</button>
        <span class="sp" />
        <span v-if="current" class="pill" title="Connection and database in use"><Icon :path="mdiDatabaseOutline" :size="13" />{{ sessionName }}<template v-if="currentDb"><i class="vsep" />{{ currentDb }}</template></span>
        <button class="ib" title="Open file (Ctrl+O)" :disabled="!current" @click="view()?.openFile()"><Icon :path="mdiFolderOpenOutline" /></button>
        <button class="ib" title="Save (Ctrl+S)" :disabled="!current" @click="view()?.save()"><Icon :path="mdiContentSaveOutline" /></button>
        <button class="ib" title="Format SQL (Ctrl+Shift+F)" :disabled="!current" @click="view()?.format()"><Icon :path="mdiFormatAlignLeft" /></button>
        <button class="ib" title="Settings (Ctrl+,)" @click="dialogs.settings = true"><Icon :path="mdiCogOutline" /></button>
      </div>

      <div v-if="app.tabs.length > 0" class="tabs" role="tablist">
        <div v-for="tab in app.tabs" :key="tab.id" class="tab" :class="{ on: tab.id === app.activeTab }" role="tab" :title="infoOf(tab.connectionId)?.name" @mousedown="app.activeTab = tab.id; onMiddle($event, tab)">
          <span class="badge">{{ badge(tab) }}</span>
          <span class="title">{{ tab.title }}</span>
          <button class="x" title="Close (Ctrl+W)" @mousedown.stop @click.stop="close(tab)"><Icon :path="mdiClose" :size="14" /></button>
        </div>
        <!-- with several connections open, the + asks which one the new query is for -->
        <DropdownMenuRoot v-if="openSessions.length > 1">
          <DropdownMenuTrigger class="ib add" title="New query (Ctrl+T)"><Icon :path="mdiPlus" /></DropdownMenuTrigger>
          <DropdownMenuPortal>
            <DropdownMenuContent class="menu" align="start" :side-offset="4">
              <div class="menu-label">New query on</div>
              <DropdownMenuItem v-for="s in openSessions" :key="s.connectionId" class="menu-item" :class="{ on: current?.connectionId === s.connectionId }" @select="newTab(s.connectionId)">
                <span class="badge">{{ engineBadge(s.info.type) }}</span>{{ s.info.name }}
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenuPortal>
        </DropdownMenuRoot>
        <button v-else class="ib add" title="New query (Ctrl+T)" @click="newQuery()"><Icon :path="mdiPlus" /></button>
      </div>

      <!-- the only place .sql files can be dropped (Jolt reports this zone with the files); the toolbar, tabs and tree are outside it -->
      <div class="views" data-jolt-drop="scripts">
        <EmptyMain v-if="app.tabs.length === 0" />
        <QueryTabView v-for="tab in app.tabs" v-show="tab.id === app.activeTab" :key="tab.id" :ref="(el) => { if (el) views[tab.id] = el as InstanceType<typeof QueryTabView>; }" :tab="tab" />
      </div>
    </section>
  </div>
</template>

<style scoped>
.ws { display: flex; height: 100%; min-height: 0; }
.side { flex: none; background: var(--bg-side); display: flex; flex-direction: column; min-height: 0; min-width: 0; }
.main { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.tools { display: flex; align-items: center; gap: 6px; padding: 6px 10px; border-bottom: 1px solid var(--border); background: var(--bg); flex: none; }
.tools .play { color: var(--ok); }
.tabs { display: flex; align-items: stretch; background: var(--bg-side); border-bottom: 1px solid var(--border); height: 34px; overflow-x: auto; overflow-y: hidden; flex: none; }
.tab { display: flex; align-items: center; gap: 8px; padding: 0 8px 0 12px; border-right: 1px solid var(--border); color: var(--text-2); position: relative; flex: none; max-width: 260px; }
.tab:hover { color: var(--text); }
.tab.on { background: var(--bg); color: var(--text); }
.tab.on::after { content: ''; position: absolute; left: 0; right: 0; top: 0; height: 2px; background: var(--accent); }
.title { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.x { width: 20px; height: 20px; display: grid; place-items: center; border-radius: var(--radius); color: var(--text-3); }
.x:hover { background: var(--bg-hover); color: var(--text); }
.add { align-self: center; margin: 0 4px; flex: none; }
.menu-item .badge { font: 700 9.5px var(--mono); padding: 1px 4px; border-radius: 3px; background: var(--accent-tint-strong); color: var(--accent); }
.views { flex: 1; min-height: 0; position: relative; }
.views > * { position: absolute; inset: 0; }
/* while files are dragged over the drop area: a frame on all four edges and a faint tint, drawn inside the area's own box so
   nothing is clipped and the editor stays readable underneath */
.views::before, .views::after { content: ''; position: absolute; inset: 0; z-index: 5; pointer-events: none; opacity: 0; }
.views::before { background: var(--accent); }
.views::after { border: 2px solid var(--accent); }
.views[data-jolt-drop-over]::before { opacity: 0.08; }
.views[data-jolt-drop-over]::after { opacity: 1; }
</style>
