<script setup lang="ts">
import { mdiCheck } from '@mdi/js';
import { ref } from 'vue';
import { connect, dialogs } from '../store';
import AppDialog from './AppDialog.vue';
import Icon from './Icon.vue';

const state = dialogs.password!;
const password = ref('');
const remember = ref(false);
const busy = ref(false);

async function submit(): Promise<void> {
  busy.value = true;
  try {
    await connect(state.info, password.value, remember.value);
  } finally {
    busy.value = false;
  }
}
</script>

<template>
  <AppDialog :title="`Connect to ${state.info.name}`" :width="420" @close="dialogs.password = null">
    <form class="field" @submit.prevent="submit">
      <label>Password for {{ state.info.username || 'this connection' }}</label>
      <input v-model="password" class="input" type="password" autofocus autocomplete="off" />
    </form>
    <label class="check-row" @click.prevent="remember = !remember">
      <span class="chk" :class="{ on: remember }"><Icon v-if="remember" :path="mdiCheck" :size="11" /></span>
      Remember it in the system credential store
    </label>
    <template #footer>
      <button class="btn" @click="dialogs.password = null">Cancel</button>
      <button class="btn primary" :disabled="busy" @click="submit">{{ busy ? 'Connecting…' : 'Connect' }}</button>
    </template>
  </AppDialog>
</template>

