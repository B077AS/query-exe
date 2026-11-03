<script setup lang="ts">
import { mdiCheck, mdiDeleteOutline, mdiLinkVariantPlus, mdiPlus, mdiTable } from '@mdi/js';
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue';
import { api, type ForeignKeyDef, type TableDraft } from '../api';
import { app, dialogs, refreshContainer } from '../store';
import { errorText, fail, toast } from '../ui';
import AppDialog from './AppDialog.vue';
import Icon from './Icon.vue';
import Pick from './Pick.vue';
import TypeInput from './TypeInput.vue';

const props = defineProps<{ connectionId: string; container: string; table?: string }>();
const editing = computed(() => !!props.table);

interface Row {
  name: string;
  type: string;
  primaryKey: boolean;
  notNull: boolean;
  unique: boolean;
  autoIncrement: boolean;
  defaultValue: string;
  original: string;
}

const name = ref(props.table ?? '');
const columns = reactive<Row[]>([]);
const fks = reactive<ForeignKeyDef[]>([]);
const dataTypes = ref<string[]>([]);
const tables = ref<string[]>([]);
const refColumns = reactive<Record<string, string[]>>({});
const tab = ref<'columns' | 'keys' | 'sql'>('columns');
const sqlText = ref('');
/** Why there is no script to show (neutral text, not an error): nothing typed yet, nothing changed, or the engine cannot do it. */
const sqlNote = ref('');
const loading = ref(!!props.table); // a new table has nothing to load: the form is there at once, with its first column
const busy = ref(false);

const rules = ['NO ACTION', 'CASCADE', 'SET NULL', 'SET DEFAULT', 'RESTRICT'];
const columnNames = computed(() => columns.map((c) => c.name.trim()).filter(Boolean));
const engine = computed(() => app.sessions[props.connectionId]?.info.type);

function blank(): Row {
  return { name: '', type: '', primaryKey: false, notNull: false, unique: false, autoIncrement: false, defaultValue: '', original: '' };
}

// a new table starts with an id column already in place, so nothing is added after the dialog opens
if (!props.table) columns.push({ ...blank(), name: 'id', primaryKey: true, notNull: true, autoIncrement: true });

onMounted(async () => {
  try {
    const d = await api.describeTable(props.connectionId, props.container, props.table ?? '');
    dataTypes.value = d.dataTypes;
    tables.value = d.tables;
    for (const c of d.columns) {
      columns.push({ name: c.columnName, type: c.dataType, primaryKey: c.primaryKey, notNull: c.notNull, unique: c.unique, autoIncrement: c.autoIncrement, defaultValue: c.defaultValue ?? '', original: c.columnName });
    }
    for (const fk of d.foreignKeys) {
      fks.push({ ...fk });
      void loadRefColumns(fk.referenceTable);
    }
  } catch (e) {
    fail('Could not load the table', e);
    dialogs.tableEditor = null;
  } finally {
    loading.value = false;
  }
});

async function loadRefColumns(table: string): Promise<void> {
  if (!table || refColumns[table]) return;
  try {
    const d = await api.describeTable(props.connectionId, props.container, table);
    refColumns[table] = d.columns.map((c) => c.columnName);
  } catch {
    refColumns[table] = [];
  }
}

function draft(): TableDraft {
  const renames: Record<string, string> = {};
  for (const c of columns) {
    const now = c.name.trim();
    if (c.original && now && c.original !== now) renames[c.original] = now;
  }
  return {
    connectionId: props.connectionId,
    container: props.container,
    table: name.value.trim(),
    originalTable: props.table,
    columns: columns.map(({ name: n, type, primaryKey, notNull, unique, autoIncrement, defaultValue }) => ({ name: n, type, primaryKey, notNull, unique, autoIncrement, defaultValue })),
    foreignKeys: fks.map((f) => ({ ...f })),
    renames,
  };
}

