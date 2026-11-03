<script setup lang="ts">
import { mdiClose } from '@mdi/js';
import { DialogClose, DialogContent, DialogDescription, DialogOverlay, DialogPortal, DialogRoot, DialogTitle } from 'reka-ui';
import Icon from './Icon.vue';

// A modal dialog: a title bar, a scrolling body and an optional footer. Closing (X, Escape, click outside) emits `close`.
withDefaults(defineProps<{ title: string; width?: number; closable?: boolean; flush?: boolean }>(), { width: 520, closable: true, flush: false });
const emit = defineEmits<{ close: [] }>();
</script>

<template>
  <DialogRoot :open="true" @update:open="(open: boolean) => !open && emit('close')">
    <DialogPortal>
      <DialogOverlay class="scrim" />
      <DialogContent class="dlg" :style="{ width: width + 'px' }" @interact-outside="(e: Event) => closable || e.preventDefault()"
        @escape-key-down="(e: Event) => closable || e.preventDefault()">
        <div class="dlg-head">
          <slot name="icon" />
          <DialogTitle as="h2">{{ title }}</DialogTitle>
          <slot name="head" />
          <span class="sp" />
          <DialogClose v-if="closable" class="ib sm" aria-label="Close"><Icon :path="mdiClose" /></DialogClose>
        </div>
        <DialogDescription class="sr-only" style="display: none">{{ title }}</DialogDescription>
        <div class="dlg-body" :style="flush ? { padding: 0, gap: 0 } : undefined"><slot /></div>
        <div v-if="$slots.footer" class="dlg-foot"><slot name="footer" /></div>
      </DialogContent>
    </DialogPortal>
  </DialogRoot>
</template>
