<script setup lang="ts">
import { mdiDotsVertical, mdiLanDisconnect, mdiPowerPlugOffOutline, mdiRestart } from '@mdi/js';
import { DropdownMenuContent, DropdownMenuItem, DropdownMenuPortal, DropdownMenuRoot, DropdownMenuSeparator, DropdownMenuTrigger } from 'reka-ui';
import { computed, onBeforeUnmount, onMounted, reactive } from 'vue';
import { api } from '../api';
import { app, dialogs, disconnect, loadTree } from '../store';
import { confirm, fail, toast } from '../ui';
import AppDialog from './AppDialog.vue';
import Icon from './Icon.vue';

const status = reactive<Record<string, { alive: boolean }>>({});
const busy = reactive<Record<string, string>>({});

const rows = computed(() => Object.values(app.sessions));
const tabsOf = (id: string) => app.tabs.filter((t) => t.connectionId === id);
const alive = (id: string): boolean | null => (status[id] ? status[id].alive : null);
const down = computed(() => rows.value.filter((s) => alive(s.connectionId) === false).length);
const healthy = computed(() => rows.value.filter((s) => alive(s.connectionId) !== false).length);

async function check(): Promise<void> {
  try {
    for (const s of await api.connectionStatus()) status[s.id] = { alive: s.alive };
  } catch (e) {
    fail('Could not check the connections', e);
  }
}

let timer: ReturnType<typeof setInterval> | undefined;
onMounted(() => {
  void check();
  timer = setInterval(() => void check(), 10_000);
});
onBeforeUnmount(() => clearInterval(timer));

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
  const tabs = tabsOf(id).length;
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
  <AppDialog title="Active connections" :width="780" flush @close="dialogs.activeConnections = false">
    <div v-if="rows.length === 0" class="none">
      <Icon :path="mdiPowerPlugOffOutline" :size="44" />
      <b>Nothing connected</b>
      <span>Double-click a connection in the tree to open it.</span>
    </div>

    <template v-else>
      <div class="hero" :class="{ bad: down > 0 }">
        <div class="count"><b>{{ healthy }}</b><span>/ {{ rows.length }}</span></div>
        <div class="say">
          <strong>{{ down > 0 ? `${down} not responding` : 'All connections healthy' }}</strong>
          <small>{{ app.tabs.length }} open tab{{ app.tabs.length === 1 ? '' : 's' }}</small>
        </div>
        <svg class="ecg" viewBox="0 0 160 32" preserveAspectRatio="none" aria-hidden="true">
          <path class="trail" d="M0 16 H40 L46 16 L51 5 L58 27 L63 16 H100 L105 16 L109 10 L114 22 L118 16 H160" />
          <path v-if="down === 0" class="beat" pathLength="1" d="M0 16 H40 L46 16 L51 5 L58 27 L63 16 H100 L105 16 L109 10 L114 22 L118 16 H160" />
        </svg>
      </div>

      <div class="panel">
        <div class="list">
          <table class="dgrid static roomy">
            <thead><tr><th class="g">#</th><th>Connection</th><th>Target</th><th>{{ rows.some((r) => r.info.type === 'PostgreSQL') ? 'Database / schema' : 'Database' }}</th><th class="num">Tabs</th><th colspan="2">Status</th></tr></thead>
            <tbody>
              <tr v-for="(s, n) in rows" :key="s.connectionId">
                <td class="g">{{ n + 1 }}</td>
                <td class="nm">{{ s.info.name }}</td>
                <td class="trunc" :title="s.info.target">{{ s.info.target }}</td>
                <td :class="{ nul: !s.current }" :title="s.info.type === 'PostgreSQL' ? 'Current schema' : 'Current database'">{{ s.current ?? 'NULL' }}</td>
                <td class="num">{{ tabsOf(s.connectionId).length }}</td>
                <td class="st">
                  <span class="dot" :class="busy[s.connectionId] ? 'wait' : alive(s.connectionId) === false ? 'bad' : alive(s.connectionId) === true ? 'ok' : 'wait'" />
                  {{ busy[s.connectionId] === 'reconnect' ? 'Reconnecting…' : busy[s.connectionId] ? 'Disconnecting…' : alive(s.connectionId) === false ? 'Not responding' : alive(s.connectionId) === true ? 'Connected' : 'Checking…' }}
                </td>
                <td class="act">
                  <DropdownMenuRoot>
                    <DropdownMenuTrigger class="ib sm" title="Actions" :disabled="!!busy[s.connectionId]"><Icon :path="mdiDotsVertical" /></DropdownMenuTrigger>
                    <DropdownMenuPortal>
                      <DropdownMenuContent class="menu" align="end" :side-offset="4">
                        <DropdownMenuItem class="menu-item" @select="reconnect(s.connectionId)"><Icon :path="mdiRestart" /> Reconnect</DropdownMenuItem>
                        <DropdownMenuSeparator class="menu-sep" />
                        <DropdownMenuItem class="menu-item danger" @select="kill(s.connectionId)"><Icon :path="mdiLanDisconnect" /> Disconnect</DropdownMenuItem>
                      </DropdownMenuContent>
                    </DropdownMenuPortal>
                  </DropdownMenuRoot>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </template>

    <template #footer>
      <button class="link" :disabled="rows.length === 0" @click="killAll">Disconnect all</button>
      <span class="sp" />
      <button class="btn primary" @click="dialogs.activeConnections = false">Close</button>
    </template>
  </AppDialog>
