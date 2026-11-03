<script setup lang="ts">
import { onMounted } from 'vue';
import { api } from './api';
import ActiveConnectionsDialog from './components/ActiveConnectionsDialog.vue';
import ColumnInfoDialog from './components/ColumnInfoDialog.vue';
import ConfirmDialog from './components/ConfirmDialog.vue';
import ConnectionDialog from './components/ConnectionDialog.vue';
import NewContainerDialog from './components/NewContainerDialog.vue';
import PasswordDialog from './components/PasswordDialog.vue';
import PromptDialog from './components/PromptDialog.vue';
import ScriptDialog from './components/ScriptDialog.vue';
import SettingsDialog from './components/SettingsDialog.vue';
import TableEditor from './components/TableEditor.vue';
import TableInfoDialog from './components/TableInfoDialog.vue';
import Toasts from './components/Toasts.vue';
import Workspace from './components/Workspace.vue';
import { app, dialogs, initStore, loadConnections } from './store';

onMounted(async () => {
  initStore();
  await loadConnections();
  app.ready = true;
  // the first frame is painted: now the window may appear
  requestAnimationFrame(() => requestAnimationFrame(() => void api.windowReady().catch(() => undefined)));
});
</script>

<template>
  <Workspace v-if="app.ready" />

  <ConnectionDialog v-if="dialogs.connection" :id="dialogs.connection.id" :type="dialogs.connection.type" :folder="dialogs.connection.folder" />
  <PasswordDialog v-if="dialogs.password" />
  <SettingsDialog v-if="dialogs.settings" />
  <ActiveConnectionsDialog v-if="dialogs.activeConnections" />
  <TableEditor v-if="dialogs.tableEditor" :connection-id="dialogs.tableEditor.connectionId" :container="dialogs.tableEditor.container" :table="dialogs.tableEditor.table" />
  <TableInfoDialog v-if="dialogs.tableInfo" />
  <ColumnInfoDialog v-if="dialogs.columnInfo" />
  <ScriptDialog v-if="dialogs.script" />
  <NewContainerDialog v-if="dialogs.newContainer" />
  <PromptDialog />
  <ConfirmDialog />
  <Toasts />
</template>
