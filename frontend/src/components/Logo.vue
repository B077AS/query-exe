<script setup lang="ts">
// The app icon (docs/icon/queryexe.svg), the same artwork: a fixed slate tile with a white outline that does not follow the theme.
// `bare` draws only the outline, with no tile, in the theme accent (for places where the logo sits straight on the page).
withDefaults(defineProps<{ size?: number; bare?: boolean }>(), { size: 24, bare: false });
</script>

<template>
  <!-- the bare view is cropped to the glyph (it sits at 6..30 of the 36 box, plus half the stroke) -->
  <svg class="logo" :width="size" :height="size" :viewBox="bare ? '5 5 26 26' : '0 0 36 36'" shape-rendering="geometricPrecision" aria-hidden="true">
    <rect v-if="!bare" width="36" height="36" rx="4.8" fill="#4a5565" />
    <g transform="translate(6 6)" fill="none" :stroke="bare ? undefined : '#fff'" :class="{ accent: bare }" :stroke-width="bare ? 1.7 : 2.3" stroke-linecap="round" stroke-linejoin="round">
      <ellipse cx="12" cy="5" rx="9" ry="3" />
      <path d="M3 5V19A9 3 0 0 0 15 21.84" />
      <path d="M21 5V8" />
      <path d="M21 12L18 17H22L19 22" />
      <path d="M3 12A9 3 0 0 0 14.59 14.87" />
    </g>
  </svg>
</template>

<style scoped>
.logo { display: block; flex: none; }
.accent { stroke: var(--accent); }
</style>
