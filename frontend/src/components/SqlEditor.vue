<script setup lang="ts">
import { autocompletion, closeBrackets, closeBracketsKeymap, completionKeymap } from '@codemirror/autocomplete';
import { defaultKeymap, history, historyKeymap, indentWithTab, selectAll as cmSelectAll, toggleComment } from '@codemirror/commands';
import { MariaSQL, MSSQL, MySQL, PostgreSQL, SQLite, StandardSQL, sql } from '@codemirror/lang-sql';
import { bracketMatching, HighlightStyle, indentOnInput, syntaxHighlighting } from '@codemirror/language';
import {
  closeSearchPanel, findNext, findPrevious, getSearchQuery, highlightSelectionMatches, openSearchPanel, replaceAll, replaceNext, search, SearchQuery, setSearchQuery,
} from '@codemirror/search';
import { Compartment, EditorState, StateEffect, StateField } from '@codemirror/state';
import { Decoration, drawSelection, EditorView, keymap, lineNumbers, type DecorationSet } from '@codemirror/view';
import { tags as t } from '@lezer/highlight';
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
import type { RunRequest } from '../store';

const props = defineProps<{
  modelValue: string;
  engine: string;
  schema: Record<string, string[]>;
  fontSize: number;
  wordWrap: boolean;
  errorRange: { from: number; to: number } | null;
}>();
const emit = defineEmits<{
  'update:modelValue': [value: string];
  run: [request: RunRequest];
  save: [];
  format: [];
  find: [];
}>();

const host = ref<HTMLDivElement>();
let view: EditorView | null = null;
const language = new Compartment();
const wrapping = new Compartment();
const sizing = new Compartment();

const dialects = { PostgreSQL, MySQL, MariaDB: MariaSQL, SQLServer: MSSQL, SQLite, H2: StandardSQL } as const;

function languageFor(): ReturnType<typeof sql> {
  const dialect = dialects[props.engine as keyof typeof dialects] ?? StandardSQL;
  return sql({ dialect, schema: props.schema, upperCaseKeywords: true });
}

const setError = StateEffect.define<{ from: number; to: number } | null>();
const errorField = StateField.define<DecorationSet>({
  create: () => Decoration.none,
  update(value, tr) {
    value = value.map(tr.changes);
    for (const effect of tr.effects) {
      if (effect.is(setError)) {
        const range = effect.value;
        const max = tr.state.doc.length;
        value = range && range.to > range.from
          ? Decoration.set([Decoration.mark({ class: 'cm-error-range' }).range(Math.min(range.from, max), Math.min(range.to, max))])
          : Decoration.none;
      }
    }
    return value;
  },
  provide: (f) => EditorView.decorations.from(f),
});

const highlight = HighlightStyle.define([
  { tag: [t.keyword, t.operatorKeyword, t.modifier], color: 'var(--syn-keyword)', fontWeight: '600' },
  { tag: [t.string, t.special(t.string)], color: 'var(--syn-string)' },
  { tag: [t.number, t.bool, t.null], color: 'var(--syn-number)' },
  { tag: [t.comment, t.lineComment, t.blockComment], color: 'var(--syn-comment)', fontStyle: 'italic' },
  { tag: [t.function(t.variableName), t.standard(t.name)], color: 'var(--syn-function)' },
  { tag: [t.typeName, t.className], color: 'var(--syn-type)' },
  { tag: [t.operator, t.punctuation], color: 'var(--text-2)' },
]);

