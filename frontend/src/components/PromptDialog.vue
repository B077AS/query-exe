<script setup lang="ts">
import { answerPrompt, promptState } from '../ui';
import AppDialog from './AppDialog.vue';

function submit(): void {
  const value = promptState.value.trim();
  answerPrompt(value === '' ? null : value);
}
</script>

<template>
  <AppDialog v-if="promptState.open" :title="promptState.title" :width="400" @close="answerPrompt(null)">
    <form class="field" @submit.prevent="submit">
      <label>{{ promptState.label }}</label>
      <input v-model="promptState.value" class="input" autofocus @keydown.enter.prevent="submit" />
    </form>
    <template #footer>
      <button class="btn" @click="answerPrompt(null)">Cancel</button>
      <button class="btn primary" :disabled="promptState.value.trim() === ''" @click="submit">{{ promptState.action }}</button>
    </template>
  </AppDialog>
</template>
