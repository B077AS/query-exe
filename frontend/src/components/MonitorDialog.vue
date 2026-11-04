<script setup lang="ts">
import { mdiAlertOutline, mdiChartLine, mdiLockOutline, mdiPause, mdiPlay, mdiTableMultiple } from '@mdi/js';
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { api, type MonitorChartDef, type MonitorMetric, type MonitorTable } from '../api';
import { app, dialogs } from '../store';
import { errorText } from '../ui';
import AppDialog from './AppDialog.vue';
import Icon from './Icon.vue';
import MonitorChart from './MonitorChart.vue';
import Seg from './Seg.vue';

// Live view of a database server: numbers and charts that update every few seconds, who is connected, and who is blocked.
// What is shown comes from the engine (see the ServerMonitor classes): the page only draws what it is told.
const CAPACITY = 90; // samples kept and drawn

const state = dialogs.monitor!;
const connectionId = state.connectionId;
const name = computed(() => app.sessions[connectionId]?.info.name ?? '');

type Tab = 'overview' | 'sessions' | 'locks';
const tab = ref<Tab>('overview');
const loading = ref(true);
const fatal = ref('');
const problem = ref('');
const charts = ref<MonitorChartDef[]>([]);
const metrics = ref<MonitorMetric[]>([]);
const times = ref<number[]>([]);
const history = reactive<Record<string, (number | null)[]>>({});
const sessions = ref<MonitorTable>({ columns: [], rows: [], note: null });
const locks = ref<MonitorTable>({ columns: [], rows: [], note: null });
const interval = ref<'1' | '2' | '5' | '10'>('2');
const paused = ref(false);
const filter = ref('');

let timer: ReturnType<typeof setTimeout> | undefined;
let stopped = false;

onMounted(async () => {
  try {
    const opened = await api.monitorOpen(connectionId);
    if (!opened.supported) {
      fatal.value = opened.reason ?? 'This connection cannot be monitored.';
      return;
    }
    charts.value = opened.charts ?? [];
    metrics.value = opened.metrics ?? [];
    for (const m of metrics.value) history[m.key] = [];
    await tick();
  } catch (e) {
    fatal.value = errorText(e);
  } finally {
    loading.value = false;
  }
});

onBeforeUnmount(() => {
  stopped = true;
  clearTimeout(timer);
  void api.monitorClose(connectionId).catch(() => undefined);
});

async function tick(): Promise<void> {
  if (stopped) return;
  try {
    const sample = await api.monitorSample(connectionId, true);
    if (stopped) return;
    times.value.push(Date.now());
    for (const m of metrics.value) history[m.key].push(sample.values[m.key] ?? null);
    if (times.value.length > CAPACITY) {
      times.value.shift();
      for (const m of metrics.value) history[m.key].shift();
    }
    if (sample.sessions) sessions.value = sample.sessions;
    if (sample.locks) locks.value = sample.locks;
    problem.value = '';
  } catch (e) {
    problem.value = errorText(e);
  }
  schedule();
}

function schedule(): void {
  clearTimeout(timer);
  if (!stopped && !paused.value) timer = setTimeout(tick, Number(interval.value) * 1000);
}

function togglePause(): void {
  paused.value = !paused.value;
  if (paused.value) clearTimeout(timer);
  else void tick();
}

const intervals = [
  { value: '1' as const, label: '1 s' },
  { value: '2' as const, label: '2 s' },
  { value: '5' as const, label: '5 s' },
  { value: '10' as const, label: '10 s' },
];

const views = computed(() => [
  { value: 'overview' as const, label: 'Overview', icon: mdiChartLine, count: undefined as number | undefined },
  { value: 'sessions' as const, label: 'Sessions', icon: mdiTableMultiple, count: sessions.value.rows.length },
  { value: 'locks' as const, label: 'Locks', icon: mdiLockOutline, count: locks.value.rows.length },
]);

// ---- overview ----------------------------------------------------------------------------------------------------------

const current = (key: string): number | null => {
  const list = history[key];
  return list && list.length ? list[list.length - 1] : null;
};

