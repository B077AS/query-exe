<script setup lang="ts">
import { mdiContentCopy } from '@mdi/js';
import { defaultKeymap, history, historyKeymap, indentWithTab } from '@codemirror/commands';
import { json } from '@codemirror/lang-json';
import { xml } from '@codemirror/lang-xml';
import { bracketMatching, foldGutter, foldKeymap, indentOnInput, syntaxHighlighting } from '@codemirror/language';
import { closeSearchPanel, highlightSelectionMatches, search, searchKeymap, searchPanelOpen } from '@codemirror/search';
import { Compartment, EditorState, type Extension } from '@codemirror/state';
import { drawSelection, EditorView, keymap, lineNumbers } from '@codemirror/view';
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue';
import { api } from '../api';
import { compact, detectKind, isValid, pretty } from '../format';
import { highlight } from '../highlight';
import { applyPatch, dialogs } from '../store';
import { confirm, fail, toast } from '../ui';
import AppDialog from './AppDialog.vue';
import Icon from './Icon.vue';
import Seg from './Seg.vue';

// One cell's full value: JSON and XML are shown indented, and an editable cell can be changed here (line breaks are kept,
// which the grid's one-line editor cannot do). The change joins the grid's pending edits.
// A binary cell is read-only: its decoded content (text, a Java object, an image) next to a hex dump.
const state = dialogs.value!;
const original = state.text;
const binary = state.binary;
const image = binary?.image ?? null;
const hasContent = original !== null || image !== null;
// the type name of a binary column says nothing about what is in it
const kind = detectKind(original, binary ? '' : state.typeName);
// A value stored on one line goes back on one line, even when it was edited in the indented view.
const oneLine = !original?.includes('\n');
const windowsBreaks = !!original?.includes('\r\n');

type Mode = 'formatted' | 'raw';
// For a binary cell the two views are its content and its bytes ("raw").
const mode = ref<Mode>((binary ? hasContent : kind !== 'text') ? 'formatted' : 'raw');
const modes = [
  { value: 'formatted' as const, label: !binary ? 'Formatted' : image ? 'Image' : 'Content' },
  { value: 'raw' as const, label: binary ? 'Hex' : 'Raw' },
];
const switchable = binary ? hasContent : kind !== 'text';

/** The editor always breaks lines with \n. */
function render(value: string | null, as: Mode): string {
  if (binary && as === 'raw') return binary.hex;
  return (as === 'formatted' ? pretty(kind, value ?? '') : value ?? '').replace(/\r\n?/g, '\n');
}

/** The value as of the last switch of view, and the text it was shown as: while that is untouched the value stays exactly as it was. */
let base = original;
let shown = render(base, mode.value);
const doc = shallowRef(shown);

function valueOf(text: string): string | null {
  if (text === shown) return base;
  const value = mode.value === 'formatted' && oneLine ? compact(kind, text) : text;
  return windowsBreaks ? value.replace(/\n/g, '\r\n') : value;
}

const value = computed(() => valueOf(doc.value));
const dirty = computed(() => state.editable && value.value !== original && !(original === null && value.value === ''));
const invalid = computed(() => kind !== 'text' && !!doc.value.trim() && !isValid(kind, doc.value));
const busy = ref(false);

const host = ref<HTMLDivElement>();
let view: EditorView | null = null;
const language = new Compartment();

function setMode(next: Mode): void {
  if (!view || next === mode.value) return;
  base = valueOf(doc.value);
  mode.value = next;
  shown = render(base, next);
  view.dispatch({ changes: { from: 0, to: view.state.doc.length, insert: shown }, effects: language.reconfigure(languageFor(next)) });
}

/** A hex dump is neither JSON nor XML, whatever it is the dump of. */
function languageFor(as: Mode): Extension {
  if (binary && as === 'raw') return [];
  return kind === 'json' ? json() : kind === 'xml' ? xml() : [];
}

async function copy(): Promise<void> {
  try {
    await navigator.clipboard.writeText(doc.value);
    toast('info', 'Copied');
  } catch (e) {
    fail('Could not copy', e);
  }
}

async function save(): Promise<void> {
  if (!dirty.value || busy.value) return;
  busy.value = true;
  try {
    applyPatch(state.grid, await api.gridEdit(state.grid.id, state.rowId, state.column, value.value));
    dialogs.value = null;
  } catch (e) {
    fail('Could not change the value', e);
  } finally {
    busy.value = false;
  }
}

async function close(): Promise<void> {
  if (dirty.value && !(await confirm('Discard changes?', 'The value was edited but not saved to the grid.', 'Discard', true))) return;
  dialogs.value = null;
}

/** Escape closes the search panel first, not the whole dialog. */
function onEscape(event: KeyboardEvent): void {
  if (!view || !searchPanelOpen(view.state)) return;
  event.preventDefault();
  event.stopPropagation();
  closeSearchPanel(view);
  view.focus();
}

