<script setup lang="ts">
import { mdiAlertOutline, mdiChevronDown, mdiChevronRight, mdiContentCopy, mdiLoading, mdiRefresh, mdiStop } from '@mdi/js';
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { api, type Plan, type PlanNode } from '../api';
import { dialogs, infoOf } from '../store';
import { fail, toast } from '../ui';
import AppDialog from './AppDialog.vue';
import Icon from './Icon.vue';
import Seg from './Seg.vue';

// The execution plan of a statement as a tree: what each step does, how much it was expected to cost (or, when the statement was
// really run, how long it took), with the steps that cost the most picked out. The engines that can measure a statement offer it as
// "Actual"; what the statement changes is rolled back.

const state = dialogs.explain!;
const MEASURING = new Set(['PostgreSQL', 'MySQL', 'MariaDB']);
const canMeasure = computed(() => MEASURING.has(infoOf(state.connectionId)?.type ?? ''));

const plan = ref<Plan | null>(null);
const loading = ref(false);
const error = ref('');
const mode = ref<'estimated' | 'actual'>('estimated');
const view = ref<'tree' | 'raw'>('tree');
const selectedId = ref<string | null>(null);
const collapsed = reactive(new Set<string>());
let token = 0;

async function load(): Promise<void> {
  const mine = ++token;
  loading.value = true;
  error.value = '';
  try {
    const result = await api.explain({ tabId: state.tabId, connectionId: state.connectionId, ...state.request, analyze: mode.value === 'actual' });
    if (mine !== token) return;
    plan.value = result;
    collapsed.clear();
    selectedId.value = result.roots.length > 0 ? '0' : null;
  } catch (e) {
    if (mine !== token) return;
    error.value = e instanceof Error ? e.message : String(e);
  } finally {
    if (mine === token) loading.value = false;
  }
}

function setMode(value: 'estimated' | 'actual'): void {
  mode.value = value;
  void load();
}

function stop(): void {
  void api.cancel(state.tabId);
}

function close(): void {
  if (loading.value) stop();
  token++;
  dialogs.explain = null;
}

onMounted(load);
onBeforeUnmount(() => token++);

// ---- the rows ---------------------------------------------------------------------------------------------------------

interface Row {
  id: string;
  node: PlanNode;
  depth: number;
  open: boolean;
  /** What the step itself cost (or took), leaving out its children. */
  self: number;
  share: number;
  heat: 'hot' | 'warm' | 'cool';
  full: boolean;
  off: number | null;
}

const sum = (nodes: PlanNode[], pick: (n: PlanNode) => number | undefined): number => nodes.reduce((a, n) => a + (pick(n) ?? 0), 0);
const hasTimes = computed(() => !!plan.value?.analyzed && allNodes().some((n) => n.actualMs !== undefined));
const hasCosts = computed(() => allNodes().some((n) => n.cost !== undefined));
const hasEstimate = computed(() => allNodes().some((n) => n.rows !== undefined));
const hasActual = computed(() => !!plan.value?.analyzed && allNodes().some((n) => n.actualRows !== undefined));
/** What the bars measure: time when the statement was run, else the planner's cost. */
const metric = computed<'time' | 'cost' | null>(() => (hasTimes.value ? 'time' : hasCosts.value ? 'cost' : null));

function allNodes(): PlanNode[] {
  const out: PlanNode[] = [];
  const walk = (n: PlanNode): void => {
    out.push(n);
    n.children.forEach(walk);
  };
  plan.value?.roots.forEach(walk);
  return out;
}

const selfOf = (n: PlanNode): number => {
  const total = metric.value === 'time' ? n.actualMs : n.cost;
  const below = sum(n.children, (c) => (metric.value === 'time' ? c.actualMs : c.cost));
  return Math.max(0, (total ?? 0) - below);
};

/** Reading everything of a table, as opposed to using an index. */
const isFullScan = (n: PlanNode): boolean => /seq scan|table scan|^all$|clustered index scan|index scan/i.test(n.op) && !/index only/i.test(n.op)
  || (n.op === 'SCAN' && !/INDEX/i.test(n.detail ?? ''));

