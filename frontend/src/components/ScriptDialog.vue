<script setup lang="ts">
import { mdiContentCopy } from '@mdi/js';
import { dialogs } from '../store';
import { fail, toast } from '../ui';
import AppDialog from './AppDialog.vue';
import Icon from './Icon.vue';

const state = dialogs.script!;

async function copy(): Promise<void> {
  try {
    await navigator.clipboard.writeText(state.sql);
    toast('info', 'Copied');
  } catch (e) {
    fail('Could not copy', e);
  }
}
</script>

<template>
  <AppDialog :title="state.title" :width="640" @close="dialogs.script = null">
    <pre class="selectable mono">{{ state.sql }}</pre>
    <template #footer>
      <button class="btn" @click="copy"><Icon :path="mdiContentCopy" /> Copy</button>
      <button class="btn primary" @click="dialogs.script = null">Close</button>
    </template>
  </AppDialog>
</template>

<style scoped>
pre { margin: 0; padding: 10px; border: 1px solid var(--border); border-radius: var(--radius); background: var(--bg); max-height: 50vh; overflow: auto; white-space: pre-wrap; word-break: break-word; font-size: 12px; user-select: text; }
</style>