const theme = EditorView.theme({
  '&': { height: '100%', backgroundColor: 'var(--bg)', color: 'var(--text)', fontSize: '12.5px' },
  '&.cm-focused': { outline: 'none' },
  '.cm-scroller': { fontFamily: 'var(--mono)', lineHeight: '1.6', cursor: 'text' },
  '.cm-content': { caretColor: 'var(--accent)', padding: '8px 0' },
  '.cm-cursor, .cm-dropCursor': { borderLeftColor: 'var(--accent)', borderLeftWidth: '2px' },
  '.cm-gutters': { backgroundColor: 'var(--bg-side)', color: 'var(--text-3)', border: 'none', borderRight: '1px solid var(--border)' },
  '&.cm-focused > .cm-scroller > .cm-selectionLayer .cm-selectionBackground, .cm-selectionBackground': { backgroundColor: 'color-mix(in srgb, var(--accent) 32%, transparent)' },
  '.cm-selectionMatch': { backgroundColor: 'color-mix(in srgb, var(--accent) 18%, transparent)' },
  '.cm-matchingBracket': { backgroundColor: 'var(--accent-tint-strong)', outline: '1px solid var(--accent)' },
  '.cm-foldPlaceholder': { background: 'var(--bg-hover)', border: '1px solid var(--border-strong)', color: 'var(--text-2)' },
  '.cm-panels': { backgroundColor: 'var(--bg-side)', color: 'var(--text)', borderColor: 'var(--border)' },
  '.cm-panel input, .cm-panel button': { font: 'inherit' },
  '.cm-panel.cm-search input': { background: 'var(--bg)', border: '1px solid var(--border-strong)', borderRadius: 'var(--radius)', color: 'var(--text)' },
  '.cm-panel.cm-search button': { background: 'var(--bg-raised)', border: '1px solid var(--border-strong)', borderRadius: 'var(--radius)', color: 'var(--text)', backgroundImage: 'none' },
  '.cm-searchMatch': { backgroundColor: 'color-mix(in srgb, var(--warn) 30%, transparent)' },
  '.cm-searchMatch.cm-searchMatch-selected': { backgroundColor: 'color-mix(in srgb, var(--warn) 55%, transparent)' },
});

// Not onMounted: the dialog puts its content in the page one step later, so the element does not exist yet at that point.
watch(host, (element) => {
  if (!element || view) return;
  view = new EditorView({
    parent: element,
    state: EditorState.create({
      doc: shown,
      extensions: [
        lineNumbers(), foldGutter(), history(), drawSelection(), indentOnInput(), bracketMatching(), highlightSelectionMatches(),
        search({ top: true }), syntaxHighlighting(highlight), theme, EditorView.lineWrapping,
        language.of(languageFor(mode.value)),
        EditorState.readOnly.of(!state.editable),
        keymap.of([
          { key: 'Mod-Enter', run: () => (void save(), true) },
          ...searchKeymap, ...foldKeymap, ...historyKeymap, ...defaultKeymap, indentWithTab,
        ]),
        EditorView.updateListener.of((update) => {
          if (update.docChanged) doc.value = update.state.doc.toString();
        }),
      ],
    }),
  });
  // the dialog focuses its first button as it opens; the value is what the keys are for
  setTimeout(() => view?.focus(), 0);
});

onBeforeUnmount(() => view?.destroy());
</script>

<template>
  <AppDialog :title="state.title" :width="900" @close="close">
    <template #head>
      <span class="pill">{{ state.typeName.toLowerCase() }}</span>
      <span v-if="!binary && original === null && !dirty" class="hint">NULL</span>
      <span v-if="binary" class="hint">{{ binary.format }} · {{ binary.size.toLocaleString() }} bytes</span>
    </template>
    <div class="tools">
      <Seg v-if="switchable" :model-value="mode" :options="modes" @update:model-value="setMode" />
      <span v-if="binary?.note" class="hint">{{ binary.note }}</span>
      <span class="sp" />
      <span v-if="invalid" class="warn">Not valid {{ kind.toUpperCase() }}</span>
      <span v-if="!state.editable" class="hint">Read-only</span>
    </div>
    <div v-if="image && mode === 'formatted'" class="value-image"><img :src="image" :alt="state.title" /></div>
    <div v-show="!image || mode === 'raw'" ref="host" class="value-editor selectable" @keydown.esc.capture="onEscape" />
    <template #footer>
      <button v-if="!image || mode === 'raw'" class="btn" @click="copy"><Icon :path="mdiContentCopy" /> Copy</button>
      <span class="sp" />
      <button class="btn" :class="{ primary: !state.editable }" @click="close">{{ state.editable ? 'Cancel' : 'Close' }}</button>
      <button v-if="state.editable" class="btn primary" :disabled="!dirty || busy" title="Ctrl Enter" @click="save">Update cell</button>
    </template>
  </AppDialog>
</template>

<style scoped>
.tools { display: flex; align-items: center; gap: 10px; min-height: 26px; }
.warn { color: var(--warn); font-size: 12px; }
.value-editor { height: 60vh; min-height: 0; flex: 0 1 auto; overflow: hidden; border: 1px solid var(--border); border-radius: var(--radius); user-select: text; }
.value-editor :deep(.cm-editor) { height: 100%; }
.value-image { height: 60vh; min-height: 0; flex: 0 1 auto; overflow: auto; display: flex; border: 1px solid var(--border); border-radius: var(--radius); background: var(--bg); }
.value-image img { margin: auto; max-width: 100%; max-height: 100%; }
</style>
