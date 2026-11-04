<script setup lang="ts">
import { mdiFitToScreenOutline, mdiGraphOutline, mdiKeyVariant, mdiMagnifyMinusOutline, mdiMagnifyPlusOutline, mdiViewGridOutline } from '@mdi/js';
import { computed, nextTick, onMounted, reactive, ref } from 'vue';
import { api, type DiagramData } from '../api';
import { dialogs } from '../store';
import { fail } from '../ui';
import AppDialog from './AppDialog.vue';
import Icon from './Icon.vue';

// An entity-relationship diagram of one database/schema: a card per table, a line per foreign key. Cards can be dragged,
// the background pans, the wheel zooms.
const state = dialogs.diagram!;
const data = ref<DiagramData | null>(null);
const stage = ref<HTMLElement | null>(null);

const CARD_W = 232;
const HEAD_H = 30;
const ROW_H = 22;
const PAD_Y = 6;
const GAP_X = 90;
const GAP_Y = 50;
const STUB = 20;

const pos = reactive<Record<string, { x: number; y: number }>>({});
const view = reactive({ x: 20, y: 20, k: 1 });
const hover = ref<string | null>(null);
const search = ref('');

const heightOf = (table: DiagramData['tables'][number]): number => HEAD_H + table.columns.length * ROW_H + PAD_Y;

/** Tables ordered so related ones are neighbours (breadth first from the most connected table), then laid out in rows. */
function layout(d: DiagramData): void {
  const links = new Map<string, Set<string>>();
  for (const t of d.tables) links.set(t.name, new Set());
  for (const r of d.relations) {
    links.get(r.from)?.add(r.to);
    links.get(r.to)?.add(r.from);
  }
  const remaining = new Set(d.tables.map((t) => t.name));
  const order: string[] = [];
  while (remaining.size) {
    let start = '';
    for (const name of remaining) {
      if (!start || links.get(name)!.size > links.get(start)!.size) start = name;
    }
    const queue = [start];
    remaining.delete(start);
    while (queue.length) {
      const name = queue.shift()!;
      order.push(name);
      const next = [...links.get(name)!].filter((n) => remaining.has(n)).sort((a, b) => links.get(b)!.size - links.get(a)!.size);
      for (const n of next) {
        remaining.delete(n);
        queue.push(n);
      }
    }
  }
  const byName = new Map(d.tables.map((t) => [t.name, t]));
  const perRow = Math.max(1, Math.ceil(Math.sqrt(order.length * 1.4)));
  let y = 0;
  for (let i = 0; i < order.length; i += perRow) {
    const row = order.slice(i, i + perRow);
    row.forEach((name, j) => (pos[name] = { x: j * (CARD_W + GAP_X), y }));
    y += Math.max(...row.map((n) => heightOf(byName.get(n)!))) + GAP_Y;
  }
}

function fit(): void {
  const el = stage.value;
  if (!el || !data.value?.tables.length) return;
  let maxX = 0;
  let maxY = 0;
  for (const t of data.value.tables) {
    const p = pos[t.name];
    if (!p) continue;
    maxX = Math.max(maxX, p.x + CARD_W);
    maxY = Math.max(maxY, p.y + heightOf(t));
  }
  const k = Math.min(1, (el.clientWidth - 40) / maxX, (el.clientHeight - 40) / maxY);
  view.k = Math.max(0.1, k);
  view.x = Math.max(20, (el.clientWidth - maxX * view.k) / 2);
  view.y = 20;
}

function arrange(): void {
  if (!data.value) return;
  layout(data.value);
  fit();
}

onMounted(async () => {
  try {
    const d = await api.diagram(state.connectionId, state.container);
    data.value = d;
    layout(d);
    await nextTick();
    fit();
  } catch (e) {
    fail('Could not build the diagram', e);
    dialogs.diagram = null;
  }
});

// ---- relations as lines ---------------------------------------------------------------------------------------------

interface Line { key: string; table: string; other: string; d: string; childMark: string; parentMark: string; name: string }

function rowY(table: string, column: string): number {
  const t = data.value!.tables.find((x) => x.name === table)!;
  const index = Math.max(0, t.columns.findIndex((c) => c.name === column));
  return pos[table].y + HEAD_H + index * ROW_H + ROW_H / 2;
}

