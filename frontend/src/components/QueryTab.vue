<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { api } from '../api';
import { addFileTab, app, infoOf, runTab, type QueryTab, type RunRequest } from '../store';
import { fail, prefs, savePref, toast } from '../ui';
import {
  mdiContentCopy, mdiContentCut, mdiContentPaste, mdiFormatAlignLeft, mdiMagnify, mdiPlay, mdiPlayOutline, mdiSelectAll, mdiCommentOutline,
} from '@mdi/js';
import { ContextMenuContent, ContextMenuItem, ContextMenuPortal, ContextMenuRoot, ContextMenuSeparator, ContextMenuTrigger } from 'reka-ui';
import Icon from './Icon.vue';
import FindBar from './FindBar.vue';
import ResultPanel from './ResultPanel.vue';
import Splitter from './Splitter.vue';
import SqlEditor from './SqlEditor.vue';

const props = defineProps<{ tab: QueryTab }>();

const editor = ref<InstanceType<typeof SqlEditor>>();
const resultHeight = ref(prefs.resultHeight);
const root = ref<HTMLDivElement>();
const maxHeight = ref(800);

const info = computed(() => infoOf(props.tab.connectionId));
const session = computed(() => app.sessions[props.tab.connectionId]);
/** The results area only exists once there is something to show in it. */
const hasResults = computed(() => props.tab.items.length > 0);

const schema = computed<Record<string, string[]>>(() => {
  const out: Record<string, string[]> = {};
  const tree = session.value?.tree;
  if (!tree) return out;
  for (const container of tree.containers) {
    for (const table of container.tables) {
      const columns = table.columns.map((c) => c.name);
      out[`${container.name}.${table.name}`] = columns;
      if (container.name === session.value?.current) out[table.name] = columns;
    }
  }
  return out;
});

onMounted(() => {
  maxHeight.value = Math.max(200, (root.value?.clientHeight ?? 800) - 160);
  void nextTick(() => editor.value?.focus());
});
watch(() => app.activeTab, (id) => id === props.tab.id && void nextTick(() => editor.value?.focus()));

function run(request: RunRequest): void {
  void runTab(props.tab, request);
}

function runAll(): void {
  if (editor.value) run(editor.value.request('auto'));
}

function runStatement(): void {
  if (editor.value) run(editor.value.request('statement'));
}

async function save(saveAs = false): Promise<void> {
  try {
    const result = await api.saveFile({ path: props.tab.path ?? undefined, text: props.tab.text, name: props.tab.title, saveAs, connectionId: props.tab.connectionId });
    if (result.cancelled || !result.path) return;
    props.tab.path = result.path;
    props.tab.title = result.name ?? props.tab.title;
    props.tab.savedText = props.tab.text;
    toast('ok', 'File saved', result.path);
  } catch (e) {
    fail('Could not save the file', e);
  }
}

async function openFile(): Promise<void> {
  try {
    const opened = await api.openFile(props.tab.connectionId);
    if (opened.cancelled || !opened.path) return;
    addFileTab(props.tab.connectionId, opened.path, opened.name ?? 'Script', opened.text ?? '');
  } catch (e) {
    fail('Could not open the file', e);
  }
}

async function format(): Promise<void> {
  if (!editor.value) return;
  const { text, from, to } = editor.value.selectedOrAll();
  if (text.trim() === '') return;
  try {
    const result = await api.formatSql(props.tab.connectionId, text);
    editor.value.replaceRange(from, to, result.sql);
  } catch (e) {
    fail('Could not format the SQL', e);
  }
}

// ---- the editor's right-click menu ----------------------------------------------------------------------------------

const hasSelection = ref(false);

// ---- find and replace ---------------------------------------------------------------------------------------------

const findOpen = ref(false);
const findBar = ref<InstanceType<typeof FindBar>>();

function openFind(): void {
  if (findOpen.value) findBar.value?.focus();
  else findOpen.value = true;
}

function closeFind(): void {
  findOpen.value = false;
  editor.value?.focus();
}

