<script setup lang="ts">
// Takes an SVG path from @mdi/js, e.g. <Icon :path="mdiDatabase" />. Only the icons that are imported end up in the bundle.
withDefaults(defineProps<{ path: string; size?: number }>(), { size: 16 });
</script>

<template>
  <svg class="icon" :width="size" :height="size" viewBox="0 0 24 24" aria-hidden="true">
    <path :d="path" fill="currentColor" />
  </svg>
</template>
