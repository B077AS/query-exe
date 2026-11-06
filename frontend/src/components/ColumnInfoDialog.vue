<script setup lang="ts">
import { mdiFormatListBulleted, mdiLinkVariant, mdiLinkVariantPlus, mdiSortAscending, mdiTableColumn } from '@mdi/js';
import { computed, onMounted, ref } from 'vue';
import { api, type ColumnDetails } from '../api';
import { dialogs } from '../store';
import { fail } from '../ui';
import AppDialog from './AppDialog.vue';
import Icon from './Icon.vue';

const state = dialogs.columnInfo!;
const d = ref<ColumnDetails | null>(null);
const tab = ref<'details' | 'indexes' | 'references' | 'referencedBy'>('details');

onMounted(async () => {
  try {
    d.value = await api.columnInfo(state.connectionId, state.container, state.table, state.column);
  } catch (e) {
    fail('Could not read the column', e);
    dialogs.columnInfo = null;
  }
});

const yes = (v: boolean | undefined): string => (v ? 'yes' : 'no');
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

// The server decides which keys a row of indexes or references has; show them as columns, in the order they arrived.
const label = (key: string): string => key.replace(/([a-z])([A-Z])/g, '$1 $2').replace(/_/g, ' ').replace(/^./, (s) => s.toUpperCase());
function table(list: Record<string, string>[] | null): { head: string[]; rows: string[][] } {
  const rows = list ?? [];
  const keys: string[] = [];
  for (const r of rows) for (const k of Object.keys(r)) if (!keys.includes(k) && r[k]) keys.push(k);
  return { head: keys.map(label), rows: rows.map((r) => keys.map((k) => r[k] ?? '')) };
}
const indexes = computed(() => table(d.value?.indexes ?? null));
const references = computed(() => table(d.value?.foreignKeyReferences ?? null));
const referencedBy = computed(() => table(d.value?.referencedByForeignKeys ?? null));

const views = computed(() => [
  { value: 'details' as const, label: 'Details', icon: mdiFormatListBulleted, count: undefined },
  { value: 'indexes' as const, label: 'Indexes', icon: mdiSortAscending, count: indexes.value.rows.length },
  { value: 'references' as const, label: 'References', icon: mdiLinkVariant, count: references.value.rows.length },
  { value: 'referencedBy' as const, label: 'Referenced by', icon: mdiLinkVariantPlus, count: referencedBy.value.rows.length },
]);
const shown = computed(() => (tab.value === 'indexes' ? indexes.value : tab.value === 'references' ? references.value : referencedBy.value));
const emptyText = computed(() => ({ indexes: 'No index uses this column.', references: 'This column does not reference another table.', referencedBy: 'No other table references this column.', details: '' })[tab.value]);
</script>

<template>
  <AppDialog title="Column properties" :width="860" flush @close="dialogs.columnInfo = null">
    <template #icon><Icon :path="mdiTableColumn" style="color: var(--accent)" /></template>
    <div class="info">
      <nav class="rail">
        <button v-for="v in views" :key="v.value" class="rail-item" :class="{ on: tab === v.value }" @click="tab = v.value">
          <Icon :path="v.icon" :size="18" />{{ v.label }}<span class="sp" /><span v-if="v.count" class="n">{{ v.count }}</span>
        </button>
      </nav>

      <div class="page">
        <div class="head">
          <h3>{{ state.column }}</h3>
          <span class="pill">{{ state.table }}</span><span class="pill">{{ state.container }}</span>
        </div>
        <div v-if="!d" class="empty">Loading…</div>

        <div v-else-if="tab === 'details'" class="box">
          <table class="dgrid">
            <tbody>
              <tr v-for="[k, v] in facts" :key="k"><th class="row">{{ k }}</th><td :class="{ nul: v === '—' }">{{ v }}</td></tr>
            </tbody>
          </table>
        </div>

        <template v-else>
          <div v-if="!shown.rows.length" class="empty">{{ emptyText }}</div>
          <div v-else class="box">
            <table class="dgrid">
              <thead><tr><th class="g">#</th><th v-for="h in shown.head" :key="h">{{ h }}</th></tr></thead>
              <tbody>
                <tr v-for="(r, i) in shown.rows" :key="i"><td class="g">{{ i + 1 }}</td><td v-for="(cell, j) in r" :key="j">{{ cell }}</td></tr>
              </tbody>
            </table>
          </div>
        </template>
      </div>
    </div>
    <template #footer><button class="btn primary" @click="dialogs.columnInfo = null">Close</button></template>
  </AppDialog>
</template>

<style scoped>
/* the same rail and page as the Settings, table editor and table properties dialogs */
.info { display: grid; grid-template-columns: 190px 1fr; height: 460px; min-height: 0; }
.page { padding: 22px 28px; min-width: 0; display: flex; flex-direction: column; gap: 12px; min-height: 0; }
.head { display: flex; align-items: center; gap: 10px; }
.head h3 { font-size: 18px; font-weight: 600; margin: 0; margin-right: 4px; }
.n { font-size: 11px; color: var(--text-3); }
.box { flex: 1; min-height: 0; border: 1px solid var(--border); border-radius: var(--radius); overflow: auto; }
</style>
