<script setup lang="ts">
import { mdiAlertCircleOutline, mdiCheckCircleOutline, mdiClose, mdiInformationOutline } from '@mdi/js';
import { dismissToast, toasts } from '../ui';
import Icon from './Icon.vue';

const icons = { info: mdiInformationOutline, ok: mdiCheckCircleOutline, error: mdiAlertCircleOutline };
const colors = { info: 'var(--accent)', ok: 'var(--ok)', error: 'var(--danger)' };
</script>

<template>
  <div class="toasts">
    <div v-for="t in toasts" :key="t.id" class="toast" :class="t.kind">
      <Icon :path="icons[t.kind]" :size="18" :style="{ color: colors[t.kind], marginTop: '1px' }" />
      <div style="flex: 1; min-width: 0">
        <div class="t">{{ t.title }}</div>
        <div v-if="t.text" class="d">{{ t.text }}</div>
      </div>
      <button class="ib sm" aria-label="Dismiss" @click="dismissToast(t.id)"><Icon :path="mdiClose" /></button>
    </div>
  </div>
</template>