async function copySelection(cut = false): Promise<void> {
  const text = editor.value?.selectedText() ?? '';
  if (text === '') return;
  try {
    await navigator.clipboard.writeText(text);
    if (cut) editor.value?.deleteSelection();
  } catch (e) {
    fail('Could not copy', e);
  }
  editor.value?.focus();
}

async function paste(): Promise<void> {
  try {
    editor.value?.insert(await api.readClipboardText());
  } catch (e) {
    fail('Could not paste', e);
  }
}

function gotoError(from: number, to: number): void {
  editor.value?.select(from, to);
  editor.value?.focus();
}

defineExpose({ save, openFile, format, runAll, runStatement });
</script>

<template>
  <div ref="root" class="qtab">
    <ContextMenuRoot @update:open="(open: boolean) => open && (hasSelection = editor?.hasSelection() ?? false)">
      <ContextMenuTrigger as-child>
        <div class="editor">
          <SqlEditor ref="editor" v-model="tab.text" :engine="info?.type ?? ''" :schema="schema" :font-size="prefs.editorFontSize" :word-wrap="prefs.wordWrap"
            :error-range="tab.errorRange" @run="run" @save="save()" @format="format" @find="openFind" />
          <FindBar v-if="findOpen" ref="findBar" :editor="editor" :text="tab.text" @close="closeFind" />
        </div>
      </ContextMenuTrigger>
      <ContextMenuPortal>
        <ContextMenuContent class="menu" @close-auto-focus.prevent="editor?.focus()">
          <ContextMenuItem class="menu-item" :disabled="tab.running" @select="runAll"><Icon :path="mdiPlay" /> {{ hasSelection ? 'Run selection' : 'Run' }} <span class="shortcut">Ctrl Enter</span></ContextMenuItem>
          <ContextMenuItem class="menu-item" :disabled="tab.running" @select="runStatement"><Icon :path="mdiPlayOutline" /> Run statement <span class="shortcut">Ctrl Shift Enter</span></ContextMenuItem>
          <ContextMenuSeparator class="menu-sep" />
          <ContextMenuItem class="menu-item" :disabled="!hasSelection" @select="copySelection(true)"><Icon :path="mdiContentCut" /> Cut <span class="shortcut">Ctrl X</span></ContextMenuItem>
          <ContextMenuItem class="menu-item" :disabled="!hasSelection" @select="copySelection()"><Icon :path="mdiContentCopy" /> Copy <span class="shortcut">Ctrl C</span></ContextMenuItem>
          <ContextMenuItem class="menu-item" @select="paste"><Icon :path="mdiContentPaste" /> Paste <span class="shortcut">Ctrl V</span></ContextMenuItem>
          <ContextMenuItem class="menu-item" @select="editor?.selectEverything()"><Icon :path="mdiSelectAll" /> Select all <span class="shortcut">Ctrl A</span></ContextMenuItem>
          <ContextMenuSeparator class="menu-sep" />
          <ContextMenuItem class="menu-item" @select="format"><Icon :path="mdiFormatAlignLeft" /> Format SQL <span class="shortcut">Ctrl Shift F</span></ContextMenuItem>
          <ContextMenuItem class="menu-item" @select="editor?.comment()"><Icon :path="mdiCommentOutline" /> Comment / uncomment <span class="shortcut">Ctrl /</span></ContextMenuItem>
          <ContextMenuItem class="menu-item" @select="openFind"><Icon :path="mdiMagnify" /> Find <span class="shortcut">Ctrl F</span></ContextMenuItem>
        </ContextMenuContent>
      </ContextMenuPortal>
    </ContextMenuRoot>
    <template v-if="hasResults">
      <Splitter v-model="resultHeight" reverse :min="120" :max="maxHeight" @commit="savePref('resultHeight', $event)" />
      <div class="results" :style="{ height: resultHeight + 'px' }">
        <ResultPanel :tab="tab" @goto-error="gotoError" />
      </div>
    </template>
  </div>
</template>

<style scoped>
.qtab { display: flex; flex-direction: column; height: 100%; min-height: 0; }
.editor { flex: 1; min-height: 80px; overflow: hidden; position: relative; }
.results { flex: none; min-height: 0; }
</style>
