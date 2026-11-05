<script setup lang="ts">
import { mdiFormatListBulleted, mdiKeyVariant, mdiLinkVariant, mdiSortAscending, mdiTable } from '@mdi/js';
import { computed, onMounted, ref } from 'vue';
import { api, type TableInfo } from '../api';
import { dialogs } from '../store';
import { fail } from '../ui';
import AppDialog from './AppDialog.vue';
import Icon from './Icon.vue';

const state = dialogs.tableInfo!;
const info = ref<TableInfo | null>(null);
const tab = ref<'columns' | 'foreignKeys' | 'indexes'>('columns');

const views = computed(() => [
  { value: 'columns' as const, label: 'Columns', icon: mdiFormatListBulleted, count: info.value?.columns.length },
  { value: 'foreignKeys' as const, label: 'Foreign keys', icon: mdiLinkVariant, count: info.value?.foreignKeys.length },
  { value: 'indexes' as const, label: 'Indexes', icon: mdiSortAscending, count: info.value?.indexes.length },
]);

onMounted(async () => {
  try {
    info.value = await api.tableInfo(state.connectionId, state.container, state.table);
  } catch (e) {
    fail('Could not read the table', e);
    dialogs.tableInfo = null;
  }
});
</script>

<template>
  <AppDialog title="Table properties" :width="960" flush @close="dialogs.tableInfo = null">
    <template #icon><Icon :path="mdiTable" style="color: var(--accent)" /></template>
    <div class="info">
      <nav class="rail">
        <button v-for="v in views" :key="v.value" class="rail-item" :class="{ on: tab === v.value }" @click="tab = v.value">
          <Icon :path="v.icon" :size="18" />{{ v.label }}<span class="sp" /><span v-if="v.count" class="n">{{ v.count }}</span>
        </button>
      </nav>

      <div class="page">
        <div class="head">
          <h3>{{ state.table }}</h3>
          <span class="pill">{{ state.container }}</span><span v-if="info?.sizeMb" class="pill">{{ info.sizeMb.toFixed(2) }} MB</span>
        </div>
        <div v-if="!info" class="empty">Loading…</div>

        <div v-else-if="tab === 'columns'" class="box">
          <table>
            <thead><tr><th /><th>Column</th><th>Type</th><th>Not null</th><th>Unique</th><th>Auto</th><th>Default</th></tr></thead>
            <tbody>
              <tr v-for="c in info.columns" :key="c.columnName">
                <td style="width: 28px"><Icon v-if="c.primaryKey" :path="mdiKeyVariant" :size="14" /></td>
                <td class="mono">{{ c.columnName }}</td><td class="mono">{{ c.dataType }}</td>
                <td>{{ c.notNull ? 'yes' : '' }}</td><td>{{ c.unique ? 'yes' : '' }}</td><td>{{ c.autoIncrement ? 'yes' : '' }}</td>
                <td class="mono">{{ c.defaultValue ?? '' }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <template v-else-if="tab === 'foreignKeys'">
          <div v-if="!info.foreignKeys.length" class="empty">This table has no foreign keys.</div>
          <div v-else class="box">
            <table>
              <thead><tr><th>Name</th><th>Column</th><th>References</th><th>On delete</th><th>On update</th></tr></thead>
              <tbody>
                <tr v-for="f in info.foreignKeys" :key="f.constraintName">
                  <td class="mono">{{ f.constraintName }}</td><td class="mono">{{ f.localColumn }}</td>
                  <td class="mono">{{ f.referenceTable }}({{ f.referenceColumn }})</td><td>{{ f.onDelete }}</td><td>{{ f.onUpdate }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </template>

        <template v-else>
          <div v-if="!info.indexes.length" class="empty">This table has no indexes.</div>
          <div v-else class="box">
            <table>
              <thead><tr><th /><th>Name</th><th>Columns</th><th>Unique</th><th>Type</th></tr></thead>
              <tbody>
                <tr v-for="i in info.indexes" :key="i.name">
                  <td style="width: 28px"><Icon v-if="i.primary" :path="mdiKeyVariant" :size="14" /></td>
                  <td class="mono">{{ i.name }}</td><td class="mono">{{ i.columns.join(', ') }}</td>
                  <td>{{ i.unique ? 'yes' : '' }}</td><td>{{ i.type ?? '' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </template>
      </div>
    </div>
    <template #footer>
      <button class="btn primary" @click="dialogs.tableInfo = null">Close</button>
    </template>
  </AppDialog>
</template>

<style scoped>
/* the same rail and page as the Settings and table editor dialogs */
.info { display: grid; grid-template-columns: 190px 1fr; height: 460px; min-height: 0; }
.page { padding: 22px 28px; min-width: 0; display: flex; flex-direction: column; gap: 12px; min-height: 0; }
.head { display: flex; align-items: center; gap: 10px; }
.head h3 { font-size: 18px; font-weight: 600; margin: 0; margin-right: 4px; }
.n { font-size: 11px; color: var(--text-3); }
.box { flex: 1; min-height: 0; border: 1px solid var(--border); border-radius: var(--radius); overflow: auto; }
table { width: 100%; border-collapse: collapse; }
th { text-align: left; font-weight: 600; font-size: 11.5px; color: var(--text-2); padding: 6px 10px; background: var(--bg-side); border-bottom: 1px solid var(--border); position: sticky; top: 0; }
td { padding: 5px 10px; border-bottom: 1px solid var(--border); font-size: 12px; user-select: text; }
</style>
