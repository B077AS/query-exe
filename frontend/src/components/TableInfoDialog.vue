<script setup lang="ts">
import { mdiKeyVariant, mdiTable } from '@mdi/js';
import { onMounted, ref } from 'vue';
import { api, type TableInfo } from '../api';
import { dialogs } from '../store';
import { fail } from '../ui';
import AppDialog from './AppDialog.vue';
import Icon from './Icon.vue';

const state = dialogs.tableInfo!;
const info = ref<TableInfo | null>(null);

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
  <AppDialog :title="state.table" :width="960" @close="dialogs.tableInfo = null">
    <template #icon><Icon :path="mdiTable" style="color: var(--accent)" /></template>
    <template #head><span class="pill">{{ state.container }}</span><span v-if="info?.sizeMb" class="pill">{{ info.sizeMb.toFixed(2) }} MB</span></template>
    <div v-if="!info" class="empty">Loading…</div>
    <template v-else>
      <div class="box">
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
      <template v-if="info.foreignKeys.length">
        <div class="label">Foreign keys</div>
        <div class="box">
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
    </template>
    <template #footer>
      <button class="btn primary" @click="dialogs.tableInfo = null">Close</button>
    </template>
  </AppDialog>
</template>

<style scoped>
.box { border: 1px solid var(--border); border-radius: var(--radius); max-height: 340px; overflow: auto; }
table { width: 100%; border-collapse: collapse; }
th { text-align: left; font-weight: 600; font-size: 11.5px; color: var(--text-2); padding: 6px 10px; background: var(--bg-side); border-bottom: 1px solid var(--border); position: sticky; top: 0; }
td { padding: 5px 10px; border-bottom: 1px solid var(--border); font-size: 12px; user-select: text; }
</style>
