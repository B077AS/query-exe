<script setup lang="ts">
// The app icon (docs/icon/queryexe.svg), painted with the theme: the tile is the accent. The icon file itself keeps the fixed brand
// blue and never changes colour.
withDefaults(defineProps<{ size?: number }>(), { size: 24 });
</script>

<template>
  <svg class="logo" :width="size" :height="size" viewBox="0 0 16 16" aria-hidden="true">
    <rect width="16" height="16" rx="3" fill="var(--accent)" />
    <rect x="3" y="3" width="10" height="2.6" rx="1" fill="var(--on-accent)" />
    <rect x="3" y="6.7" width="10" height="2.6" rx="1" fill="var(--on-accent)" fill-opacity=".72" />
    <rect x="3" y="10.4" width="6" height="2.6" rx="1" fill="var(--on-accent)" fill-opacity=".45" />
    <rect x="10.5" y="10.4" width="2.5" height="2.6" rx=".6" fill="var(--on-accent)" />
  </svg>
</template>

<style scoped>
.logo { display: block; flex: none; }
</style>
