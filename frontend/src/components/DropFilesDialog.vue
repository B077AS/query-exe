<script setup lang="ts">
import { mdiDatabase, mdiDatabaseOutline } from '@mdi/js';
import { computed } from 'vue';
import type { ConnectionInfo } from '../api';
import { app, connect, dialogs, openDroppedFiles } from '../store';
import { engineBadge } from '../ui';
import AppDialog from './AppDialog.vue';
import Icon from './Icon.vue';

/** Files were dropped while no connection was selected: pick the one they open on. */
const files = computed(() => dialogs.dropFiles?.files ?? []);
const rows = computed(() => [...app.connections].sort((a, b) => Number(!!app.sessions[b.id]) - Number(!!app.sessions[a.id]) || a.name.localeCompare(b.name)));

async function choose(info: ConnectionInfo): Promise<void> {
  const dropped = files.value;
  dialogs.dropFiles = null;
  if (!app.sessions[info.id] && !(await connect(info))) return;
  await openDroppedFiles(dropped, info.id);
}
</script>

<template>
  <AppDialog :title="files.length === 1 ? 'Open ' + files[0].name : `Open ${files.length} files`" :width="440" @close="dialogs.dropFiles = null">
    <p class="say">Which connection should {{ files.length === 1 ? 'this script' : 'these scripts' }} run on?</p>
    <div v-if="rows.length === 0" class="none">No saved connections yet.</div>
    <button v-for="c in rows" :key="c.id" class="row" @click="choose(c)">
      <Icon :path="app.sessions[c.id] ? mdiDatabase : mdiDatabaseOutline" class="ic" :class="{ live: !!app.sessions[c.id] }" />
      <span class="name">{{ c.name }}</span>
      <span class="badge">{{ engineBadge(c.type) }}</span>
      <span class="state">{{ app.sessions[c.id] ? 'connected' : 'connects first' }}</span>
    </button>
    <template #footer>
      <button class="btn" @click="dialogs.dropFiles = null">Cancel</button>
    </template>
  </AppDialog>
</template>

<style scoped>
.say { margin: 0 0 10px; color: var(--text-2); }
.none { color: var(--text-3); padding: 8px 0; }
.row { display: flex; align-items: center; gap: 8px; width: 100%; height: 34px; padding: 0 10px; border: 1px solid var(--border); border-radius: var(--radius); background: var(--bg-raised); margin-bottom: 6px; text-align: left; }
.row:hover { background: var(--bg-hover); border-color: var(--border-strong); }
.ic { color: var(--text-3); }
.ic.live { color: var(--accent); }
.name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.badge { font: 700 9.5px var(--mono); padding: 1px 4px; border-radius: 3px; background: var(--accent-tint-strong); color: var(--accent); }
.state { color: var(--text-3); font-size: 11px; }
</style>
