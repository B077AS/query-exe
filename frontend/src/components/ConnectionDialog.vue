<script setup lang="ts">
import { mdiCheck, mdiChevronDown, mdiDownload, mdiFolderOpenOutline, mdiLightningBolt, mdiRestore } from '@mdi/js';
import { DropdownMenuContent, DropdownMenuItem, DropdownMenuPortal, DropdownMenuRoot, DropdownMenuTrigger } from 'reka-ui';
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { api, type ConnectionForm, type DriverInfo } from '../api';
import { app, dialogs, reloadConnections } from '../store';
import { errorText, fail, toast } from '../ui';
import AppDialog from './AppDialog.vue';
import Icon from './Icon.vue';

const props = defineProps<{ id?: string; type?: string; folder?: string }>();
const editing = computed(() => !!props.id);

const form = reactive<ConnectionForm>({
  name: '', type: props.type ?? 'PostgreSQL', host: 'localhost', port: '5432', database: '', username: '',
  password: '', savePassword: true, url: '', driver: null,
});
const useUrl = ref(false);
const hadPassword = ref(false);
const tab = ref<'general' | 'driver'>('general');
const testing = ref(false);
const saving = ref(false);
const drivers = ref<DriverInfo[] | null>(null);
const driversError = ref('');
const loadingDrivers = ref(false);
const downloading = ref(false);

const engine = computed(() => app.types.find((t) => t.key === form.type));
const fileBased = computed(() => !!engine.value?.fileBased);
const isH2 = computed(() => form.type === 'H2');
/** H2 can also be reached over the network (jdbc:h2:tcp://host:port/db), with its own user and password. */
const h2Mode = ref<'file' | 'server'>('file');
const h2Server = computed(() => isH2.value && h2Mode.value === 'server');
/** SQLite files have no login; H2 (file or server) and every network engine do. */
const hasCredentials = computed(() => !fileBased.value || isH2.value);
const H2_TCP = /^jdbc:h2:tcp:\/\/([^:/]+)(?::(\d+))?\/(.*)$/i;
const bundled = computed(() => engine.value?.driver);
const currentDriver = computed(() => form.driver ?? bundled.value);

onMounted(async () => {
  if (!props.id) {
    applyType(form.type, true);
    return;
  }
  try {
    const info = await api.connection(props.id);
    Object.assign(form, {
      id: info.id, name: info.name, type: info.type, host: info.host ?? 'localhost', port: info.port ?? '', database: info.database ?? '',
      username: info.username ?? '', url: info.url ?? '', driver: info.driver, savePassword: info.hasPassword || info.type === 'SQLite',
    });
    hadPassword.value = info.hasPassword;
    useUrl.value = !!info.url && !fileBased.value;
    const tcp = info.type === 'H2' && info.url ? H2_TCP.exec(info.url) : null;
    if (tcp) {
      h2Mode.value = 'server';
      Object.assign(form, { host: tcp[1], port: tcp[2] ?? '9092', database: tcp[3] });
    }
  } catch (e) {
    fail('Could not load the connection', e);
    dialogs.connection = null;
  }
});

function applyType(type: string, initial = false): void {
  form.type = type;
  const t = app.types.find((x) => x.key === type);
  if (!t) return;
  if (t.fileBased) {
    if (initial || !form.url.startsWith('jdbc:')) form.url = t.jdbcPrefix;
    else form.url = t.jdbcPrefix;
    useUrl.value = false;
  } else {
    form.port = t.defaultPort;
    if (!useUrl.value) form.url = '';
  }
  if (type === 'H2') {
    form.port = '9092';
    h2Mode.value = 'file';
  }
  form.driver = null;
  drivers.value = null;
}

watch(useUrl, (on) => {
  if (on && !form.url) form.url = engine.value?.jdbcPrefix ?? '';
  if (!on && !fileBased.value) form.url = '';
});

async function browse(): Promise<void> {
  try {
    const r = await api.pickDatabase(form.type);
    if (!r.path) return;
    const path = form.type === 'H2' ? r.path.replace(/\.mv\.db$/i, '').replace(/\.h2\.db$/i, '') : r.path;
    form.url = (engine.value?.jdbcPrefix ?? '') + path;
    if (!form.name) form.name = path.split(/[\\/]/).pop()?.replace(/\.[^.]+$/, '') ?? '';
  } catch (e) {
    fail('Could not open the file dialog', e);
  }
}

