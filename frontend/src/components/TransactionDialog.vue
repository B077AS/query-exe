<script setup lang="ts">
import { answerTransaction, txAsk } from '../ui';
import AppDialog from './AppDialog.vue';
</script>

<template>
  <AppDialog v-if="txAsk.open" :title="txAsk.title" :width="460" @close="answerTransaction(null)">
    <p style="color: var(--text-2); white-space: pre-wrap">{{ txAsk.text }}</p>
    <p style="color: var(--text-3); font-size: 12px">Closing without choosing would lose them: the database rolls back what was never committed.</p>
    <template #footer>
      <button class="btn" @click="answerTransaction(null)">Cancel</button>
      <button class="btn danger" @click="answerTransaction('rollback')">Roll back and {{ txAsk.verb }}</button>
      <button class="btn primary" @click="answerTransaction('commit')">Commit and {{ txAsk.verb }}</button>
    </template>
  </AppDialog>
</template>
