<script setup lang="ts">
import { mdiFileDocumentOutline, mdiFolderOpenOutline, mdiPlus } from '@mdi/js';
import { api } from '../api';
import { addFileTab, app, currentSession, dialogs, infoOf, openRecent } from '../store';
import { fail, toast } from '../ui';
import Icon from './Icon.vue';
import Logo from './Logo.vue';

async function openScript(): Promise<void> {
  const session = currentSession();
  if (!session) {
    toast('info', 'Pick a connection first', 'Double-click a connection on the left, then open the script.');
    return;
  }
  try {
    const opened = await api.openFile(session.connectionId);
    if (!opened.cancelled && opened.path) addFileTab(session.connectionId, opened.path, opened.name ?? 'Script', opened.text ?? '');
  } catch (e) {
    fail('Could not open the file', e);
  }
}
</script>

<template>
  <div class="empty-main">
    <Logo :size="88" bare />
    <h3>{{ app.connections.length ? 'Pick a connection to start' : 'Add your first connection' }}</h3>
    <p v-if="app.connections.length">Double-click one on the left to connect and browse it, or:</p>
    <p v-else>QueryExe connects to PostgreSQL, MySQL, MariaDB, SQL Server, H2 and SQLite.</p>
    <div class="act">
      <button class="btn primary" @click="dialogs.connection = {}"><Icon :path="mdiPlus" /> New connection</button>
      <button class="btn" @click="openScript"><Icon :path="mdiFolderOpenOutline" /> Open script</button>
    </div>
    <div v-if="app.recent.length" class="rec">
      <div class="h">Recent scripts</div>
      <button v-for="f in app.recent.slice(0, 6)" :key="f.path" class="r" :title="f.path" @click="openRecent(f)">
        <Icon :path="mdiFileDocumentOutline" class="mut" /><span class="name">{{ f.name }}</span><span class="sp" />
        <span v-if="f.connectionId && infoOf(f.connectionId)" class="pill">{{ infoOf(f.connectionId)?.name }}</span>
      </button>
    </div>
  </div>
</template>

<style scoped>
.empty-main { height: 100%; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 12px; color: var(--text-2); }
h3 { color: var(--text); font-size: 16px; }
.act { display: flex; gap: 8px; margin-top: 4px; }
.rec { width: 420px; margin-top: 10px; border: 1px solid var(--border); border-radius: var(--radius-lg); background: var(--bg-raised); overflow: hidden; }
.h { padding: 8px 12px; font-size: 11px; text-transform: uppercase; letter-spacing: 0.06em; color: var(--text-3); font-weight: 600; background: var(--bg-side); }
.r { display: flex; align-items: center; gap: 10px; width: 100%; height: 34px; padding: 0 12px; border-top: 1px solid var(--border); text-align: left; }
.r:hover { background: var(--bg-hover); }
.mut { color: var(--text-3); }
.name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>