/** How far the planner's row count was from the real one (a factor), when it is far. */
function offBy(n: PlanNode): number | null {
  if (n.rows === undefined || n.actualRows === undefined) return null;
  const est = Math.max(n.rows, 1);
  const act = Math.max(n.actualRows, 1);
  const ratio = Math.max(est / act, act / est);
  return ratio >= 10 && Math.abs(n.actualRows - n.rows) >= 100 ? ratio : null;
}

const rows = computed<Row[]>(() => {
  const out: Row[] = [];
  const total = metric.value ? allNodes().reduce((a, n) => a + selfOf(n), 0) : 0;
  const walk = (n: PlanNode, depth: number, id: string): void => {
    const self = metric.value ? selfOf(n) : 0;
    const share = total > 0 ? self / total : 0;
    const open = !collapsed.has(id);
    out.push({ id, node: n, depth, open, self, share, heat: share >= 0.5 ? 'hot' : share >= 0.2 ? 'warm' : 'cool', full: isFullScan(n), off: offBy(n) });
    if (open) n.children.forEach((c, i) => walk(c, depth + 1, `${id}.${i}`));
  };
  plan.value?.roots.forEach((r, i) => walk(r, 0, String(i)));
  return out;
});

const selected = computed(() => rows.value.find((r) => r.id === selectedId.value) ?? null);

function toggle(row: Row): void {
  if (row.node.children.length === 0) return;
  if (collapsed.has(row.id)) collapsed.delete(row.id);
  else collapsed.add(row.id);
}

const NUMBER = new Intl.NumberFormat(undefined, { maximumFractionDigits: 1 });
function count(n: number | undefined): string {
  if (n === undefined) return '';
  if (n >= 1e9) return NUMBER.format(n / 1e9) + 'B';
  if (n >= 1e6) return NUMBER.format(n / 1e6) + 'M';
  if (n >= 1e4) return NUMBER.format(n / 1e3) + 'k';
  return NUMBER.format(Math.round(n * 10) / 10);
}
function millis(n: number | undefined): string {
  if (n === undefined) return '';
  if (n >= 1000) return NUMBER.format(n / 1000) + ' s';
  return (n < 10 ? n.toFixed(2) : n < 100 ? n.toFixed(1) : String(Math.round(n))) + ' ms';
}
const cost = (n: number | undefined): string => (n === undefined ? '' : n >= 1000 ? count(n) : NUMBER.format(Math.round(n * 100) / 100));

const attrs = computed(() => Object.entries(selected.value?.node.attrs ?? {}));

async function copyRaw(): Promise<void> {
  try {
    await navigator.clipboard.writeText(plan.value?.raw ?? '');
    toast('info', 'Copied');
  } catch (e) {
    fail('Could not copy', e);
  }
}
</script>