const theme = EditorView.theme({
  '&': { height: '100%', backgroundColor: 'var(--bg)', color: 'var(--text)' },
  '&.cm-focused': { outline: 'none' },
  '.cm-scroller': { fontFamily: 'var(--mono)', lineHeight: '1.65', cursor: 'text' },
  '.cm-content': { caretColor: 'var(--accent)', padding: '8px 0' },
  '.cm-cursor, .cm-dropCursor': { borderLeftColor: 'var(--accent)', borderLeftWidth: '2px' },
  '.cm-gutters': { backgroundColor: 'var(--bg-side)', color: 'var(--text-3)', border: 'none', borderRight: '1px solid var(--border)' },
  '&.cm-focused > .cm-scroller > .cm-selectionLayer .cm-selectionBackground, .cm-selectionBackground': { backgroundColor: 'color-mix(in srgb, var(--accent) 32%, transparent)' },
  '.cm-selectionMatch': { backgroundColor: 'color-mix(in srgb, var(--accent) 18%, transparent)' },
  '.cm-matchingBracket': { backgroundColor: 'var(--accent-tint-strong)', outline: '1px solid var(--accent)' },
  '.cm-placeholder': { color: 'var(--text-3)' },
  '.cm-error-range': { textDecoration: 'underline wavy var(--danger)', textUnderlineOffset: '3px' },
  '.cm-tooltip': { backgroundColor: 'var(--bg-raised)', border: '1px solid var(--border-strong)', borderRadius: 'var(--radius-lg)', boxShadow: 'var(--shadow)', color: 'var(--text)' },
  '.cm-tooltip.cm-tooltip-autocomplete > ul': { fontFamily: 'var(--mono)', fontSize: '12px' },
  '.cm-tooltip-autocomplete ul li[aria-selected]': { backgroundColor: 'var(--accent-tint-strong)', color: 'var(--text)' },
  '.cm-completionDetail': { color: 'var(--text-3)' },
  '.cm-panels': { display: 'none' },
  '.cm-panels-unused': { backgroundColor: 'var(--bg-side)', color: 'var(--text)', borderColor: 'var(--border)' },
  '.cm-panel input, .cm-panel button': { font: 'inherit' },
  '.cm-panel.cm-search input': { background: 'var(--bg)', border: '1px solid var(--border-strong)', borderRadius: 'var(--radius)', color: 'var(--text)' },
  '.cm-panel.cm-search button': { background: 'var(--bg-raised)', border: '1px solid var(--border-strong)', borderRadius: 'var(--radius)', color: 'var(--text)', backgroundImage: 'none' },
  '.cm-searchMatch': { backgroundColor: 'color-mix(in srgb, var(--warn) 30%, transparent)' },
  '.cm-searchMatch.cm-searchMatch-selected': { backgroundColor: 'color-mix(in srgb, var(--warn) 55%, transparent)' },
});

/** What a Run should execute: the selection if there is one, else everything; or the statement at the caret. */
function request(mode: 'auto' | 'statement'): RunRequest {
  const state = view!.state;
  const text = state.doc.toString();
  const sel = state.selection.main;
  if (mode === 'statement') return { mode: 'statement', text, caret: sel.head };
  if (!sel.empty) return { mode: 'selection', text: state.sliceDoc(sel.from, sel.to), offset: sel.from };
  return { mode: 'all', text };
}

function focus(): void {
  view?.focus();
}

function select(from: number, to: number): void {
  if (!view) return;
  view.dispatch({ selection: { anchor: from, head: to }, scrollIntoView: true });
}

function insert(text: string): void {
  if (!view) return;
  view.dispatch(view.state.replaceSelection(text));
  view.focus();
}

function setText(text: string): void {
  if (!view) return;
  view.dispatch({ changes: { from: 0, to: view.state.doc.length, insert: text } });
}

function selectedOrAll(): { text: string; from: number; to: number } {
  const state = view!.state;
  const sel = state.selection.main;
  return sel.empty ? { text: state.doc.toString(), from: 0, to: state.doc.length } : { text: state.sliceDoc(sel.from, sel.to), from: sel.from, to: sel.to };
}

function replaceRange(from: number, to: number, text: string): void {
  view?.dispatch({ changes: { from, to, insert: text } });
}

function hasSelection(): boolean {
  return !!view && !view.state.selection.main.empty;
}

/** The selected text, for Copy and Cut. */
function selectedText(): string {
  if (!view) return '';
  const sel = view.state.selection.main;
  return view.state.sliceDoc(sel.from, sel.to);
}

function deleteSelection(): void {
  if (view && hasSelection()) view.dispatch(view.state.replaceSelection(''));
}

function selectEverything(): void {
  if (!view) return;
  cmSelectAll(view);
  view.focus();
}

// ---- search (the widget is FindBar.vue; CodeMirror's own panel is replaced by an empty one so match highlighting still works) ----

export interface FindOptions {
  search: string;
  caseSensitive: boolean;
  wholeWord: boolean;
  regexp: boolean;
  replace: string;
}

function find(): void {
  emit('find');
}

/** Turns match highlighting on (while the find widget is open) or off. */
function showFindHighlights(on: boolean): void {
  if (!view) return;
  if (on) openSearchPanel(view);
  else closeSearchPanel(view);
}