const tiles = computed(() =>
  metrics.value.filter((m) => m.headline).map((m) => {
    const unit = m.chart ? charts.value.find((c) => c.id === m.chart)?.unit ?? '' : m.unit;
    const value = current(m.key);
    return {
      key: m.key,
      label: m.label,
      text: value === null ? '—' : value.toLocaleString(undefined, { maximumFractionDigits: 1 }) + (unit === 's' ? ' s' : ''),
      warn: value !== null && value > 0 && /waiting|blocked/i.test(m.label),
    };
  }));

const cards = computed(() =>
  charts.value.map((c) => ({
    chart: c,
    series: metrics.value.filter((m) => m.chart === c.id).map((m) => ({ key: m.key, label: m.label, values: history[m.key] ?? [] })),
  })));

// ---- tables ------------------------------------------------------------------------------------------------------------

const pretty = (column: string): string => column.charAt(0).toUpperCase() + column.slice(1).replace(/_/g, ' ');

const shownSessions = computed(() => {
  const q = filter.value.trim().toLowerCase();
  if (!q) return sessions.value.rows;
  return sessions.value.rows.filter((r) => r.some((c) => c !== null && c.toLowerCase().includes(q)));
});

const isQuery = (column: string): boolean => column === 'query' || column.endsWith('_query');
</script>

<template>
  <AppDialog title="Server monitor" :width="1240" flush @close="dialogs.monitor = null">
    <template #icon><Icon :path="mdiChartLine" style="color: var(--accent)" /></template>
    <template #head><span class="pill">{{ name }}</span></template>

    <div class="monitor">
      <nav class="rail">
        <button v-for="v in views" :key="v.value" class="rail-item" :class="{ on: tab === v.value }" @click="tab = v.value">
          <Icon :path="v.icon" :size="18" />{{ v.label }}
          <span v-if="v.count !== undefined" class="count" :class="{ warn: v.value === 'locks' && v.count > 0 }">{{ v.count }}</span>
        </button>
      </nav>

      <div class="page">
        <div v-if="loading" class="empty">Connecting…</div>
        <div v-else-if="fatal" class="empty">
          <Icon :path="mdiAlertOutline" :size="22" />
          <div class="selectable">{{ fatal }}</div>
        </div>

        <template v-else>
          <div class="head">
            <h3>{{ views.find((v) => v.value === tab)?.label }}</h3>
            <span v-if="problem" class="problem selectable" :title="problem"><Icon :path="mdiAlertOutline" :size="14" /> {{ problem }}</span>
            <span class="sp" />
            <input v-if="tab === 'sessions'" v-model="filter" class="input find" placeholder="Filter…" spellcheck="false" />
            <button class="btn" :title="paused ? 'Resume' : 'Pause'" @click="togglePause"><Icon :path="paused ? mdiPlay : mdiPause" /> {{ paused ? 'Resume' : 'Pause' }}</button>
            <Seg v-model="interval" :options="intervals" @update:model-value="schedule" />
          </div>

          <!-- overview: the headline numbers, then one chart per group of metrics -->
          <div v-show="tab === 'overview'" class="scroll">
            <div class="tiles">
              <div v-for="t in tiles" :key="t.key" class="tile" :class="{ warn: t.warn }">
                <div class="tl">{{ t.label }}<Icon v-if="t.warn" :path="mdiAlertOutline" :size="13" /></div>
                <div class="tv mono">{{ t.text }}</div>
              </div>
            </div>
            <div class="cards">
              <div v-for="c in cards" :key="c.chart.id" class="card">
                <MonitorChart :title="c.chart.title" :unit="c.chart.unit" :capacity="CAPACITY" :times="times" :series="c.series" />
              </div>
            </div>
          </div>

          <!-- sessions -->
          <div v-show="tab === 'sessions'" class="scroll box">
            <p v-if="sessions.note" class="note selectable"><Icon :path="mdiAlertOutline" :size="14" /> {{ sessions.note }}</p>
            <table v-else-if="shownSessions.length">
              <thead><tr><th v-for="c in sessions.columns" :key="c">{{ pretty(c) }}</th></tr></thead>
              <tbody>
                <tr v-for="(r, i) in shownSessions" :key="i">
                  <td v-for="(cell, k) in r" :key="k" :class="{ q: isQuery(sessions.columns[k]), mono: isQuery(sessions.columns[k]) }" :title="cell ?? ''">{{ cell ?? '' }}</td>
                </tr>
              </tbody>
            </table>
            <p v-else class="none">{{ filter ? 'No session matches.' : 'No other sessions.' }}</p>
          </div>

          <!-- locks: who waits for whom -->
          <div v-show="tab === 'locks'" class="scroll box">
            <p v-if="locks.note" class="note selectable"><Icon :path="mdiAlertOutline" :size="14" /> {{ locks.note }}</p>
            <table v-else-if="locks.rows.length">
              <thead><tr><th v-for="c in locks.columns" :key="c">{{ pretty(c) }}</th></tr></thead>
              <tbody>
                <tr v-for="(r, i) in locks.rows" :key="i">
                  <td v-for="(cell, k) in r" :key="k" :class="{ q: isQuery(locks.columns[k]), mono: isQuery(locks.columns[k]) }" :title="cell ?? ''">{{ cell ?? '' }}</td>
                </tr>
              </tbody>
            </table>
            <p v-else class="none">Nothing is blocked.</p>
          </div>
        </template>
      </div>
    </div>

    <template #footer>
      <span class="hint">{{ paused ? 'Paused' : `Updates every ${interval} s` }}<template v-if="times.length"><i class="vsep" />{{ times.length }} samples</template></span>
      <span class="sp" />
      <button class="btn primary" @click="dialogs.monitor = null">Close</button>
    </template>
  </AppDialog>