let timer: ReturnType<typeof setTimeout> | undefined;
let generation = 0;
function schedule(): void {
  clearTimeout(timer);
  timer = setTimeout(async () => {
    const mine = ++generation;
    if (name.value.trim() === '') {
      sqlText.value = '';
      sqlNote.value = 'Name the table to see its script.';
      return;
    }
    if (!columns.some((c) => c.name.trim() && c.type.trim())) {
      sqlText.value = '';
      sqlNote.value = 'Give a column a name and a type to see the script.';
      return;
    }
    try {
      const { sql } = await api.previewTable(draft());
      if (mine !== generation) return;
      sqlText.value = sql;
      sqlNote.value = sql.trim() === '' ? 'No changes.' : '';
    } catch (e) {
      if (mine !== generation) return;
      sqlText.value = '';
      sqlNote.value = errorText(e);
    }
  }, 300);
}
watch([name, columns, fks], schedule, { deep: true, immediate: true });

const colsEl = ref<HTMLDivElement>();

function addColumn(): void {
  columns.push(blank());
  // the new row is at the bottom of a list that may be scrolled: bring it into view
  void nextTick(() => colsEl.value?.scrollTo({ top: colsEl.value.scrollHeight }));
}

function removeColumn(i: number): void {
  const removed = columns.splice(i, 1)[0];
  for (let k = fks.length - 1; k >= 0; k--) if (fks[k].localColumn === removed.name) fks.splice(k, 1);
}

function addFk(): void {
  fks.push({ constraintName: '', localColumn: '', referenceTable: '', referenceColumn: '', onDelete: 'NO ACTION', onUpdate: 'NO ACTION' });
  tab.value = 'keys';
}

async function setRefTable(fk: ForeignKeyDef, table: string): Promise<void> {
  fk.referenceTable = table;
  fk.referenceColumn = '';
  await loadRefColumns(table);
}

async function save(): Promise<void> {
  if (sqlText.value.trim() === '') return;
  busy.value = true;
  try {
    await api.executeScript(props.connectionId, sqlText.value);
    const session = app.sessions[props.connectionId];
    if (session) await refreshContainer(session, props.container);
    toast('ok', editing.value ? 'Table changed' : 'Table created', name.value);
    dialogs.tableEditor = null;
  } catch (e) {
    toast('error', 'The script failed', errorText(e));
  } finally {
    busy.value = false;
  }
}
</script>