/** Sets what is searched for; with `jump` the nearest match at or after the cursor is selected. */
function setFind(options: FindOptions, jump = true): void {
  if (!view) return;
  const query = new SearchQuery(options);
  view.dispatch({ effects: setSearchQuery.of(query) });
  if (jump && query.valid) {
    const from = view.state.selection.main.from;
    view.dispatch({ selection: { anchor: from } });
    findNext(view);
  }
}

function nextMatch(): void {
  if (view && getSearchQuery(view.state).valid) findNext(view);
}

function previousMatch(): void {
  if (view && getSearchQuery(view.state).valid) findPrevious(view);
}

function replaceOne(): void {
  if (view && getSearchQuery(view.state).valid) replaceNext(view);
}

function replaceEverything(): void {
  if (view && getSearchQuery(view.state).valid) replaceAll(view);
}

/** How many matches there are (counted up to 10,000) and which one is selected (1-based, 0 when none). */
function matchInfo(): { count: number; current: number } {
  if (!view) return { count: 0, current: 0 };
  const query = getSearchQuery(view.state);
  if (!query.valid) return { count: 0, current: 0 };
  const sel = view.state.selection.main;
  const cursor = query.getCursor(view.state);
  let count = 0;
  let current = 0;
  for (let r = cursor.next(); !r.done; r = cursor.next()) {
    count++;
    if (r.value.from === sel.from && r.value.to === sel.to) current = count;
    if (count >= 10_000) break;
  }
  return { count, current };
}

function comment(): void {
  if (!view) return;
  toggleComment(view);
  view.focus();
}

defineExpose({ request, focus, select, insert, setText, selectedOrAll, replaceRange, hasSelection, selectedText, deleteSelection, selectEverything, find, comment, showFindHighlights, setFind, nextMatch, previousMatch, replaceOne, replaceEverything, matchInfo });

onMounted(() => {
  view = new EditorView({
    parent: host.value!,
    state: EditorState.create({
      doc: props.modelValue,
      extensions: [
        lineNumbers(), history(), drawSelection(), indentOnInput(), bracketMatching(), closeBrackets(),
        highlightSelectionMatches(), autocompletion({ activateOnTyping: true }),
        search({ top: true, createPanel: () => ({ dom: Object.assign(document.createElement('div'), { hidden: true }) }) }),
        syntaxHighlighting(highlight), theme, errorField,
        language.of(languageFor()),
        wrapping.of(props.wordWrap ? EditorView.lineWrapping : []),
        sizing.of(EditorView.theme({ '.cm-scroller': { fontSize: props.fontSize + 'px' } })),
        keymap.of([
          { key: 'Mod-Enter', run: () => (emit('run', request('auto')), true) },
          { key: 'Mod-Shift-Enter', run: () => (emit('run', request('statement')), true) },
          { key: 'Mod-s', run: () => (emit('save'), true) },
          { key: 'Mod-Shift-f', run: () => (emit('format'), true) },
          { key: 'Mod-/', run: toggleComment },
          { key: 'Mod-f', run: () => (emit('find'), true) },
          { key: 'F3', run: findNext, shift: findPrevious },
          { key: 'Mod-g', run: findNext, shift: findPrevious },
          ...closeBracketsKeymap, ...completionKeymap, ...historyKeymap, ...defaultKeymap, indentWithTab,
        ]),
        EditorView.updateListener.of((update) => {
          if (update.docChanged) {
            // any edit clears the error mark: its offsets no longer mean the same text
            emit('update:modelValue', update.state.doc.toString());
          }
        }),
      ],
    }),
  });
});

onBeforeUnmount(() => view?.destroy());

watch(() => props.modelValue, (value) => {
  if (view && value !== view.state.doc.toString()) setText(value);
});
watch(() => [props.engine, props.schema], () => view?.dispatch({ effects: language.reconfigure(languageFor()) }), { deep: false });
watch(() => props.wordWrap, (on) => view?.dispatch({ effects: wrapping.reconfigure(on ? EditorView.lineWrapping : []) }));
watch(() => props.fontSize, (size) => view?.dispatch({ effects: sizing.reconfigure(EditorView.theme({ '.cm-scroller': { fontSize: size + 'px' } })) }));
watch(() => props.errorRange, (range) => view?.dispatch({ effects: setError.of(range) }));
</script>

<template>
  <div ref="host" class="sql-editor selectable" />
</template>

<style scoped>
.sql-editor { height: 100%; min-height: 0; overflow: hidden; user-select: text; }
.sql-editor :deep(.cm-editor) { height: 100%; }
</style>