/** Whether the form can be tested (and saved, which also needs a name). No messages: the buttons just stay disabled until it can. */
const testable = computed(() => {
  if (h2Server.value) return form.host.trim() !== '' && form.database.trim() !== '';
  if (fileBased.value || useUrl.value) {
    const url = form.url.trim();
    return url !== '' && url.toLowerCase() !== (engine.value?.jdbcPrefix ?? '').toLowerCase();
  }
  return form.host.trim() !== '';
});
const savable = computed(() => testable.value && form.name.trim() !== '');

function payload(): ConnectionForm {
  if (h2Server.value) {
    const url = `jdbc:h2:tcp://${form.host.trim()}:${form.port.trim() || '9092'}/${form.database.trim()}`;
    return { ...form, url };
  }
  return { ...form, url: fileBased.value || useUrl.value ? form.url.trim() : '' };
}

async function test(): Promise<void> {
  if (!testable.value) return;
  testing.value = true;
  try {
    await api.testConnection(payload());
    toast('ok', 'Connection successful', 'The database connection was established.');
  } catch (e) {
    toast('error', 'Connection failed', errorText(e));
  } finally {
    testing.value = false;
  }
}

async function save(): Promise<void> {
  if (!savable.value) return;
  saving.value = true;
  try {
    const saved = await api.saveConnection(payload());
    if (props.folder && !props.id) await api.setConnectionFolder(saved.id, props.folder);
    await reloadConnections();
    dialogs.connection = null;
    toast('ok', 'Connection saved');
  } catch (e) {
    toast('error', 'Could not save the connection', errorText(e));
  } finally {
    saving.value = false;
  }
}

async function loadDrivers(): Promise<void> {
  loadingDrivers.value = true;
  driversError.value = '';
  try {
    drivers.value = await api.drivers(form.type);
  } catch (e) {
    driversError.value = errorText(e);
  } finally {
    loadingDrivers.value = false;
  }
}

watch(tab, (t) => {
  if (t === 'driver' && drivers.value === null) void loadDrivers();
});

async function chooseDriver(d: DriverInfo): Promise<void> {
  if (!d.downloaded) {
    downloading.value = true;
    try {
      await api.downloadDriver(d);
      d.downloaded = true;
    } catch (e) {
      fail('Could not download the driver', e);
      return;
    } finally {
      downloading.value = false;
    }
  }
  form.driver = bundled.value && d.version === bundled.value.version && d.fileName === bundled.value.fileName ? null : { ...d };
}
</script>

