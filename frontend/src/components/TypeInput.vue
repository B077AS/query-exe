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

function show(): void {
  const r = input.value!.getBoundingClientRect();
  pos.value = { left: r.left, top: r.bottom + 2, width: Math.max(r.width, 160) };
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
    <div v-if="open && matches.length" class="menu suggest" :style="{ left: pos.left + 'px', top: pos.top + 'px', width: pos.width + 'px' }">
      <div v-for="(m, i) in matches" :key="m" class="menu-item" :data-highlighted="i === index ? '' : undefined" @mousedown.prevent="pick(m)">{{ m }}</div>
    </div>
  </Teleport>
</template>

<style scoped>
.suggest { position: fixed; max-height: 240px; z-index: 300; font-family: var(--mono); font-size: 12px; }
</style>