const lines = computed<Line[]>(() => {
  const out: Line[] = [];
  if (!data.value) return out;
  for (const r of data.value.relations) {
    if (!pos[r.from] || !pos[r.to]) continue;
    r.fromColumns.forEach((fromColumn, i) => {
      const toColumn = r.toColumns[i] ?? r.toColumns[0];
      const a = pos[r.from];
      const b = pos[r.to];
      // leave each card through the side that faces the other one (both on the right for a table pointing at itself)
      let sa = 1;
      let sb = -1;
      if (a.x > b.x + CARD_W - 1) { sa = -1; sb = 1; }
      else if (a.x + CARD_W <= b.x + 1) { sa = 1; sb = -1; }
      else { sa = 1; sb = 1; }
      const x1 = a.x + (sa > 0 ? CARD_W : 0);
      const x2 = b.x + (sb > 0 ? CARD_W : 0);
      const y1 = rowY(r.from, fromColumn);
      const y2 = rowY(r.to, toColumn);
      // each end runs straight out of the card for STUB px (that is where the markers sit), then the curve joins the two stub ends
      const s1x = x1 + sa * STUB;
      const s2x = x2 + sb * STUB;
      const reach = sa === sb ? 50 : Math.max(30, Math.min(120, Math.abs(s2x - s1x) / 2));
      const d = `M${x1},${y1} L${s1x},${y1} C${s1x + sa * reach},${y1} ${s2x + sb * reach},${y2} ${s2x},${y2} L${x2},${y2}`;
      // crow's foot on the table holding the key (toes on the card edge, joined on the line), a bar on the referenced one
      const childMark = `M${x1},${y1 - 5} L${x1 + sa * 12},${y1} L${x1},${y1 + 5}`;
      const parentMark = `M${x2 + sb * 9},${y2 - 5} L${x2 + sb * 9},${y2 + 5}`;
      out.push({ key: `${r.name}:${i}`, table: r.from, other: r.to, d, childMark, parentMark, name: `${r.from}.${fromColumn} → ${r.to}.${toColumn}` });
    });
  }
  return out;
});

const linked = computed(() => {
  const set = new Set<string>();
  if (hover.value) {
    set.add(hover.value);
    for (const l of lines.value) {
      if (l.table === hover.value || l.other === hover.value) {
        set.add(l.table);
        set.add(l.other);
      }
    }
  }
  return set;
});

const active = (l: Line): boolean => !hover.value || l.table === hover.value || l.other === hover.value;

const matches = computed(() => {
  const q = search.value.trim().toLowerCase();
  return (name: string): boolean => !q || name.toLowerCase().includes(q);
});

// ---- pan, zoom, drag --------------------------------------------------------------------------------------------------

let drag: null | { kind: 'pan' | 'card'; name?: string; sx: number; sy: number; ox: number; oy: number } = null;

function down(e: PointerEvent, name?: string): void {
  if (e.button !== 0) return;
  e.stopPropagation();
  (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);
  if (name) drag = { kind: 'card', name, sx: e.clientX, sy: e.clientY, ox: pos[name].x, oy: pos[name].y };
  else drag = { kind: 'pan', sx: e.clientX, sy: e.clientY, ox: view.x, oy: view.y };
}

function move(e: PointerEvent): void {
  if (!drag) return;
  const dx = e.clientX - drag.sx;
  const dy = e.clientY - drag.sy;
  if (drag.kind === 'pan') {
    view.x = drag.ox + dx;
    view.y = drag.oy + dy;
  } else {
    pos[drag.name!] = { x: drag.ox + dx / view.k, y: drag.oy + dy / view.k };
  }
}

function up(): void {
  drag = null;
}

function zoomAt(factor: number, cx?: number, cy?: number): void {
  const el = stage.value;
  if (!el) return;
  const k = Math.min(2.5, Math.max(0.1, view.k * factor));
  const x = cx ?? el.clientWidth / 2;
  const y = cy ?? el.clientHeight / 2;
  view.x = x - ((x - view.x) / view.k) * k;
  view.y = y - ((y - view.y) / view.k) * k;
  view.k = k;
}

function wheel(e: WheelEvent): void {
  const rect = stage.value!.getBoundingClientRect();
  zoomAt(e.deltaY < 0 ? 1.12 : 1 / 1.12, e.clientX - rect.left, e.clientY - rect.top);
}
</script>