<template>
  <AppDialog :title="editing ? 'Edit connection' : 'New connection'" :width="560" @close="dialogs.connection = null">
    <div class="seg" style="align-self: flex-start">
      <button :class="{ on: tab === 'general' }" @click="tab = 'general'">General</button>
      <button :class="{ on: tab === 'driver' }" @click="tab = 'driver'">Driver</button>
    </div>

    <template v-if="tab === 'general'">
      <div class="row">
        <div class="field"><label>Name</label><input v-model="form.name" class="input" placeholder="My database" autofocus /></div>
        <div class="field" style="flex: 0 0 180px">
          <label>Engine</label>
          <DropdownMenuRoot>
            <DropdownMenuTrigger class="input" style="display: flex; align-items: center; justify-content: space-between" :disabled="editing">
              {{ form.type === 'SQLServer' ? 'SQL Server' : form.type }}<Icon :path="mdiChevronDown" />
            </DropdownMenuTrigger>
            <DropdownMenuPortal>
              <DropdownMenuContent class="menu" :side-offset="4" align="start">
                <DropdownMenuItem v-for="t in app.types" :key="t.key" class="menu-item" @select="applyType(t.key)">
                  <Icon :path="t.key === form.type ? mdiCheck : ''" />{{ t.key === 'SQLServer' ? 'SQL Server' : t.key }}
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenuPortal>
          </DropdownMenuRoot>
        </div>
      </div>

      <div v-if="isH2" class="seg" style="align-self: flex-start">
        <button :class="{ on: h2Mode === 'file' }" @click="h2Mode = 'file'">File or in-memory</button>
        <button :class="{ on: h2Mode === 'server' }" @click="h2Mode = 'server'">Server (TCP)</button>
      </div>

      <template v-if="(fileBased && !h2Server) || useUrl">
        <div class="field">
          <label>{{ fileBased ? 'Database file' : 'JDBC URL' }}</label>
          <div style="display: flex; gap: 8px">
            <input v-model="form.url" class="input mono" :placeholder="engine?.jdbcPrefix + (fileBased ? '/path/to/database' : '')" />
            <button v-if="fileBased" class="btn" @click="browse"><Icon :path="mdiFolderOpenOutline" /> Browse</button>
          </div>
          <span v-if="form.type === 'H2'" class="hint">Use jdbc:h2:mem:name for an in-memory database, or the path without the .mv.db extension.</span>
        </div>
      </template>
      <template v-else>
        <div class="row">
          <div class="field"><label>Host</label><input v-model="form.host" class="input mono" placeholder="localhost" /></div>
          <div class="field" style="flex: 0 0 110px"><label>Port</label><input v-model="form.port" class="input mono" /></div>
        </div>
        <div v-if="h2Server" class="field"><label>Database path or name</label><input v-model="form.database" class="input mono" placeholder="~/test  or  mem:test" /><span class="hint">What follows the host in jdbc:h2:tcp://host:port/…</span></div>
        <div v-else class="field"><label>Database <span class="hint">(optional)</span></label><input v-model="form.database" class="input mono" placeholder="All databases" /></div>
      </template>

      <div v-if="hasCredentials" class="row">
        <div class="field"><label>User</label><input v-model="form.username" class="input" autocomplete="off" /></div>
        <div class="field">
          <label>Password</label>
          <input v-model="form.password" class="input" type="password" autocomplete="new-password"
            :placeholder="editing && hadPassword ? 'Saved — leave empty to keep' : ''" />
        </div>
      </div>
      <label v-if="hasCredentials" class="check-row" @click.prevent="form.savePassword = !form.savePassword">
        <span class="chk" :class="{ on: form.savePassword }"><Icon v-if="form.savePassword" :path="mdiCheck" :size="11" /></span>
        Save the password in the system credential store
      </label>
      <label v-if="!fileBased" class="check-row" @click.prevent="useUrl = !useUrl">
        <span class="chk" :class="{ on: useUrl }"><Icon v-if="useUrl" :path="mdiCheck" :size="11" /></span>
        Use a custom JDBC URL
      </label>
    </template>

    <template v-else>
      <div class="field">
        <label>JDBC driver in use</label>
        <div class="drv">
          <div>
            <div style="font-weight: 600">{{ currentDriver?.name }} {{ currentDriver?.version }}</div>
            <div class="hint mono">{{ currentDriver?.driverClass }}</div>
          </div>
          <span class="pill">{{ form.driver ? 'custom' : 'bundled' }}</span>
          <button v-if="form.driver" class="btn sm" @click="form.driver = null"><Icon :path="mdiRestore" /> Use bundled</button>
        </div>
      </div>
      <div class="field">
        <label>Other versions (from Maven Central)</label>
        <div v-if="loadingDrivers" class="hint">Loading versions…</div>
        <div v-else-if="driversError" class="error-text">{{ driversError }}</div>
        <div v-else class="versions">
          <button v-for="d in drivers ?? []" :key="d.version" class="ver" :class="{ on: currentDriver?.version === d.version }" :disabled="downloading" @click="chooseDriver(d)">
            <span class="mono">{{ d.version }}</span>
            <Icon v-if="currentDriver?.version === d.version" :path="mdiCheck" style="color: var(--accent)" />
            <Icon v-else-if="!d.downloaded" :path="mdiDownload" style="color: var(--text-3)" />
          </button>
        </div>
        <span class="hint">Downloaded drivers are kept in the app data folder and loaded only for this connection.</span>
      </div>
    </template>

    <template #footer>
      <button class="btn" :disabled="testing || !testable" @click="test"><Icon :path="mdiLightningBolt" />{{ testing ? 'Testing…' : 'Test connection' }}</button>
      <span class="sp" />
      <button class="btn" @click="dialogs.connection = null">Cancel</button>
      <button class="btn primary" :disabled="saving || !savable" @click="save">Save</button>
    </template>
  </AppDialog>
</template>

<style scoped>
.drv { display: flex; align-items: center; gap: 10px; padding: 10px 12px; border: 1px solid var(--border); border-radius: var(--radius); background: var(--bg); }
.drv > div:first-child { flex: 1; min-width: 0; }
.versions { display: grid; grid-template-columns: repeat(3, 1fr); gap: 6px; max-height: 190px; overflow: auto; }
.ver { display: flex; align-items: center; justify-content: space-between; height: 28px; padding: 0 8px; border: 1px solid var(--border); border-radius: var(--radius); }
.ver:hover:not(:disabled) { background: var(--bg-hover); }
.ver.on { border-color: var(--accent); background: var(--accent-tint); }
</style>
