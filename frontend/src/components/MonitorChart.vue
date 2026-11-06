<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';

// A live line chart: the newest sample is at the right edge and the window slides. One axis (from 0), 2 px lines, hairline grid,
// a legend when there are two or more series, an end dot, and a crosshair with a tooltip.
const props = defineProps<{
  title: string;
  /** "" (a count), "/s", "B/s" or "s". */
  unit: string;
  /** How many samples fit across the chart. */
  capacity: number;
  /** Sample times (ms), oldest first. */
  times: number[];
  series: { key: string; label: string; values: (number | null)[] }[];
}>();

const HEIGHT = 156;
const M = { left: 46, right: 14, top: 8, bottom: 22 };

const root = ref<HTMLDivElement>();
const width = ref(420);
let observer: ResizeObserver | null = null;
onMounted(() => {
  if (!root.value) return;
  width.value = root.value.clientWidth;
  observer = new ResizeObserver(() => (width.value = root.value?.clientWidth ?? 420));
  observer.observe(root.value);
});
onBeforeUnmount(() => observer?.disconnect());

const plotW = computed(() => Math.max(40, width.value - M.left - M.right));
const plotH = HEIGHT - M.top - M.bottom;
const count = computed(() => props.times.length);
const step = computed(() => plotW.value / Math.max(1, props.capacity - 1));
const xAt = (i: number): number => M.left + plotW.value - (count.value - 1 - i) * step.value;

/** A round number at or above v (1, 2, 5, 10, 20, 50 ...). */
function niceCeil(v: number): number {
  if (v <= 0) return 1;
  const exp = Math.pow(10, Math.floor(Math.log10(v)));
  const f = v / exp;
  return (f <= 1 ? 1 : f <= 2 ? 2 : f <= 5 ? 5 : 10) * exp;
}

const yMax = computed(() => {
  let max = 0;
  for (const s of props.series) for (const v of s.values) if (v !== null && v > max) max = v;
  return niceCeil(max * 1.05);
});
const yAt = (v: number): number => M.top + plotH - (v / yMax.value) * plotH;
const ticks = computed(() => [0, yMax.value / 2, yMax.value]);

function compact(v: number): string {
  if (v >= 1e9) return +(v / 1e9).toFixed(1) + 'G';
  if (v >= 1e6) return +(v / 1e6).toFixed(1) + 'M';
  if (v >= 1e4) return +(v / 1e3).toFixed(1) + 'K';
  return Number.isInteger(v) ? String(v) : v >= 100 ? String(Math.round(v)) : v.toFixed(v >= 10 ? 1 : 2).replace(/\.?0+$/, '');
}

function bytes(v: number): string {
  const units = ['B', 'KB', 'MB', 'GB'];
  let n = v;
  let i = 0;
  while (n >= 1024 && i < units.length - 1) {
    n /= 1024;
    i++;
  }
  return (n >= 100 || i === 0 ? Math.round(n) : n.toFixed(1)) + ' ' + units[i];
}

function format(v: number | null | undefined): string {
  if (v === null || v === undefined) return '—';
  if (props.unit === 'B/s') return bytes(v) + '/s';
  if (props.unit === '/s') return compact(v) + '/s';
  if (props.unit === 's') return compact(v) + ' s';
  return compact(v);
}

/** Axis labels carry no unit text (the title does), except bytes, where the magnitude is part of the number. */
const axis = (v: number): string => (props.unit === 'B/s' ? bytes(v) : compact(v));

const colour = (i: number): string => `var(--series-${(i % 4) + 1})`;

/** A line broken where a sample is missing. */
function pathOf(values: (number | null)[]): string {
  let d = '';
  let open = false;
  values.forEach((v, i) => {
    if (v === null) {
      open = false;
      return;
    }
    d += `${open ? 'L' : 'M'}${xAt(i).toFixed(1)},${yAt(v).toFixed(1)}`;
    open = true;
  });
  return d;
}

const areaOf = (values: (number | null)[]): string => {
  const line = pathOf(values);
  const idx = values.map((v, i) => (v === null ? -1 : i)).filter((i) => i >= 0);
  if (!line || idx.length < 2 || line.includes('M', 1)) return '';
  return `${line}L${xAt(idx[idx.length - 1]).toFixed(1)},${yAt(0)}L${xAt(idx[0]).toFixed(1)},${yAt(0)}Z`;
};

const last = (values: (number | null)[]): number | null => (values.length ? values[values.length - 1] : null);

// ---- hover ----------------------------------------------------------------------------------------------------------

const hover = ref<number | null>(null);

function onMove(e: PointerEvent): void {
  const rect = root.value!.getBoundingClientRect();
  const x = e.clientX - rect.left;
  let best = -1;
  let bestDist = Infinity;
  for (let i = 0; i < count.value; i++) {
    const d = Math.abs(xAt(i) - x);
    if (d < bestDist) {
      best = i;
      bestDist = d;
    }
  }
  hover.value = best >= 0 && bestDist <= Math.max(step.value, 14) ? best : null;
}

const tipLeft = computed(() => {
  if (hover.value === null) return 0;
  const x = xAt(hover.value);
  return x > width.value / 2 ? x - 12 : x + 12;
});
const tipFlip = computed(() => hover.value !== null && xAt(hover.value) > width.value / 2);

const clock = (t: number): string => new Date(t).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });

