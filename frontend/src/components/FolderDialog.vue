<script setup lang="ts">
import { mdiCheck } from '@mdi/js';
import { ACCENTS } from '../accents';
import { scheme } from '../theme';
import { answerFolder, folderState } from '../ui';
import AppDialog from './AppDialog.vue';
import Icon from './Icon.vue';

const shade = (key: string): string => {
  const a = ACCENTS.find((x) => x.key === key)!;
  return scheme.value === 'dark' ? a.dark : a.light;
};
const onShade = (key: string): string => {
  const a = ACCENTS.find((x) => x.key === key)!;
  return scheme.value === 'dark' ? a.onDark : a.onLight;
};

function submit(): void {
  const name = folderState.name.trim();
  if (name !== '') answerFolder({ name, color: folderState.color });
}
</script>

<template>
  <AppDialog v-if="folderState.open" title="New folder" :width="420" @close="answerFolder(null)">
    <form class="field" @submit.prevent="submit">
      <label>Folder name</label>
      <input v-model="folderState.name" class="input" autofocus @keydown.enter.prevent="submit" />
    </form>
    <div class="field">
      <label>Color</label>
      <div class="swatches">
        <button class="sw none" :class="{ on: folderState.color === null }" title="Default" @click="folderState.color = null" />
        <button v-for="a in ACCENTS" :key="a.key" class="sw" :class="{ on: folderState.color === a.key }" :style="{ background: shade(a.key), color: onShade(a.key) }" :title="a.name" @click="folderState.color = a.key">
          <Icon v-if="folderState.color === a.key" :path="mdiCheck" :size="16" />
        </button>
      </div>
    </div>
    <template #footer>
      <button class="btn" @click="answerFolder(null)">Cancel</button>
      <button class="btn primary" :disabled="folderState.name.trim() === ''" @click="submit">Create</button>
    </template>
  </AppDialog>
</template>

<style scoped>
.swatches { display: flex; flex-wrap: wrap; gap: 10px; padding: 3px; }
.sw { width: 28px; height: 28px; border-radius: var(--radius); display: grid; place-items: center; border: 2px solid transparent; }
.sw.none { border: 1px solid var(--text-3); background: var(--bg); }
.sw:hover { filter: brightness(1.08); }
.sw.on { outline: 2px solid var(--text-3); outline-offset: 2px; }
</style>