</template>

<style scoped>
.monitor { display: grid; grid-template-columns: 190px 1fr; height: 640px; min-height: 0; }
.page { padding: 20px 26px; min-width: 0; min-height: 0; display: flex; flex-direction: column; gap: 14px; }
.head { display: flex; align-items: center; gap: 10px; }
.head h3 { font-size: 18px; font-weight: 600; margin: 0; }
.sp { flex: 1; }
.find { width: 200px; }
.count { margin-left: auto; font-size: 11px; color: var(--text-3); }
.count.warn { color: var(--warn); font-weight: 600; }
.problem { display: inline-flex; align-items: center; gap: 5px; font-size: 11.5px; color: var(--danger); max-width: 420px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.empty { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8px; color: var(--text-3); text-align: center; padding: 0 40px; }
.scroll { flex: 1; min-height: 0; overflow: auto; }
.tiles { display: grid; grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: 10px; margin-bottom: 14px; }
.tile { border: 1px solid var(--border); border-radius: var(--radius-lg); padding: 9px 12px; background: var(--bg); }
.tile.warn { border-color: var(--warn); }
.tl { display: flex; align-items: center; gap: 5px; font-size: 11.5px; color: var(--text-2); }
.tile.warn .tl { color: var(--warn); }
.tv { font-size: 20px; font-weight: 600; margin-top: 2px; }
.cards { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.card { border: 1px solid var(--border); border-radius: var(--radius-lg); padding: 12px 14px 8px; background: var(--bg); }
.box { border: 1px solid var(--border); border-radius: var(--radius); }
table { width: 100%; border-collapse: collapse; }
th { text-align: left; font-weight: 600; font-size: 11.5px; color: var(--text-2); padding: 6px 10px; background: var(--bg-side); border-bottom: 1px solid var(--border); position: sticky; top: 0; white-space: nowrap; }
td { padding: 5px 10px; border-bottom: 1px solid var(--border); font-size: 12px; max-width: 220px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; user-select: text; }
td.q { max-width: 460px; font-size: 11.5px; }
.none, .note { padding: 28px; text-align: center; color: var(--text-3); margin: 0; }
.note { display: flex; align-items: center; justify-content: center; gap: 6px; color: var(--warn); }
</style>
