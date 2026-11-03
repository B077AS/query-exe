<script setup lang="ts">
import { computed, ref } from 'vue';

// A text box that suggests the engine's data types while still allowing anything (VARCHAR(50), NUMERIC(10,2), ...).
const props = defineProps<{ modelValue: string; options: string[] }>();
const emit = defineEmits<{ 'update:modelValue': [value: string] }>();

const input = ref<HTMLInputElement>();
const open = ref(false);
const pos = ref({ left: 0, top: 0, width: 0 });
const index = ref(0);

const matches = computed(() => {
  const q = props.modelValue.trim().toLowerCase().replace(/\(.*$/, '');
  const list = q === '' ? props.options : props.options.filter((o) => o.toLowerCase().includes(q));
  return list.slice(0, 40);
});

// The list is as wide as the longest type the engine offers (measured with the menu's own font), never narrower than the box.
let canvas: CanvasRenderingContext2D | null = null;
function widest(): number {
  canvas ??= document.createElement('canvas').getContext('2d');
  if (!canvas) return 0;
  canvas.font = `12px ${getComputedStyle(input.value!).fontFamily}`;
  return Math.max(0, ...props.options.map((o) => canvas!.measureText(o).width));
}

function show(): void {
  const r = input.value!.getBoundingClientRect();
  const wanted = Math.ceil(widest()) + 24 /* menu + item padding */ + 14 /* scrollbar */;
  pos.value = { left: r.left, top: r.bottom + 2, width: Math.min(Math.max(r.width, wanted), window.innerWidth - r.left - 8) };
  index.value = 0;
  open.value = true;
}

function pick(value: string): void {
  emit('update:modelValue', value);
  open.value = false;
}

function key(e: KeyboardEvent): void {
  if (!open.value) return;
  if (e.key === 'ArrowDown') { index.value = Math.min(index.value + 1, matches.value.length - 1); e.preventDefault(); }
  else if (e.key === 'ArrowUp') { index.value = Math.max(index.value - 1, 0); e.preventDefault(); }
  else if (e.key === 'Enter' && matches.value[index.value]) { pick(matches.value[index.value]); e.preventDefault(); }
  else if (e.key === 'Escape') { open.value = false; e.stopPropagation(); }
}
</script>

<template>
  <input ref="input" class="input mono" :value="modelValue" spellcheck="false" @input="emit('update:modelValue', ($event.target as HTMLInputElement).value); show()" @focus="show" @blur="open = false" @keydown="key" />
  <Teleport to="body">
    <!-- The menu lives in <body>, outside the dialog: a modal dialog turns body pointer events off and treats a press outside
         itself as a dismiss. So the menu re-enables pointer events, keeps the press and wheel to itself, and (mousedown.prevent)
         keeps the input focused so grabbing the scrollbar does not close the list. -->
    <div v-if="open && matches.length" class="menu suggest" :style="{ left: pos.left + 'px', top: pos.top + 'px', width: pos.width + 'px' }"
      @mousedown.prevent @pointerdown.stop @wheel.stop>
      <div v-for="(m, i) in matches" :key="m" class="menu-item" :data-highlighted="i === index ? '' : undefined" @mousedown.prevent="pick(m)">{{ m }}</div>
    </div>
  </Teleport>
</template>

<style scoped>
.suggest { position: fixed; max-height: 240px; z-index: 300; font-family: var(--mono); font-size: 12px; pointer-events: auto; overscroll-behavior: contain; }
</style>
