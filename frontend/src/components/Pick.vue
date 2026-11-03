<script setup lang="ts">
import { mdiCheck, mdiChevronDown } from '@mdi/js';
import { DropdownMenuContent, DropdownMenuItem, DropdownMenuPortal, DropdownMenuRoot, DropdownMenuTrigger } from 'reka-ui';
import Icon from './Icon.vue';

// A select box (the native <select> looks different in WebView2 and WebKitGTK, so we draw our own).
defineProps<{ modelValue: string; options: string[]; placeholder?: string; disabled?: boolean }>();
const emit = defineEmits<{ 'update:modelValue': [value: string] }>();
</script>

<template>
  <DropdownMenuRoot>
    <DropdownMenuTrigger class="input pick" :disabled="disabled">
      <span class="v" :class="{ ph: !modelValue }">{{ modelValue || placeholder || 'Select…' }}</span>
      <Icon :path="mdiChevronDown" />
    </DropdownMenuTrigger>
    <DropdownMenuPortal>
      <DropdownMenuContent class="menu" :side-offset="4" align="start" style="min-width: var(--reka-dropdown-menu-trigger-width); max-height: 280px">
        <DropdownMenuItem v-for="o in options" :key="o" class="menu-item" @select="emit('update:modelValue', o)">
          <Icon :path="o === modelValue ? mdiCheck : ''" />{{ o }}
        </DropdownMenuItem>
        <div v-if="options.length === 0" class="menu-label">No options</div>
      </DropdownMenuContent>
    </DropdownMenuPortal>
  </DropdownMenuRoot>
</template>

<style scoped>
.pick { display: flex; align-items: center; justify-content: space-between; gap: 6px; text-align: left; }
.v { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ph { color: var(--text-3); }
</style>
