<script setup lang="ts">
import { mdiArrowDown, mdiArrowUp, mdiChevronDown, mdiChevronRight, mdiClose, mdiFormatLetterCase, mdiFormatLetterMatches, mdiRegex } from '@mdi/js';
import { nextTick, onMounted, ref, watch } from 'vue';
import Icon from './Icon.vue';
import type SqlEditor from './SqlEditor.vue';

// The find and replace widget: a small panel attached to the top right of the editor (like the one in IDEs), not a separate window.
const props = defineProps<{ editor: InstanceType<typeof SqlEditor> | undefined; text: string }>();
const emit = defineEmits<{ close: [] }>();

const query = ref('');
const replacement = ref('');
const caseSensitive = ref(false);
const wholeWord = ref(false);
const regexp = ref(false);
const showReplace = ref(false);
const count = ref(0);
const current = ref(0);
const input = ref<HTMLInputElement>();

function options() {
  return { search: query.value, caseSensitive: caseSensitive.value, wholeWord: wholeWord.value, regexp: regexp.value, replace: replacement.value };
}

function refreshInfo(): void {
  const info = props.editor?.matchInfo() ?? { count: 0, current: 0 };
  count.value = info.count;
  current.value = info.current;
}

function apply(jump = true): void {
  props.editor?.setFind(options(), jump);
  refreshInfo();
}

onMounted(async () => {
  // start from the selected text when it is a single line
  const selected = props.editor?.selectedText() ?? '';
  if (selected !== '' && !selected.includes('\n')) query.value = selected;
  props.editor?.showFindHighlights(true);
  apply(false);
  await nextTick();
  input.value?.focus();
  input.value?.select();
});

watch([query, caseSensitive, wholeWord, regexp], () => apply(true));
watch(replacement, () => props.editor?.setFind(options(), false));
watch(() => props.text, () => nextTick(refreshInfo));

function step(back: boolean): void {
  if (back) props.editor?.previousMatch();
  else props.editor?.nextMatch();
  refreshInfo();
}

function close(): void {
  props.editor?.showFindHighlights(false);
  emit('close');
}

function onKey(event: KeyboardEvent): void {
  if (event.key === 'Enter') {
    event.preventDefault();
    step(event.shiftKey);
  } else if (event.key === 'Escape') {
    event.preventDefault();
    event.stopPropagation();
    close();
  } else if (event.key === 'F3') {
    event.preventDefault();
    step(event.shiftKey);
  }
}

function replaceOne(): void {
  props.editor?.replaceOne();
  refreshInfo();
}

function replaceAll(): void {
  props.editor?.replaceEverything();
  refreshInfo();
}

defineExpose({ focus: () => { input.value?.focus(); input.value?.select(); } });
</script>

<template>
  <div class="find" @keydown.esc.stop="close">
    <button class="toggle" :title="showReplace ? 'Hide replace' : 'Show replace'" @click="showReplace = !showReplace"><Icon :path="showReplace ? mdiChevronDown : mdiChevronRight" :size="18" /></button>
    <div class="rows">
      <div class="row">
        <div class="fbox">
          <input ref="input" v-model="query" placeholder="Find" spellcheck="false" @keydown="onKey" />
          <button class="opt" :class="{ on: caseSensitive }" title="Match case" @click="caseSensitive = !caseSensitive"><Icon :path="mdiFormatLetterCase" :size="18" /></button>
          <button class="opt" :class="{ on: wholeWord }" title="Whole word" @click="wholeWord = !wholeWord"><Icon :path="mdiFormatLetterMatches" :size="18" /></button>
          <button class="opt" :class="{ on: regexp }" title="Regular expression" @click="regexp = !regexp"><Icon :path="mdiRegex" :size="18" /></button>
        </div>
        <span class="count" :class="{ none: query && count === 0 }">{{ !query ? '' : count === 0 ? 'No results' : `${current || '–'} of ${count >= 10000 ? '10000+' : count}` }}</span>
        <button class="ib sm" title="Previous (Shift+Enter)" :disabled="count === 0" @click="step(true)"><Icon :path="mdiArrowUp" /></button>
        <button class="ib sm" title="Next (Enter)" :disabled="count === 0" @click="step(false)"><Icon :path="mdiArrowDown" /></button>
        <button class="ib sm" title="Close (Esc)" @click="close"><Icon :path="mdiClose" /></button>
      </div>
      <div v-if="showReplace" class="row">
        <div class="fbox">
          <input v-model="replacement" placeholder="Replace with" spellcheck="false" @keydown.enter.prevent="replaceOne" @keydown.esc.prevent.stop="close" />
        </div>
        <button class="btn sm" :disabled="count === 0" @click="replaceOne">Replace</button>
        <button class="btn sm" :disabled="count === 0" @click="replaceAll">Replace all</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.find { position: absolute; top: 8px; right: 22px; z-index: 6; display: flex; gap: 2px; align-items: flex-start; padding: 6px 8px 6px 4px; background: var(--bg-raised); border: 1px solid var(--border-strong); border-top: 0; box-shadow: var(--shadow); }
.toggle { width: 22px; height: 28px; display: grid; place-items: center; color: var(--text-2); flex: none; }
.toggle:hover { color: var(--text); background: var(--bg-hover); }
.rows { display: flex; flex-direction: column; gap: 6px; }
.row { display: flex; align-items: center; gap: 6px; }
.fbox { display: flex; align-items: center; width: 300px; height: 28px; border: 1px solid var(--border-strong); background: var(--bg); padding-right: 2px; }
.fbox:focus-within { border-color: var(--accent); }
.fbox input { flex: 1; min-width: 0; height: 100%; border: 0; outline: 0; background: none; padding: 0 8px; font: 12.5px var(--mono); color: var(--text); user-select: text; }
.opt { width: 24px; height: 24px; display: grid; place-items: center; color: var(--text-3); flex: none; }
.opt:hover { color: var(--text); background: var(--bg-hover); }
.opt.on { color: var(--accent); background: var(--accent-tint-strong); box-shadow: inset 0 0 0 1px var(--accent); }
.count { min-width: 64px; text-align: center; font-size: 12px; color: var(--text-2); white-space: nowrap; }
.count.none { color: var(--text-3); }
</style>
