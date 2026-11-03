<script setup lang="ts">
import { ref } from 'vue';
import { api } from '../api';
import { app, dialogs, loadTree } from '../store';
import { errorText, toast } from '../ui';
import AppDialog from './AppDialog.vue';

const state = dialogs.newContainer!;
const name = ref('');
const busy = ref(false);

async function create(): Promise<void> {
  if (name.value.trim() === '') return;
  busy.value = true;
  try {
    await api.createContainer(state.connectionId, name.value.trim());
    const session = app.sessions[state.connectionId];
    if (session) await loadTree(session);
    toast('ok', `${state.kind === 'schema' ? 'Schema' : 'Database'} created`, name.value.trim());
    dialogs.newContainer = null;
  } catch (e) {
    toast('error', 'Could not create it', errorText(e));
  } finally {
    busy.value = false;
  }
}
</script>

<template>
  <AppDialog :title="state.kind === 'schema' ? 'New schema' : 'New database'" :width="400" @close="dialogs.newContainer = null">
    <form class="field" @submit.prevent="create">
      <label>Name</label>
      <input v-model="name" class="input mono" autofocus />
    </form>
    <template #footer>
      <button class="btn" @click="dialogs.newContainer = null">Cancel</button>
      <button class="btn primary" :disabled="busy || !name.trim()" @click="create">Create</button>
    </template>
  </AppDialog>
</template>