<template>
  <AppDialog title="Execution plan" :width="1040" flush @close="close">
    <div class="xp">
      <div class="xp-bar">
        <Seg v-if="canMeasure" :model-value="mode" :options="[{ value: 'estimated', label: 'Estimated' }, { value: 'actual', label: 'Actual' }]" @update:model-value="setMode" />
        <i v-if="canMeasure" class="vsep bar-sep" />
        <Seg v-model="view" :options="[{ value: 'tree', label: 'Plan' }, { value: 'raw', label: 'Raw' }]" />
        <span class="sp" />
        <template v-if="plan && !loading">
          <span v-if="plan.planningMs !== undefined" class="hint">Planning {{ millis(plan.planningMs) }}</span>
          <span v-if="plan.executionMs !== undefined" class="hint">Execution {{ millis(plan.executionMs) }}</span>
        </template>
        <button v-if="loading" class="btn sm" @click="stop"><Icon :path="mdiStop" /> Stop</button>
        <button v-else class="btn sm" @click="load"><Icon :path="mdiRefresh" /> Refresh</button>
      </div>
      <div v-if="mode === 'actual'" class="xp-note"><Icon :path="mdiAlertOutline" :size="14" /> Actual runs the statement to measure it. Anything it changes is rolled back.</div>
      <div class="xp-sql mono" :title="plan?.sql ?? ''">{{ plan?.sql ?? state.title }}</div>

      <div v-if="loading && !plan" class="empty"><Icon :path="mdiLoading" class="spin" /> {{ mode === 'actual' ? 'Running the statement…' : 'Asking for the plan…' }}</div>
      <div v-else-if="error" class="xp-error selectable">{{ error }}</div>
      <template v-else-if="plan">
        <div v-if="view === 'tree'" class="xp-tree" :class="{ stale: loading }" role="tree">
          <div class="xp-head">
            <span class="c-step">Step</span>
            <span v-if="hasEstimate" class="c-num">Rows est.</span>
            <span v-if="hasActual" class="c-num">Rows actual</span>
            <span v-if="hasTimes" class="c-num">Time</span>
            <span v-if="hasCosts" class="c-num">Cost</span>
            <span v-if="metric" class="c-bar">{{ metric === 'time' ? 'Time in the step' : 'Cost of the step' }}</span>
          </div>
          <div v-for="row in rows" :key="row.id" class="xp-row" :class="[{ sel: row.id === selectedId }, metric ? row.heat : '']" role="treeitem" @click="selectedId = row.id" @dblclick="toggle(row)">
            <span class="c-step" :style="{ paddingLeft: row.depth * 18 + 'px' }">
              <button class="chev" tabindex="-1" @click.stop="toggle(row)"><Icon v-if="row.node.children.length" :path="row.open ? mdiChevronDown : mdiChevronRight" :size="16" /></button>
              <b class="op">{{ row.node.op }}</b>
              <span v-if="row.node.relation" class="rel">{{ row.node.relation }}</span>
              <span v-if="row.full" class="tag warn" title="Reads every row of the table">full scan</span>
              <span v-if="row.node.detail" class="det" :title="row.node.detail">{{ row.node.detail }}</span>
            </span>
            <span v-if="hasEstimate" class="c-num mono">{{ count(row.node.rows) }}</span>
            <span v-if="hasActual" class="c-num mono">
              {{ count(row.node.actualRows) }}
              <Icon v-if="row.off" :path="mdiAlertOutline" :size="13" class="off" :title="`The planner expected ${count(row.node.rows)} rows: ${count(row.off)}× off`" />
            </span>
            <span v-if="hasTimes" class="c-num mono">{{ millis(row.node.actualMs) }}</span>
            <span v-if="hasCosts" class="c-num mono">{{ cost(row.node.cost) }}</span>
            <span v-if="metric" class="c-bar"><i :style="{ width: Math.max(row.share * 100, row.share > 0 ? 2 : 0) + '%' }" /><em class="mono">{{ row.share > 0.005 ? Math.round(row.share * 100) + '%' : '' }}</em></span>
          </div>
          <div v-if="rows.length === 0" class="empty">The engine returned no plan.</div>
        </div>

        <div v-else class="xp-raw">
          <button class="btn sm" @click="copyRaw"><Icon :path="mdiContentCopy" /> Copy</button>
          <pre class="selectable mono">{{ plan.raw }}</pre>
        </div>

        <div v-if="view === 'tree' && selected" class="xp-detail">
          <div class="xp-detail-head"><b>{{ selected.node.op }}</b><span v-if="selected.node.relation"> on {{ selected.node.relation }}</span></div>
          <div v-if="selected.node.detail" class="xp-detail-text mono selectable">{{ selected.node.detail }}</div>
          <dl v-if="attrs.length" class="selectable">
            <template v-for="[key, value] in attrs" :key="key"><dt>{{ key }}</dt><dd class="mono">{{ value }}</dd></template>
          </dl>
        </div>
      </template>
    </div>
    <template #footer>
      <button class="btn primary" @click="close">Close</button>
    </template>
  </AppDialog>
