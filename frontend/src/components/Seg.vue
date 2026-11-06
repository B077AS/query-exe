<script setup lang="ts" generic="T extends string">
import Icon from './Icon.vue';

// A switch between a few views. The selected option is bold; each label reserves its bold width (see .seg-lbl in components.css),
// so switching never moves the neighbours.
defineProps<{ modelValue: T; options: { value: T; label: string; icon?: string; count?: number }[] }>();
const emit = defineEmits<{ 'update:modelValue': [value: T] }>();
</script>

<template>
  <div class="seg">
    <button v-for="o in options" :key="o.value" :class="{ on: o.value === modelValue }" @click="emit('update:modelValue', o.value)">
      <Icon v-if="o.icon" :path="o.icon" />
      <span class="seg-lbl" :data-t="o.label">{{ o.label }}</span>
      <span v-if="o.count !== undefined" class="badge">{{ o.count }}</span>
    </button>
  </div>
</template>