<template>
  <AppDialog title="ER diagram" :width="1200" flush @close="dialogs.diagram = null">
    <template #icon><Icon :path="mdiGraphOutline" style="color: var(--accent)" /></template>
    <template #head>
      <span class="pill">{{ state.container }}</span>
      <span v-if="data" class="pill">{{ data.tables.length }} tables<i class="vsep" />{{ data.relations.length }} relations</span>
    </template>
    <div class="bar">
      <input v-model="search" class="find" placeholder="Find a table…" spellcheck="false" />
      <span class="sp" />
      <button class="ib sm" title="Arrange again" @click="arrange"><Icon :path="mdiViewGridOutline" /></button>
      <button class="ib sm" title="Zoom out" @click="zoomAt(1 / 1.2)"><Icon :path="mdiMagnifyMinusOutline" /></button>
      <span class="zoom mono">{{ Math.round(view.k * 100) }}%</span>
      <button class="ib sm" title="Zoom in" @click="zoomAt(1.2)"><Icon :path="mdiMagnifyPlusOutline" /></button>
      <button class="ib sm" title="Fit to window" @click="fit"><Icon :path="mdiFitToScreenOutline" /></button>
    </div>
    <div ref="stage" class="stage" @pointerdown="down($event)" @pointermove="move" @pointerup="up" @pointercancel="up" @wheel.prevent="wheel">
      <div v-if="!data" class="empty">Loading…</div>
      <div v-else-if="!data.tables.length" class="empty">This {{ state.container }} has no tables.</div>
      <div v-else class="layer" :style="{ transform: `translate(${view.x}px, ${view.y}px) scale(${view.k})` }">
        <svg class="wires" width="1" height="1">
          <g v-for="l in lines" :key="l.key" :class="{ dim: !active(l), hot: !!hover && active(l) }">
            <path :d="l.d" class="wire"><title>{{ l.name }}</title></path>
            <path :d="l.childMark" class="mark" />
            <path :d="l.parentMark" class="mark" />
          </g>
        </svg>
        <div v-for="t in data.tables" :key="t.name" class="card"
          :class="{ dim: (!!hover && !linked.has(t.name)) || !matches(t.name), found: !!search.trim() && matches(t.name) }"
          :style="{ left: pos[t.name].x + 'px', top: pos[t.name].y + 'px', width: CARD_W + 'px' }"
          @pointerenter="hover = t.name" @pointerleave="hover = null">
          <div class="head" :style="{ height: HEAD_H + 'px' }" @pointerdown="down($event, t.name)">{{ t.name }}</div>
          <div v-for="c in t.columns" :key="c.name" class="row" :style="{ height: ROW_H + 'px' }">
            <Icon v-if="c.primaryKey" :path="mdiKeyVariant" :size="13" class="key" />
            <span v-else class="key" />
            <span class="cn mono" :class="{ pk: c.primaryKey }">{{ c.name }}</span>
            <span class="ct mono">{{ c.type }}</span>
          </div>
        </div>
      </div>
    </div>
    <template #footer>
      <span class="hint">Drag a table by its title<i class="vsep" />drag the background to pan<i class="vsep" />scroll to zoom</span>
      <span class="sp" />
      <button class="btn primary" @click="dialogs.diagram = null">Close</button>
    </template>
  </AppDialog>
</template>

<style scoped>
.bar { display: flex; align-items: center; gap: 4px; padding: 6px 10px; border-bottom: 1px solid var(--border); background: var(--bg-side); }
.find { height: 24px; width: 220px; padding: 0 8px; border: 1px solid var(--border); border-radius: var(--radius); background: var(--bg-raised); color: var(--text); font: inherit; font-size: 12px; outline: none; }
.find:focus { border-color: var(--accent); }
.sp { flex: 1; }
.zoom { font-size: 11.5px; color: var(--text-2); min-width: 40px; text-align: center; }
.stage { position: relative; height: min(72vh, 760px); overflow: hidden; background: var(--bg); cursor: grab; touch-action: none; user-select: none;
  background-image: radial-gradient(var(--border) 1px, transparent 1px); background-size: 20px 20px; }
.stage:active { cursor: grabbing; }
.empty { position: absolute; inset: 0; display: grid; place-items: center; color: var(--text-3); }
.layer { position: absolute; left: 0; top: 0; transform-origin: 0 0; }
.wires { position: absolute; left: 0; top: 0; overflow: visible; pointer-events: none; }
.wire { fill: none; stroke: var(--text-3); stroke-width: 1.4; }
.mark { fill: none; stroke: var(--text-3); stroke-width: 1.4; }
.hot .wire, .hot .mark { stroke: var(--accent); stroke-width: 2; }
.dim { opacity: 0.12; }
.card { position: absolute; background: var(--bg-raised); border: 1px solid var(--border-strong); border-radius: var(--radius-lg); box-shadow: var(--shadow); overflow: hidden; cursor: default; }
.card.dim { opacity: 0.25; }
.card.found { border-color: var(--accent); box-shadow: 0 0 0 2px var(--accent-tint-strong); }
.head { display: flex; align-items: center; padding: 0 10px; font-weight: 600; font-size: 12.5px; background: var(--accent-tint); border-bottom: 1px solid var(--border); cursor: grab; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.row { display: flex; align-items: center; gap: 6px; padding: 0 10px 0 8px; font-size: 11.5px; }
.row + .row { border-top: 1px solid color-mix(in srgb, var(--border) 50%, transparent); }
.key { width: 13px; flex: none; color: var(--warn); }
.cn { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cn.pk { font-weight: 600; }
.ct { color: var(--text-3); font-size: 10.5px; max-width: 90px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.hint { font-size: 11.5px; color: var(--text-3); }
</style>
