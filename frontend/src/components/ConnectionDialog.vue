<script setup lang="ts">
import { mdiCheck, mdiEyeOffOutline, mdiEyeOutline, mdiCogOutline, mdiPowerPlugOutline, mdiChevronDown, mdiDownload, mdiFolderOpenOutline, mdiLightningBolt, mdiRestore, mdiConsoleNetworkOutline } from '@mdi/js';
import { DropdownMenuContent, DropdownMenuItem, DropdownMenuPortal, DropdownMenuRoot, DropdownMenuTrigger } from 'reka-ui';
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { api, type ConnectionForm, type DriverInfo, type SshForm } from '../api';
import { app, dialogs, reloadConnections } from '../store';
import { errorText, fail, toast } from '../ui';
import AppDialog from './AppDialog.vue';
import Icon from './Icon.vue';
import Seg from './Seg.vue';

const props = defineProps<{ id?: string; type?: string; folder?: string }>();
const editing = computed(() => !!props.id);

const form = reactive<ConnectionForm>({
  name: '', type: props.type ?? 'PostgreSQL', host: 'localhost', port: '5432', database: '', username: '',
  password: '', savePassword: true, url: '', driver: null, ssh: null,
});
const ssh = reactive<SshForm & { enabled: boolean }>({ enabled: false, host: '', port: '22', user: '', auth: 'password', keyPath: '', password: '' });
const hadSshPassword = ref(false);
const showSshPassword = ref(false);
const useUrl = ref(false);
const hadPassword = ref(false);
const showPassword = ref(false);
const tab = ref<'general' | 'ssh' | 'driver'>('general');
const pages = computed(() => [
  { key: 'general' as const, label: 'General', icon: mdiCogOutline },
  ...(fileBased.value && !h2Server.value ? [] : [{ key: 'ssh' as const, label: 'SSH', icon: mdiConsoleNetworkOutline }]),
  { key: 'driver' as const, label: 'Driver', icon: mdiPowerPlugOutline },
]);
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
      username: info.username ?? '', password: info.password ?? '', url: info.url ?? '', driver: info.driver, savePassword: info.hasPassword || info.type === 'SQLite',
    });
    hadPassword.value = info.hasPassword;
    if (info.ssh) {
      Object.assign(ssh, { enabled: true, host: info.ssh.host, port: info.ssh.port || '22', user: info.ssh.user, auth: info.ssh.auth, keyPath: info.ssh.keyPath ?? '', password: info.ssh.password ?? '' });
      hadSshPassword.value = info.ssh.hasPassword;
    }
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
const sshActive = computed(() => ssh.enabled && !(fileBased.value && !h2Server.value));
const sshReady = computed(() => !sshActive.value || (ssh.host.trim() !== '' && ssh.user.trim() !== '' && (ssh.auth === 'password' || ssh.keyPath.trim() !== '')));
const savable = computed(() => testable.value && sshReady.value && form.name.trim() !== '');

async function browseKey(): Promise<void> {
  try {
    const r = await api.pickKey();
    if (r.path) ssh.keyPath = r.path;
  } catch (e) {
    fail('Could not open the file dialog', e);
  }
}