</template>

<style scoped>
.none { display: flex; flex-direction: column; align-items: center; gap: 6px; padding: 56px 0; color: var(--text-3); }
.none b { color: var(--text); font-size: 15px; margin-top: 6px; }

/* Header strip: how many are healthy, and a heartbeat that runs while they are. */
.hero { display: flex; align-items: center; gap: 16px; padding: 16px 20px; border-bottom: 1px solid var(--border); background: linear-gradient(100deg, color-mix(in srgb, var(--ok) 10%, var(--bg-raised)), var(--bg-raised) 70%); --pulse: var(--ok); }
.hero.bad { background: linear-gradient(100deg, color-mix(in srgb, var(--warn) 14%, var(--bg-raised)), var(--bg-raised) 70%); --pulse: var(--warn); }
.count { display: flex; align-items: baseline; gap: 4px; font-family: var(--mono); }
.count b { font-size: 34px; line-height: 1; font-weight: 600; color: var(--pulse); }
.count span { color: var(--text-3); font-size: 14px; }
.say { display: flex; flex-direction: column; gap: 2px; }
.say strong { font-size: 14px; }
.say small { color: var(--text-3); font-size: 12px; }
.ecg { flex: 1; min-width: 80px; height: 34px; margin-left: 8px; overflow: visible; }
.ecg path { fill: none; stroke-linecap: round; stroke-linejoin: round; vector-effect: non-scaling-stroke; }
.trail { stroke: var(--pulse); stroke-width: 1.5; opacity: 0.22; }
.beat { stroke: var(--pulse); stroke-width: 2; stroke-dasharray: 0.22 1; animation: beat 2.4s linear infinite; filter: drop-shadow(0 0 4px var(--pulse)); }
.hero.bad .trail { stroke-dasharray: 3 4; opacity: 0.5; }
@keyframes beat { from { stroke-dashoffset: 0.22; } to { stroke-dashoffset: -1; } }

/* The list is a table in the same style as the other dialogs (.dgrid). */
.panel { margin: 16px 20px 20px; border: 1px solid var(--border); border-radius: var(--radius-lg); background: var(--bg); overflow: hidden; }
.list { max-height: 44vh; overflow: auto; }
td.nm { font-family: var(--font); font-weight: 600; }
td.st { font-family: var(--font); white-space: nowrap; }
.dot { display: inline-block; width: 7px; height: 7px; margin-right: 7px; border-radius: 50%; background: var(--text-3); vertical-align: 1px; }
.dot.ok { background: var(--ok); }
.dot.bad { background: var(--warn); }
.dot.wait { background: var(--text-3); }
td.trunc { max-width: 220px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--text-2); }
.link { border: 0; background: none; color: var(--text-3); font: inherit; cursor: pointer; padding: 5px 2px; }
.link:hover:not(:disabled) { color: var(--danger); }

@media (prefers-reduced-motion: reduce) {
  .beat { animation: none; }
}
</style>