const span = computed(() => {
  if (count.value < 2) return '';
  const s = Math.round((props.times[count.value - 1] - props.times[0]) / 1000);
  return s >= 120 ? `${Math.round(s / 60)} min ago` : `${s} s ago`;
});
</script>

<template>
  <div class="mchart">
    <div class="mhead">
      <span class="mtitle">{{ title }}<small v-if="unit && unit !== 's'">{{ unit }}</small></span>
      <span v-if="series.length === 1" class="mnow mono">{{ format(last(series[0].values)) }}</span>
    </div>
    <div v-if="series.length > 1" class="legend">
      <span v-for="(s, i) in series" :key="s.key" class="lg"><i class="sw" :style="{ background: colour(i) }" />{{ s.label }}<b class="mono">{{ format(last(s.values)) }}</b></span>
    </div>

    <div ref="root" class="plot" @pointermove="onMove" @pointerleave="hover = null">
      <svg :width="width" :height="HEIGHT" role="img" :aria-label="title">
        <g class="grid">
          <template v-for="t in ticks" :key="t">
            <line :x1="M.left" :x2="width - M.right" :y1="yAt(t)" :y2="yAt(t)" />
            <text :x="M.left - 8" :y="yAt(t) + 4" text-anchor="end">{{ axis(t) }}</text>
          </template>
          <text :x="M.left" :y="HEIGHT - 5" text-anchor="start">{{ span }}</text>
          <text :x="width - M.right" :y="HEIGHT - 5" text-anchor="end">now</text>
        </g>

        <template v-if="count > 0">
          <path v-if="series.length === 1" :d="areaOf(series[0].values)" class="area" :style="{ fill: colour(0) }" />
          <path v-for="(s, i) in series" :key="s.key" :d="pathOf(s.values)" class="line" :style="{ stroke: colour(i) }" />

          <line v-if="hover !== null" :x1="xAt(hover)" :x2="xAt(hover)" :y1="M.top" :y2="M.top + plotH" class="cross" />
          <template v-for="(s, i) in series" :key="'d' + s.key">
            <circle v-if="hover !== null && s.values[hover] !== null" :cx="xAt(hover)" :cy="yAt(s.values[hover]!)" r="4" class="dot" :style="{ fill: colour(i) }" />
            <circle v-else-if="hover === null && last(s.values) !== null" :cx="xAt(count - 1)" :cy="yAt(last(s.values)!)" r="4" class="dot" :style="{ fill: colour(i) }" />
          </template>
        </template>
      </svg>

      <div v-if="hover !== null" class="tip" :class="{ flip: tipFlip }" :style="{ left: tipLeft + 'px' }">
        <div class="tt">{{ clock(times[hover]) }}</div>
        <div v-for="(s, i) in series" :key="s.key" class="tr"><i class="sw" :style="{ background: colour(i) }" />{{ s.label }}<b class="mono">{{ format(s.values[hover]) }}</b></div>
      </div>
      <div v-if="count === 0" class="wait">Waiting for the first sample…</div>
    </div>
  </div>
</template>

<style>
/* categorical slots 1 to 4 of the data-viz palette (light, and the same hues stepped for dark); charts never use more than four series */
:root {
  --series-1: #2a78d6;
  --series-2: #eb6834;
  --series-3: #1baf7a;
  --series-4: #eda100;
}
:root[data-theme='dark'] {
  --series-1: #3987e5;
  --series-2: #d95926;
  --series-3: #199e70;
  --series-4: #c98500;
}
</style>

<style scoped>
.mchart { display: flex; flex-direction: column; gap: 6px; min-width: 0; }
.mhead { display: flex; align-items: baseline; gap: 8px; }
.mtitle { font-weight: 600; font-size: 12.5px; flex: 1; }
.mtitle small { margin-left: 6px; font-weight: 400; font-size: 11px; color: var(--text-3); }
.mnow { font-size: 15px; font-weight: 600; }
.legend { display: flex; flex-wrap: wrap; gap: 4px 14px; font-size: 11.5px; color: var(--text-2); }
.lg { display: inline-flex; align-items: center; gap: 6px; }
.lg b, .tr b { font-weight: 600; color: var(--text); }
.sw { width: 8px; height: 8px; border-radius: 50%; flex: none; display: inline-block; }
.plot { position: relative; height: 156px; }
svg { display: block; overflow: visible; }
.grid line { stroke: var(--border); stroke-width: 1; }
.grid text { fill: var(--text-3); font-size: 10.5px; font-family: var(--mono); }
.line { fill: none; stroke-width: 2; stroke-linejoin: round; stroke-linecap: round; }
.area { opacity: 0.1; stroke: none; }
.cross { stroke: var(--border-strong); stroke-width: 1; }
.dot { stroke: var(--bg); stroke-width: 2; }
.tip { position: absolute; top: 4px; transform: translateX(0); min-width: 150px; padding: 6px 9px; background: var(--bg-raised); border: 1px solid var(--border-strong);
  border-radius: var(--radius); box-shadow: var(--shadow); font-size: 11.5px; pointer-events: none; z-index: 2; }
.tip.flip { transform: translateX(-100%); }
.tt { color: var(--text-3); margin-bottom: 3px; font-size: 11px; }
.tr { display: flex; align-items: center; gap: 6px; color: var(--text-2); }
.tr b { margin-left: auto; padding-left: 12px; }
.wait { position: absolute; inset: 0; display: grid; place-items: center; color: var(--text-3); font-size: 12px; }
</style>
