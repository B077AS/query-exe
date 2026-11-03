<script setup lang="ts">
import { mdiAlertCircleOutline, mdiCheckCircleOutline, mdiClose, mdiLoading, mdiTableLarge } from '@mdi/js';
import { computed } from 'vue';
import type { QueryTab } from '../store';
import { confirm } from '../ui';
import Icon from './Icon.vue';
import ResultGrid from './ResultGrid.vue';

const props = defineProps<{ tab: QueryTab }>();
const emit = defineEmits<{ 'goto-error': [from: number, to: number] }>();

async function close(index: number): Promise<void> {
  const it = props.tab.items[index];
  if (it.kind === 'grid' && (it.grid.pending.updates + it.grid.pending.inserts + it.grid.pending.deletes) > 0
    && !(await confirm('Close this result?', 'It has changes that were not applied.', 'Close', true))) return;
  props.tab.items.splice(index, 1);
  props.tab.active = Math.max(0, Math.min(props.tab.active > index ? props.tab.active - 1 : props.tab.active, props.tab.items.length - 1));
}

const item = computed(() => props.tab.items[props.tab.active]);
const label = (i: number): string => {
  const it = props.tab.items[i];
  return it.kind === 'error' ? 'Error' : `Result ${i + 1}`;
};
const summary = computed(() => {
  if (props.tab.running) return 'Running…';
  if (!props.tab.ran) return '';
  const n = props.tab.items.length;
  return `${props.tab.lastOk ? 'Done' : 'Failed'} · ${n} result${n === 1 ? '' : 's'} · ${props.tab.lastMillis} ms`;
});
</script>

<template>
  <div class="panel">
    <div class="rtabs">
      <div v-for="(it, i) in tab.items" :key="it.key" class="rt" :class="{ on: i === tab.active, bad: it.kind === 'error' }" role="tab" @click="tab.active = i">
        <Icon :path="it.kind === 'error' ? mdiAlertCircleOutline : it.kind === 'grid' ? mdiTableLarge : mdiCheckCircleOutline" :size="15" />{{ label(i) }}
        <button class="x" title="Close this result" @click.stop="close(i)"><Icon :path="mdiClose" :size="13" /></button>
      </div>
      <span class="sp" />
      <span class="state" :class="{ ok: tab.ran && tab.lastOk, bad: tab.ran && !tab.lastOk }">
        <Icon v-if="tab.running" :path="mdiLoading" :size="14" class="spin" />{{ summary }}
      </span>
    </div>

    <div class="content">
      <ResultGrid v-if="item && item.kind === 'grid'" :key="item.grid.id" :grid="item.grid" />
      <div v-else-if="item && item.kind === 'count'" class="msg ok">
        <Icon :path="mdiCheckCircleOutline" :size="20" />
        <div>
          <div class="t">{{ item.count }} row{{ item.count === 1 ? '' : 's' }} affected</div>
          <div class="hint">{{ item.millis }} ms</div>
          <pre class="sql selectable mono">{{ item.sql }}</pre>
        </div>
      </div>
      <div v-else-if="item && item.kind === 'error'" class="msg bad">
        <Icon :path="mdiAlertCircleOutline" :size="20" />
        <div style="min-width: 0">
          <div class="t">The statement failed</div>
          <pre class="err selectable mono">{{ item.message }}</pre>
          <pre class="sql selectable mono">{{ item.sql }}</pre>
          <button v-if="item.to > item.from" class="btn sm" @click="emit('goto-error', item.from, item.to)">Show in editor</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.panel { display: flex; flex-direction: column; height: 100%; min-height: 0; background: var(--bg); }
.rtabs { display: flex; align-items: center; gap: 2px; padding: 0 10px; height: 32px; border-bottom: 1px solid var(--border); flex: none; }
.rt { display: flex; align-items: center; gap: 6px; height: 32px; padding: 0 10px; color: var(--text-2); border-bottom: 2px solid transparent; }
.rt:hover { color: var(--text); }
.rt.on { color: var(--text); border-bottom-color: var(--accent); }
.rt.bad { color: var(--danger); }
.rt .x { width: 18px; height: 18px; display: grid; place-items: center; border-radius: var(--radius); color: var(--text-3); margin-left: 2px; }
.rt .x:hover { background: var(--bg-hover); color: var(--text); }
.rt { cursor: pointer; }
.state { display: flex; align-items: center; gap: 6px; color: var(--text-3); font-size: 12px; }
.state.ok { color: var(--ok); }
.state.bad { color: var(--danger); }
.content { flex: 1; min-height: 0; overflow: auto; }
.msg { display: flex; gap: 12px; padding: 16px; align-items: flex-start; }
.msg.ok > .icon { color: var(--ok); }
.msg.bad > .icon { color: var(--danger); }
.t { font-weight: 600; }
pre { margin: 6px 0; white-space: pre-wrap; word-break: break-word; font-size: 12px; user-select: text; }
.err { color: var(--danger); }
.sql { color: var(--text-2); max-height: 160px; overflow: auto; background: var(--bg-side); border: 1px solid var(--border); border-radius: var(--radius); padding: 8px; }
</style>
