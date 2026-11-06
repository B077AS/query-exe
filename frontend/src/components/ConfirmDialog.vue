<script setup lang="ts">
import { answerConfirm, confirmState } from '../ui';
import AppDialog from './AppDialog.vue';
</script>

<template>
  <AppDialog v-if="confirmState.open" :title="confirmState.title" :width="440" @close="answerConfirm(false)">
    <p style="color: var(--text-2); white-space: pre-wrap">{{ confirmState.text }}</p>
    <template #footer>
      <button class="btn" @click="answerConfirm(false)">Cancel</button>
      <button class="btn" :class="confirmState.danger ? 'danger solid' : 'primary'" @click="answerConfirm(true)">{{ confirmState.action }}</button>
    </template>
  </AppDialog>
</template>