function payload(): ConnectionForm {
  const sshPart = sshActive.value ? { ...ssh } : null;
  if (h2Server.value) {
    const url = `jdbc:h2:tcp://${form.host.trim()}:${form.port.trim() || '9092'}/${form.database.trim()}`;
    return { ...form, url, ssh: sshPart };
  }
  return { ...form, url: fileBased.value || useUrl.value ? form.url.trim() : '', ssh: sshPart };
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
  <AppDialog :title="editing ? 'Edit connection' : 'New connection'" :width="720" flush @close="dialogs.connection = null">
    <div class="conn">
      <nav class="rail">
        <button v-for="p in pages" :key="p.key" class="rail-item" :class="{ on: tab === p.key }" @click="tab = p.key"><Icon :path="p.icon" :size="18" />{{ p.label }}</button>
      </nav>
      <div class="page">
        <h3>{{ pages.find((p) => p.key === tab)?.label }}</h3>
        <!-- Both pages sit in the same grid cell and only one is visible, so the dialog is always as tall as the taller one and never resizes when switching. -->
        <div class="stack">
    <div class="pane" :class="{ off: tab !== 'general' }">
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
                <DropdownMenuItem v-for="t in app.types" :key="t.key" class="menu-item" :class="{ on: t.key === form.type }" @select="applyType(t.key)">{{ t.key === 'SQLServer' ? 'SQL Server' : t.key }}</DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenuPortal>
          </DropdownMenuRoot>
        </div>
      </div>

      <Seg v-if="isH2" v-model="h2Mode" style="align-self: flex-start" :options="[{ value: 'file', label: 'File or in-memory' }, { value: 'server', label: 'Server (TCP)' }]" />

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
          <div class="pw">
            <input v-model="form.password" class="input" :type="showPassword ? 'text' : 'password'" autocomplete="new-password"
              :placeholder="editing && hadPassword ? 'Saved — leave empty to keep' : ''" />
            <button type="button" class="ib sm pw-eye" tabindex="-1" :title="showPassword ? 'Hide password' : 'Show password'" @click="showPassword = !showPassword">
              <Icon :path="showPassword ? mdiEyeOffOutline : mdiEyeOutline" :size="16" />
            </button>
          </div>
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
    </div>

    <div class="pane" :class="{ off: tab !== 'ssh' }">
      <label class="check-row" @click.prevent="ssh.enabled = !ssh.enabled">
        <span class="chk" :class="{ on: ssh.enabled }"><Icon v-if="ssh.enabled" :path="mdiCheck" :size="11" /></span>
        Connect through an SSH tunnel
      </label>
      <template v-if="ssh.enabled">
        <div class="row">
          <div class="field"><label>SSH host</label><input v-model="ssh.host" class="input mono" placeholder="bastion.example.com" /></div>
          <div class="field" style="flex: 0 0 110px"><label>Port</label><input v-model="ssh.port" class="input mono" placeholder="22" /></div>
        </div>
        <div class="field"><label>SSH user</label><input v-model="ssh.user" class="input" autocomplete="off" /></div>
        <Seg v-model="ssh.auth" style="align-self: flex-start" :options="[{ value: 'password', label: 'Password' }, { value: 'key', label: 'Private key' }]" />
        <div v-if="ssh.auth === 'key'" class="field">
          <label>Private key file</label>
          <div style="display: flex; gap: 8px">
            <input v-model="ssh.keyPath" class="input mono" placeholder="~/.ssh/id_ed25519" />
            <button class="btn" @click="browseKey"><Icon :path="mdiFolderOpenOutline" /> Browse</button>
          </div>
        </div>
        <div class="field">
          <label>{{ ssh.auth === 'key' ? 'Key passphrase' : 'SSH password' }}<span v-if="ssh.auth === 'key'" class="hint"> (if the key has one)</span></label>
          <div class="pw">
            <input v-model="ssh.password" class="input" :type="showSshPassword ? 'text' : 'password'" autocomplete="new-password"
              :placeholder="editing && hadSshPassword ? 'Saved — leave empty to keep' : ''" />
            <button type="button" class="ib sm pw-eye" tabindex="-1" :title="showSshPassword ? 'Hide' : 'Show'" @click="showSshPassword = !showSshPassword">
              <Icon :path="showSshPassword ? mdiEyeOffOutline : mdiEyeOutline" :size="16" />
            </button>
          </div>
          <span class="hint">Kept in the system credential store. The host and port on the General page are the database as the SSH server sees it.</span>
        </div>
      </template>
    </div>

    <div class="pane" :class="{ off: tab !== 'driver' }">
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
        <div v-if="driversError" class="error-text">{{ driversError }}</div>
        <!-- Until the list has arrived (also the frame before the load starts) show placeholders of the same size, so the dialog does not jump. -->
        <div v-else-if="drivers === null" class="versions skeleton" aria-busy="true"><span v-for="n in 18" :key="n" class="ver" /></div>
        <div v-else class="versions">
          <button v-for="d in drivers ?? []" :key="d.version" class="ver" :class="{ on: currentDriver?.version === d.version }" :disabled="downloading" @click="chooseDriver(d)">
            <span class="mono">{{ d.version }}</span>
            <Icon v-if="currentDriver?.version === d.version" :path="mdiCheck" style="color: var(--accent)" />
            <Icon v-else-if="!d.downloaded" :path="mdiDownload" style="color: var(--text-3)" />
          </button>
        </div>
        <span class="hint">Downloaded drivers are kept in the app data folder and loaded only for this connection.</span>
      </div>
    </div>
        </div>
      </div>
    </div>

    <template #footer>
      <button class="btn" :disabled="testing || !testable" @click="test"><Icon :path="mdiLightningBolt" />{{ testing ? 'Testing…' : 'Test connection' }}</button>
      <span class="sp" />
      <button class="btn" @click="dialogs.connection = null">Cancel</button>
      <button class="btn primary" :disabled="saving || !savable" @click="save">Save</button>
    </template>
  </AppDialog>
</template>

<style scoped>
.pw { position: relative; display: flex; }
.pw .input { flex: 1; padding-right: 32px; }
.pw-eye { position: absolute; right: 4px; top: 50%; transform: translateY(-50%); }
.drv { display: flex; align-items: center; gap: 10px; padding: 10px 12px; border: 1px solid var(--border); border-radius: var(--radius); background: var(--bg); }
.drv > div:first-child { flex: 1; min-width: 0; }
/* the same rail and page as the Settings window */
.conn { display: grid; grid-template-columns: 190px 1fr; min-height: 0; }
.page { padding: 22px 28px; min-width: 0; }
h3 { font-size: 18px; font-weight: 600; margin-bottom: 18px; }
.stack { display: grid; }
.pane { grid-area: 1 / 1; display: flex; flex-direction: column; gap: 12px; min-width: 0; }
.pane.off { visibility: hidden; }
.versions { display: grid; grid-template-columns: repeat(3, 1fr); gap: 6px; max-height: 164px; overflow: auto; }
.ver { display: flex; align-items: center; justify-content: space-between; height: 28px; padding: 0 8px; border: 1px solid var(--border); border-radius: var(--radius); }
.ver:hover:not(:disabled) { background: var(--bg-hover); }
.ver.on { border-color: var(--accent); background: var(--accent-tint); }
/* placeholders while the driver versions load: same grid and cell size as the real list */
.skeleton { overflow: hidden; pointer-events: none; }
.skeleton .ver { background: var(--bg-hover); border-color: transparent; animation: pulse 1.2s ease-in-out infinite; }
.skeleton .ver:nth-child(3n+2) { animation-delay: 0.1s; }
.skeleton .ver:nth-child(3n) { animation-delay: 0.2s; }
@keyframes pulse { 50% { opacity: 0.45; } }
</style>