</template>

<style scoped>
.xp { display: flex; flex-direction: column; min-height: 320px; max-height: 70vh; }
.xp-bar { display: flex; align-items: center; gap: 10px; padding: 10px 14px; border-bottom: 1px solid var(--border); }
.bar-sep { height: 18px; margin: 0 2px; }
.xp-note { display: flex; align-items: center; gap: 6px; padding: 6px 14px; font-size: 12px; color: var(--warn); border-bottom: 1px solid var(--border); }
.xp-sql { padding: 6px 14px; font-size: 12px; color: var(--text-2); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; border-bottom: 1px solid var(--border); background: var(--bg-side); }
.xp-error { margin: 14px; padding: 10px 12px; border: 1px solid var(--danger); border-radius: var(--radius); color: var(--danger); white-space: pre-wrap; word-break: break-word; font-size: 12.5px; }

.xp-tree { flex: 1; min-height: 120px; overflow: auto; }
.xp-tree.stale { opacity: 0.55; }
.xp-head, .xp-row { display: flex; align-items: center; gap: 12px; min-height: 28px; padding: 0 14px; }
.xp-head { position: sticky; top: 0; z-index: 1; background: var(--bg-side); border-bottom: 1px solid var(--border); color: var(--text-3); font-size: 11px; text-transform: uppercase; letter-spacing: 0.05em; }
.xp-row { border-bottom: 1px solid var(--border); cursor: default; }
.xp-row:hover { background: var(--bg-hover); }
.xp-row.sel { background: var(--accent-tint-strong); }
.c-step { flex: 1; min-width: 0; display: flex; align-items: center; gap: 6px; white-space: nowrap; }
.c-num { width: 84px; flex: none; text-align: right; font-size: 12px; display: inline-flex; justify-content: flex-end; align-items: center; gap: 4px; }
.c-bar { width: 150px; flex: none; position: relative; height: 14px; display: flex; align-items: center; }
.c-bar i { display: block; height: 100%; border-radius: 2px; background: var(--ok); opacity: 0.75; }
.xp-row.warm .c-bar i { background: var(--warn); }
.xp-row.hot .c-bar i { background: var(--danger); }
.c-bar em { font-style: normal; font-size: 11px; color: var(--text-2); margin-left: 6px; }
.chev { width: 16px; height: 16px; display: grid; place-items: center; color: var(--text-3); flex: none; }
.op { font-weight: 600; flex: none; }
.rel { color: var(--accent); flex: none; }
.det { color: var(--text-3); overflow: hidden; text-overflow: ellipsis; min-width: 0; font-size: 12px; }
.tag { flex: none; font-size: 10.5px; padding: 0 5px; border-radius: 3px; border: 1px solid var(--border-strong); color: var(--text-2); }
.tag.warn { color: var(--warn); border-color: var(--warn); }
.off { color: var(--warn); }

.xp-raw { flex: 1; min-height: 0; overflow: auto; padding: 10px 14px; display: flex; flex-direction: column; gap: 8px; align-items: flex-start; }
.xp-raw pre { margin: 0; align-self: stretch; padding: 10px; border: 1px solid var(--border); border-radius: var(--radius); background: var(--bg); font-size: 12px; white-space: pre-wrap; word-break: break-word; user-select: text; }

.xp-detail { flex: none; max-height: 180px; overflow: auto; padding: 10px 14px; border-top: 1px solid var(--border); background: var(--bg-side); font-size: 12.5px; }
.xp-detail-head { margin-bottom: 4px; }
.xp-detail-head span { color: var(--text-2); }
.xp-detail-text { margin-bottom: 6px; color: var(--text-2); font-size: 12px; white-space: pre-wrap; word-break: break-word; }
dl { margin: 0; display: grid; grid-template-columns: max-content 1fr; gap: 2px 14px; }
dt { color: var(--text-3); }
dd { margin: 0; font-size: 12px; word-break: break-word; }
</style>
