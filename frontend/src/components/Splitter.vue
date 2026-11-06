<script setup lang="ts">
// A draggable divider between two panes. `vertical` splits left/right (it resizes a width), otherwise top/bottom (a height).
// `modelValue` is the size of the pane before (or, with `reverse`, after) the divider.
const props = withDefaults(defineProps<{ modelValue: number; min?: number; max?: number; vertical?: boolean; reverse?: boolean }>(), { min: 120, max: 900 });
const emit = defineEmits<{ 'update:modelValue': [value: number]; commit: [value: number] }>();

function start(event: PointerEvent): void {
  event.preventDefault();
  const startPos = props.vertical ? event.clientX : event.clientY;
  const startSize = props.modelValue;
  let size = startSize;
  const move = (e: PointerEvent): void => {
    const delta = (props.vertical ? e.clientX : e.clientY) - startPos;
    size = Math.round(Math.min(props.max, Math.max(props.min, startSize + (props.reverse ? -delta : delta))));
    emit('update:modelValue', size);
  };
  const up = (): void => {
    window.removeEventListener('pointermove', move);
    window.removeEventListener('pointerup', up);
    document.body.style.cursor = '';
    emit('commit', size);
  };
  document.body.style.cursor = props.vertical ? 'col-resize' : 'row-resize';
  window.addEventListener('pointermove', move);
  window.addEventListener('pointerup', up);
}
</script>

<template>
  <div class="splitter" :class="vertical ? 'v' : 'h'" role="separator" @pointerdown="start" />
</template>

<style scoped>
.splitter { flex: none; position: relative; background: var(--border); z-index: 5; }
.splitter.v { width: 1px; cursor: col-resize; }
.splitter.h { height: 1px; cursor: row-resize; }
.splitter::after { content: ''; position: absolute; }
.splitter.v::after { left: -3px; right: -3px; top: 0; bottom: 0; }
.splitter.h::after { top: -3px; bottom: -3px; left: 0; right: 0; }
.splitter:hover { background: var(--accent); }
</style>