<template>
  <AppDialog :title="editing ? 'Edit table' : 'New table'" :width="1120" @close="dialogs.tableEditor = null">
    <template #icon><Icon :path="mdiTable" style="color: var(--accent)" /></template>
    <template #head><span class="pill">{{ container }}</span></template>

    <template v-if="true">
      <div class="row">
        <div class="field"><label>Table name</label><input v-model="name" class="input mono" placeholder="table_name" autofocus /></div>
        <div class="seg" style="align-self: flex-end">
          <button :class="{ on: tab === 'columns' }" @click="tab = 'columns'">Columns <span class="badge">{{ columns.length }}</span></button>
          <button :class="{ on: tab === 'keys' }" @click="tab = 'keys'">Foreign keys <span class="badge">{{ fks.length }}</span></button>
          <button :class="{ on: tab === 'sql' }" @click="tab = 'sql'">SQL</button>
        </div>
        <button v-if="tab === 'columns'" class="btn" style="align-self: flex-end" @click="addColumn"><Icon :path="mdiPlus" /> Add column</button>
        <button v-else-if="tab === 'keys'" class="btn" style="align-self: flex-end" @click="addFk"><Icon :path="mdiLinkVariantPlus" /> Add foreign key</button>
      </div>

      <div v-show="tab === 'columns'" ref="colsEl" class="cols">
        <table>
          <thead><tr><th>Column</th><th style="width: 190px">Type</th><th class="c">PK</th><th class="c">Not null</th><th class="c">Unique</th><th class="c">Auto</th><th style="width: 130px">Default</th><th style="width: 32px" /></tr></thead>
          <tbody>
            <tr v-for="(c, i) in columns" :key="i">
              <td><input v-model="c.name" class="input mono" placeholder="name" /></td>
              <td><TypeInput v-model="c.type" :options="dataTypes" /></td>
              <td class="c"><button class="chk" :class="{ on: c.primaryKey }" @click="c.primaryKey = !c.primaryKey; if (c.primaryKey) c.notNull = true"><Icon v-if="c.primaryKey" :path="mdiCheck" :size="11" /></button></td>
              <td class="c"><button class="chk" :class="{ on: c.notNull }" :disabled="c.primaryKey" @click="c.notNull = !c.notNull"><Icon v-if="c.notNull" :path="mdiCheck" :size="11" /></button></td>
              <td class="c"><button class="chk" :class="{ on: c.unique }" @click="c.unique = !c.unique"><Icon v-if="c.unique" :path="mdiCheck" :size="11" /></button></td>
              <td class="c"><button class="chk" :class="{ on: c.autoIncrement }" @click="c.autoIncrement = !c.autoIncrement"><Icon v-if="c.autoIncrement" :path="mdiCheck" :size="11" /></button></td>
              <td><input v-model="c.defaultValue" class="input mono" placeholder="—" /></td>
              <td><button class="ib sm" title="Remove column" @click="removeColumn(i)"><Icon :path="mdiDeleteOutline" /></button></td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-show="tab === 'keys'" class="cols">
        <table v-if="fks.length">
          <thead><tr><th>Name</th><th>Column</th><th>References</th><th>Column</th><th style="width: 120px">On delete</th><th style="width: 120px">On update</th><th style="width: 32px" /></tr></thead>
          <tbody>
            <tr v-for="(f, i) in fks" :key="i">
              <td><input v-model="f.constraintName" class="input mono" :placeholder="`fk_${name || 'table'}_${f.localColumn || 'col'}`" /></td>
              <td><Pick v-model="f.localColumn" :options="columnNames" /></td>
              <td><Pick :model-value="f.referenceTable" :options="tables" @update:model-value="setRefTable(f, $event)" /></td>
              <td><Pick v-model="f.referenceColumn" :options="refColumns[f.referenceTable] ?? []" :disabled="!f.referenceTable" /></td>
              <td><Pick v-model="f.onDelete" :options="rules" /></td>
              <td><Pick v-model="f.onUpdate" :options="rules" /></td>
              <td><button class="ib sm" @click="fks.splice(i, 1)"><Icon :path="mdiDeleteOutline" /></button></td>
            </tr>
          </tbody>
        </table>
        <p v-else class="empty">No foreign keys. Use “Add foreign key” above.</p>
      </div>

      <div v-show="tab === 'sql'" class="sqlbox">
        <pre v-if="sqlText" class="selectable mono">{{ sqlText }}</pre>
        <p v-else class="hint">{{ sqlNote }}</p>
      </div>
      <p v-if="editing && engine === 'SQLite'" class="hint">SQLite can only rename tables and columns, add nullable columns and drop columns in place.</p>
    </template>

    <template #footer>
      <button class="btn" @click="dialogs.tableEditor = null">Cancel</button>
      <button class="btn primary" :disabled="loading || busy || !sqlText.trim()" @click="save">{{ busy ? 'Running…' : editing ? 'Apply changes' : 'Create table' }}</button>
    </template>
  </AppDialog>
</template>

<style scoped>
.cols { border: 1px solid var(--border); border-radius: var(--radius); height: 340px; overflow: auto; }
table { width: 100%; border-collapse: collapse; }
th { text-align: left; font-weight: 600; font-size: 11.5px; color: var(--text-2); padding: 6px 6px; background: var(--bg-side); border-bottom: 1px solid var(--border); position: sticky; top: 0; z-index: 1; }
td { padding: 4px 6px; border-bottom: 1px solid var(--border); }
.c { text-align: center; width: 62px; }
td.c .chk { cursor: pointer; }
.chk:disabled { opacity: 0.5; }
.sqlbox { border: 1px solid var(--border); border-radius: var(--radius); background: var(--bg); padding: 10px; height: 340px; overflow: auto; }
pre { margin: 0; font-size: 12px; white-space: pre-wrap; user-select: text; }
</style>
