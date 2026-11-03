<script setup lang="ts">
import { mdiAlertOutline, mdiCheckCircleOutline, mdiLanDisconnect, mdiRefresh, mdiRestart } from '@mdi/js';
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { api } from '../api';
import { app, dialogs, disconnect, loadTree } from '../store';
import { confirm, engineBadge, fail, toast } from '../ui';
import AppDialog from './AppDialog.vue';
import Icon from './Icon.vue';

const status = reactive<Record<string, { alive: boolean; sessions: number }>>({});
const busy = reactive<Record<string, string>>({});
const loaded = ref(false);

const rows = computed(() => Object.values(app.sessions));
const tabsOf = (id: string): number => app.tabs.filter((t) => t.connectionId === id).length;
const alive = (id: string): boolean | null => (status[id] ? status[id].alive : null);

async function check(): Promise<void> {
  try {
    for (const s of await api.connectionStatus()) status[s.id] = { alive: s.alive, sessions: s.sessions };
  } catch (e) {
    fail('Could not check the connections', e);
  } finally {
    loaded.value = true;
  }
}

let timer: ReturnType<typeof setInterval> | undefined;
onMounted(() => {
  void check();
  timer = setInterval(() => void check(), 10_000);
});
onBeforeUnmount(() => clearInterval(timer));

async function refresh(id: string): Promise<void> {
  busy[id] = 'refresh';
  try {
    await loadTree(app.sessions[id]);
    await check();
    toast('ok', 'Structure refreshed', app.sessions[id]?.info.name);
  } finally {
    delete busy[id];
  }
}

async function reconnect(id: string): Promise<void> {
  busy[id] = 'reconnect';
  try {
    await api.reconnect(id);
    await loadTree(app.sessions[id]);
    await check();
    toast('ok', 'Reconnected', app.sessions[id]?.info.name);
  } catch (e) {
    fail('Could not reconnect', e);
    await check();
  } finally {
    delete busy[id];
  }
}

async function kill(id: string): Promise<void> {
  const name = app.sessions[id]?.info.name ?? 'this connection';
  const tabs = tabsOf(id);
  const text = tabs > 0 ? `Its ${tabs} open tab${tabs === 1 ? '' : 's'} stay open but cannot run until you connect again.` : 'Anything it is running is cancelled.';
  if (!(await confirm(`Disconnect ${name}?`, text, 'Disconnect', true))) return;
  busy[id] = 'kill';
  try {
    await disconnect(id);
    delete status[id];
  } finally {
    delete busy[id];
  }
}

async function killAll(): Promise<void> {
  if (!(await confirm('Disconnect everything?', `All ${rows.value.length} open connections are closed.`, 'Disconnect all', true))) return;
  for (const s of [...rows.value]) await disconnect(s.connectionId);
  dialogs.activeConnections = false;
}
</script>

<template>
  <AppDialog title="Active connections" :width="780" @close="dialogs.activeConnections = false">
    <template #head><span class="pill">{{ rows.length }} open</span></template>
    <div v-if="rows.length === 0" class="none">No active connections.<br /><span class="hint">Double-click one in the tree to connect.</span></div>
    <div v-else class="cards">
      <article v-for="s in rows" :key="s.connectionId" class="card" :class="{ down: alive(s.connectionId) === false }">
        <header>
          <span class="badge">{{ engineBadge(s.info.type) }}</span>
          <div class="who">
            <div class="nm">{{ s.info.name }}<span v-if="app.selected === s.connectionId" class="pill">selected</span></div>
            <div class="hs mono" :title="s.info.target">{{ s.info.target }}</div>
          </div>
          <span class="state" :class="alive(s.connectionId) === null ? '' : alive(s.connectionId) ? 'ok' : 'warn'">
            <Icon :path="alive(s.connectionId) === false ? mdiAlertOutline : mdiCheckCircleOutline" :size="16" />
            {{ alive(s.connectionId) === null ? 'Checking…' : alive(s.connectionId) ? 'Connected' : 'Not responding' }}
          </span>
        </header>
        <div class="stats">
          <div class="stat"><b>{{ tabsOf(s.connectionId) }}</b><span>open tab{{ tabsOf(s.connectionId) === 1 ? '' : 's' }}</span></div>
          <div class="stat"><b>{{ status[s.connectionId]?.sessions ?? '–' }}</b><span>query session{{ status[s.connectionId]?.sessions === 1 ? '' : 's' }}</span></div>
          <div class="stat wide"><b class="mono">{{ s.current ?? '–' }}</b><span>current {{ s.info.type === 'PostgreSQL' ? 'schema' : 'database' }}</span></div>
        </div>
        <footer>
          <button class="btn sm" :disabled="!!busy[s.connectionId]" @click="refresh(s.connectionId)"><Icon :path="mdiRefresh" :class="{ spin: busy[s.connectionId] === 'refresh' }" /> Refresh structure</button>
          <button class="btn sm" :disabled="!!busy[s.connectionId]" @click="reconnect(s.connectionId)"><Icon :path="mdiRestart" :class="{ spin: busy[s.connectionId] === 'reconnect' }" /> Reconnect</button>
          <span class="sp" />
          <button class="btn sm danger" :disabled="!!busy[s.connectionId]" @click="kill(s.connectionId)"><Icon :path="mdiLanDisconnect" /> Disconnect</button>
        </footer>
      </article>
    </div>
    <template #footer>
      <button class="btn" :disabled="rows.length === 0" @click="killAll">Disconnect all</button>
      <span class="sp" />
      <button class="btn primary" @click="dialogs.activeConnections = false">Close</button>
    </template>
  </AppDialog>
</template>

<style scoped>
.none { text-align: center; padding: 36px 0; color: var(--text-2); line-height: 1.8; }
.cards { display: flex; flex-direction: column; gap: 14px; max-height: 58vh; overflow: auto; padding: 2px; }
.card { background: var(--bg-raised); border: 1px solid var(--border); box-shadow: inset 3px 0 0 var(--accent); }
.card.down { box-shadow: inset 3px 0 0 var(--warn); }
.card header { display: flex; align-items: center; gap: 12px; padding: 12px 14px 10px 17px; }
.badge { font: 700 10px var(--mono); padding: 3px 6px; background: var(--accent); color: var(--on-accent); flex: none; }
.who { flex: 1; min-width: 0; }
.nm { font-weight: 600; font-size: 14px; display: flex; align-items: center; gap: 8px; }
.hs { font-size: 11.5px; color: var(--text-3); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; margin-top: 1px; }
.state { display: flex; align-items: center; gap: 6px; font-size: 12px; color: var(--text-3); flex: none; }
.state.ok { color: var(--ok); }
.state.warn { color: var(--warn); }
.stats { display: grid; grid-template-columns: 1fr 1fr 2fr; gap: 1px; background: var(--border); border-top: 1px solid var(--border); border-bottom: 1px solid var(--border); margin-left: 3px; }
.stat { background: var(--bg-raised); padding: 9px 14px; display: flex; flex-direction: column; gap: 1px; min-width: 0; }
.stat b { font-size: 15px; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.stat span { font-size: 11px; color: var(--text-3); text-transform: uppercase; letter-spacing: 0.05em; }
.card footer { display: flex; align-items: center; gap: 8px; padding: 10px 14px 10px 17px; }
</style>
