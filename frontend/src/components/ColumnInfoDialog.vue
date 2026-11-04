<script setup lang="ts">
import { mdiTableColumn } from '@mdi/js';
import { computed, onMounted, ref } from 'vue';
import { api, type ColumnDetails } from '../api';
import { dialogs } from '../store';
import { fail } from '../ui';
import AppDialog from './AppDialog.vue';
import Icon from './Icon.vue';

const state = dialogs.columnInfo!;
const d = ref<ColumnDetails | null>(null);

onMounted(async () => {
  try {
    d.value = await api.columnInfo(state.connectionId, state.container, state.table, state.column);
  } catch (e) {
    fail('Could not read the column', e);
    dialogs.columnInfo = null;
  }
});

const yes = (v: boolean | undefined): string => (v ? 'Yes' : 'No');
const facts = computed<[string, string][]>(() => {
  const c = d.value;
  if (!c) return [];
  return [
    ['Type', c.columnType || c.dataType], ['Position', String(c.ordinalPosition)],
    ['Length', c.characterMaximumLength != null ? String(c.characterMaximumLength) : '—'],
    ['Precision', c.numericPrecision != null ? `${c.numericPrecision}${c.numericScale != null ? ',' + c.numericScale : ''}` : '—'],
    ['Collation', c.collationName ?? '—'], ['Default', c.columnDefault ?? '—'],
    ['Primary key', yes(c.isPrimaryKey)], ['Nullable', yes(c.isNullable)], ['Unique', yes(c.isUnique)], ['Auto increment', yes(c.isAutoIncrement)],
    ['Comment', c.columnComment || '—'],
  ];
});
const refs = (list: Record<string, string>[] | null): string[][] => (list ?? []).map((r) => Object.values(r).filter(Boolean));
</script>

<template>
  <AppDialog :title="state.column" :width="560" @close="dialogs.columnInfo = null">
    <template #icon><Icon :path="mdiTableColumn" style="color: var(--accent)" /></template>
    <template #head><span class="pill">{{ state.table }}</span></template>
    <div v-if="!d" class="empty">Loading…</div>
    <template v-else>
      <dl>
        <template v-for="[k, v] in facts" :key="k"><dt>{{ k }}</dt><dd class="mono selectable">{{ v }}</dd></template>
      </dl>
      <template v-if="refs(d.indexes).length"><div class="label">Indexes</div><div v-for="(r, k) in refs(d.indexes)" :key="k" class="mono ref selectable"><template v-for="(part, j) in r" :key="j"><i v-if="j" class="vsep" />{{ part }}</template></div></template>
      <template v-if="refs(d.foreignKeyReferences).length"><div class="label">References</div><div v-for="(r, k) in refs(d.foreignKeyReferences)" :key="k" class="mono ref selectable"><template v-for="(part, j) in r" :key="j"><i v-if="j" class="vsep" />{{ part }}</template></div></template>
      <template v-if="refs(d.referencedByForeignKeys).length"><div class="label">Referenced by</div><div v-for="(r, k) in refs(d.referencedByForeignKeys)" :key="k" class="mono ref selectable"><template v-for="(part, j) in r" :key="j"><i v-if="j" class="vsep" />{{ part }}</template></div></template>
    </template>
    <template #footer><button class="btn primary" @click="dialogs.columnInfo = null">Close</button></template>
  </AppDialog>
</template>

<style scoped>
dl { display: grid; grid-template-columns: 130px 1fr; gap: 6px 12px; margin: 0; }
dt { color: var(--text-2); }
dd { margin: 0; font-size: 12px; word-break: break-word; }
.ref { font-size: 12px; padding: 4px 8px; border: 1px solid var(--border); border-radius: var(--radius); background: var(--bg); }
</style>
