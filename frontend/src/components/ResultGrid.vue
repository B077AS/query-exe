<script setup lang="ts">
import {
  mdiCheck, mdiContentCopy, mdiContentDuplicate, mdiDeleteOutline, mdiExport, mdiEyeOutline, mdiKeyVariant, mdiPencilOutline, mdiPlus, mdiUndo, mdiCodeBraces,
  mdiChevronRight, mdiLock,
} from '@mdi/js';
import {
  ContextMenuContent, ContextMenuItem, ContextMenuPortal, ContextMenuRoot, ContextMenuSeparator, ContextMenuSub, ContextMenuSubContent, ContextMenuSubTrigger,
  ContextMenuTrigger, DropdownMenuContent, DropdownMenuItem, DropdownMenuPortal, DropdownMenuRoot, DropdownMenuTrigger,
} from 'reka-ui';
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { api, type GridSnapshot } from '../api';
import { applyPatch, dialogs, replaceGrid } from '../store';
import { errorText, fail, toast } from '../ui';
import Icon from './Icon.vue';

const props = defineProps<{ grid: GridSnapshot }>();

const ROW_HEIGHT = 26;
const HEADER_HEIGHT = 30;
const GUTTER = 48;
const OVERSCAN = 8;

const scroller = ref<HTMLDivElement>();
const scrollTop = ref(0);
const viewHeight = ref(400);
const widths = ref<number[]>([]);
const selected = reactive(new Set<number>()); // row ids
const anchor = ref<number | null>(null); // row index
const active = reactive({ row: -1, col: -1 });
const editing = ref<{ rowId: number; col: number; value: string; original: string | null } | null>(null);
const editInput = ref<HTMLInputElement[] | HTMLInputElement>();
const ctx = reactive({ row: -1, col: -1 });
const busy = ref(false);

const rows = computed(() => props.grid.rows);
const columns = computed(() => props.grid.columns);
const totalWidth = computed(() => GUTTER + widths.value.reduce((a, b) => a + b, 0));
const start = computed(() => Math.max(0, Math.floor(scrollTop.value / ROW_HEIGHT) - OVERSCAN));
const end = computed(() => Math.min(rows.value.length, Math.ceil((scrollTop.value + viewHeight.value) / ROW_HEIGHT) + OVERSCAN));
const visible = computed(() => rows.value.slice(start.value, end.value));
const pending = computed(() => props.grid.pending);
const hasPending = computed(() => pending.value.updates + pending.value.inserts + pending.value.deletes > 0);

function initialWidths(): number[] {
  return props.grid.columns.map((column, i) => {
    let longest = Math.max(column.label.length + column.typeName.length * 0.8 + (column.primaryKey ? 3 : 1), 6);
    for (const row of props.grid.rows.slice(0, 60)) {
      const value = row.c[i];
      longest = Math.max(longest, value === null ? 4 : Math.min(value.length, 60));
    }
    return Math.round(Math.min(Math.max(longest * 7.6 + 28, 90), 380));
  });
}

watch(() => props.grid.id, () => {
  widths.value = initialWidths();
  selected.clear();
  Object.assign(active, { row: -1, col: -1 });
  editing.value = null;
  if (scroller.value) scroller.value.scrollTop = 0;
}, { immediate: true });

let observer: ResizeObserver | null = null;
onMounted(() => {
  if (!scroller.value) return;
  viewHeight.value = scroller.value.clientHeight;
  observer = new ResizeObserver(() => (viewHeight.value = scroller.value?.clientHeight ?? 400));
  observer.observe(scroller.value);
});
onBeforeUnmount(() => observer?.disconnect());

function onScroll(): void {
  scrollTop.value = scroller.value?.scrollTop ?? 0;
}

// ---- column resizing ----------------------------------------------------------------------------------------------

function startResize(event: MouseEvent, index: number): void {
  event.preventDefault();
  event.stopPropagation();
  const startX = event.clientX;
  const startWidth = widths.value[index];
  const move = (e: MouseEvent): void => {
    widths.value[index] = Math.max(48, startWidth + e.clientX - startX);
  };
  const up = (): void => {
    window.removeEventListener('mousemove', move);
    window.removeEventListener('mouseup', up);
  };
  window.addEventListener('mousemove', move);
  window.addEventListener('mouseup', up);
}

function autoSize(index: number): void {
  let longest = columns.value[index].label.length + 2;
  for (const row of rows.value.slice(0, 500)) {
    const value = row.c[index];
    longest = Math.max(longest, value === null ? 4 : Math.min(value.length, 80));
  }
  widths.value[index] = Math.round(Math.min(Math.max(longest * 7.6 + 28, 60), 600));
}

// ---- selection ----------------------------------------------------------------------------------------------------

function selectRow(index: number, event: MouseEvent | KeyboardEvent): void {
  const row = rows.value[index];
  if (!row) return;
  if (event.shiftKey && anchor.value !== null) {
    selected.clear();
    const [a, b] = [Math.min(anchor.value, index), Math.max(anchor.value, index)];
    for (let i = a; i <= b; i++) selected.add(rows.value[i].id);
  } else if (event.ctrlKey || event.metaKey) {
    if (selected.has(row.id)) selected.delete(row.id);
    else selected.add(row.id);
    anchor.value = index;
  } else {
    selected.clear();
    selected.add(row.id);
    anchor.value = index;
  }
}

function clickCell(index: number, col: number, event: MouseEvent): void {
  if (editing.value) return;
  active.row = index;
  active.col = col;
  selectRow(index, event);
  scroller.value?.focus();
}

function clickGutter(index: number, event: MouseEvent): void {
  if (editing.value) void commitEdit();
  active.row = index;
  if (active.col < 0) active.col = 0;
  selectRow(index, event);
  scroller.value?.focus();
}

const selectedIds = (): number[] => rows.value.filter((r) => selected.has(r.id)).map((r) => r.id);

function ensureVisible(index: number, col: number): void {
  const el = scroller.value;
  if (!el) return;
  const top = index * ROW_HEIGHT;
  if (top < el.scrollTop) el.scrollTop = top;
  else if (top + ROW_HEIGHT > el.scrollTop + el.clientHeight - HEADER_HEIGHT) el.scrollTop = top + ROW_HEIGHT - el.clientHeight + HEADER_HEIGHT;
  const left = GUTTER + widths.value.slice(0, col).reduce((a, b) => a + b, 0);
  const right = left + (widths.value[col] ?? 0);
  if (left - GUTTER < el.scrollLeft) el.scrollLeft = Math.max(0, left - GUTTER);
  else if (right > el.scrollLeft + el.clientWidth) el.scrollLeft = right - el.clientWidth;
}

function move(dRow: number, dCol: number, event: KeyboardEvent): void {
  if (rows.value.length === 0) return;
  const row = Math.min(Math.max((active.row < 0 ? 0 : active.row) + dRow, 0), rows.value.length - 1);
  const col = Math.min(Math.max((active.col < 0 ? 0 : active.col) + dCol, 0), columns.value.length - 1);
  active.row = row;
  active.col = col;
  if (dRow !== 0) selectRow(row, event);
  ensureVisible(row, col);
}

// ---- editing ------------------------------------------------------------------------------------------------------

const canEdit = (col: number): boolean => props.grid.editable && !!columns.value[col]?.editable;

async function startEdit(index: number, col: number): Promise<void> {
  const row = rows.value[index];
  if (!row) return;
  if (!canEdit(col)) {
    if (!props.grid.editable) toast('info', 'Read-only result', props.grid.table ? 'This table has no primary key to identify rows by.' : 'Only a result from a single table with a primary key can be edited.');
    return;
  }
  let value = row.c[col];
  if (value !== null && value.length >= 2000) {
    try {
      value = (await api.gridCell(props.grid.id, row.id, col)).text;
    } catch (e) {
      fail('Could not read the value', e);
      return;
    }
  }
  active.row = index;
  active.col = col;
  editing.value = { rowId: row.id, col, value: value ?? '', original: value };
  await nextTick();
  const input = Array.isArray(editInput.value) ? editInput.value[0] : editInput.value;
  input?.focus();
  input?.select();
}

async function commitEdit(next?: 'down' | 'right'): Promise<void> {
  const e = editing.value;
  if (!e) return;
  editing.value = null;
  const changed = e.value !== (e.original ?? '') || (e.original === null && e.value !== '');
  scroller.value?.focus();
  if (changed) await sendEdit(e.rowId, e.col, e.value);
  if (next === 'down') move(1, 0, new KeyboardEvent('keydown'));
  if (next === 'right') move(0, 1, new KeyboardEvent('keydown'));
}

function cancelEdit(): void {
  editing.value = null;
  scroller.value?.focus();
}

async function sendEdit(rowId: number, col: number, value: string | null): Promise<void> {
  try {
    applyPatch(props.grid, await api.gridEdit(props.grid.id, rowId, col, value));
  } catch (e) {
    fail('Could not change the value', e);
  }
}

async function setNull(): Promise<void> {
  const row = rows.value[ctx.row];
  if (row && canEdit(ctx.col)) await sendEdit(row.id, ctx.col, null);
}

async function addRow(): Promise<void> {
  try {
    const patch = await api.gridAddRow(props.grid.id);
    applyPatch(props.grid, patch);
    const index = rows.value.findIndex((r) => r.id === patch.focusRow);
    selected.clear();
    selected.add(patch.focusRow);
    active.row = index;
    active.col = Math.max(0, columns.value.findIndex((c) => c.editable));
    await nextTick();
    ensureVisible(index, active.col);
  } catch (e) {
    fail('Could not add a row', e);
  }
}

async function duplicateRow(): Promise<void> {
  const row = rows.value[ctx.row >= 0 ? ctx.row : active.row];
  if (!row) return;
  try {
    const patch = await api.gridDuplicateRow(props.grid.id, row.id);
    applyPatch(props.grid, patch);
    selected.clear();
    selected.add(patch.focusRow);
  } catch (e) {
    fail('Could not duplicate the row', e);
  }
}

async function deleteSelected(): Promise<void> {
  const ids = selectedIds();
  if (ids.length === 0 || !props.grid.editable) return;
  try {
    applyPatch(props.grid, await api.gridDeleteRows(props.grid.id, ids));
    selected.clear();
    active.row = Math.min(active.row, rows.value.length - 1);
  } catch (e) {
    fail('Could not delete', e);
  }
}

async function revert(): Promise<void> {
  try {
    replaceGrid(props.grid, await api.gridRevert(props.grid.id));
    selected.clear();
  } catch (e) {
    fail('Could not revert', e);
  }
}

async function apply(): Promise<void> {
  busy.value = true;
  try {
    replaceGrid(props.grid, await api.gridApply(props.grid.id));
    selected.clear();
    toast('ok', 'Changes applied');
  } catch (e) {
    toast('error', 'Could not apply the changes — nothing was changed', errorText(e));
  } finally {
    busy.value = false;
  }
}

// ---- copy / export ------------------------------------------------------------------------------------------------

async function writeClipboard(text: string, what: string): Promise<void> {
  try {
    await navigator.clipboard.writeText(text);
    toast('info', `${what} copied`);
  } catch (e) {
    fail('Could not copy', e);
  }
}

async function copyCell(): Promise<void> {
  const row = rows.value[ctx.row >= 0 ? ctx.row : active.row];
  const col = ctx.col >= 0 ? ctx.col : active.col;
  if (!row || col < 0) return;
  let value = row.c[col];
  if (value !== null && value.length >= 2000) value = (await api.gridCell(props.grid.id, row.id, col)).text;
  await writeClipboard(value ?? '', 'Value');
}

async function copyRows(): Promise<void> {
  const ids = selectedIds();
  if (ids.length === 0) return;
  try {
    await writeClipboard((await api.gridCopy(props.grid.id, ids)).text, ids.length === 1 ? 'Row' : `${ids.length} rows`);
  } catch (e) {
    fail('Could not copy', e);
  }
}

async function copyInsert(): Promise<void> {
  const row = rows.value[ctx.row >= 0 ? ctx.row : active.row];
  if (!row) return;
  try {
    await writeClipboard((await api.gridRowScript(props.grid.id, row.id)).sql, 'INSERT statement');
  } catch (e) {
    fail('Could not script the row', e);
  }
}

async function viewValue(): Promise<void> {
  const row = rows.value[ctx.row];
  if (!row || ctx.col < 0) return;
  try {
    const text = (await api.gridCell(props.grid.id, row.id, ctx.col)).text;
    dialogs.script = { title: columns.value[ctx.col].label, sql: text ?? 'NULL' };
  } catch (e) {
    fail('Could not read the value', e);
  }
}

async function exportGrid(format: 'csv' | 'json' | 'xml', onlySelected: boolean): Promise<void> {
  try {
    const result = await api.gridExport(props.grid.id, format, onlySelected ? selectedIds() : undefined);
    if (result.path) toast('ok', 'Export complete', result.path);
  } catch (e) {
    fail('Export failed', e);
  }
}

// ---- keyboard -----------------------------------------------------------------------------------------------------

function onKey(event: KeyboardEvent): void {
  if (editing.value) return;
  const page = Math.max(1, Math.floor((viewHeight.value - HEADER_HEIGHT) / ROW_HEIGHT) - 1);
  switch (event.key) {
    case 'ArrowDown': move(1, 0, event); break;
    case 'ArrowUp': move(-1, 0, event); break;
    case 'ArrowLeft': move(0, -1, event); break;
    case 'ArrowRight': move(0, 1, event); break;
    case 'PageDown': move(page, 0, event); break;
    case 'PageUp': move(-page, 0, event); break;
    case 'Home': move(0, -active.col, event); break;
    case 'End': move(0, columns.value.length, event); break;
    case 'Enter':
    case 'F2':
      if (active.row >= 0) void startEdit(active.row, Math.max(active.col, 0));
      break;
    case 'Delete':
      if (props.grid.editable) void deleteSelected();
      break;
    case 'a':
      if (event.ctrlKey || event.metaKey) {
        selected.clear();
        rows.value.forEach((r) => selected.add(r.id));
        break;
      }
      return;
    case 'c':
      if (event.ctrlKey || event.metaKey) {
        if (selected.size > 1) void copyRows();
        else void copyCell();
        break;
      }
      return;
    default:
      return;
  }
  event.preventDefault();
}

function onEditKey(event: KeyboardEvent): void {
  if (event.key === 'Enter') {
    event.preventDefault();
    void commitEdit('down');
  } else if (event.key === 'Tab') {
    event.preventDefault();
    void commitEdit('right');
  } else if (event.key === 'Escape') {
    event.preventDefault();
    cancelEdit();
  }
}

function onContext(index: number, col: number): void {
  ctx.row = index;
  ctx.col = col;
  const row = rows.value[index];
  if (row && !selected.has(row.id)) {
    selected.clear();
    selected.add(row.id);
    anchor.value = index;
  }
  active.row = index;
  active.col = col;
}

const isChanged = (row: { m: number[] }, col: number): boolean => row.m.includes(col);
const countLabel = computed(() => `${rows.value.length.toLocaleString()} row${rows.value.length === 1 ? '' : 's'}${props.grid.truncated ? ' (limit reached)' : ''}`);
</script>

<template>
  <div class="grid-wrap">
    <div class="gtools">
      <template v-if="grid.editable">
        <button class="btn sm" @click="addRow"><Icon :path="mdiPlus" /> Add row</button>
        <button class="btn sm" :disabled="selected.size === 0" @click="deleteSelected"><Icon :path="mdiDeleteOutline" /> Delete{{ selected.size > 1 ? ` ${selected.size}` : '' }}</button>
      </template>
      <span v-else class="pill" :title="grid.table ? 'This table has no primary key.' : 'Not a single table: results can be read but not edited.'"><Icon :path="mdiLock" :size="12" /> Read-only</span>
      <span class="sp" />
      <span class="hint count">{{ countLabel }}<template v-if="grid.table"> · {{ grid.table }}</template> · {{ grid.millis }} ms</span>
      <DropdownMenuRoot>
        <DropdownMenuTrigger class="btn sm"><Icon :path="mdiExport" /> Export</DropdownMenuTrigger>
        <DropdownMenuPortal>
          <DropdownMenuContent class="menu" align="end" :side-offset="4">
            <div class="menu-label">All rows</div>
            <DropdownMenuItem class="menu-item" @select="exportGrid('csv', false)">CSV</DropdownMenuItem>
            <DropdownMenuItem class="menu-item" @select="exportGrid('json', false)">JSON</DropdownMenuItem>
            <DropdownMenuItem class="menu-item" @select="exportGrid('xml', false)">XML</DropdownMenuItem>
            <template v-if="selected.size > 0">
              <div class="menu-label">{{ selected.size }} selected</div>
              <DropdownMenuItem class="menu-item" @select="exportGrid('csv', true)">CSV</DropdownMenuItem>
              <DropdownMenuItem class="menu-item" @select="exportGrid('json', true)">JSON</DropdownMenuItem>
              <DropdownMenuItem class="menu-item" @select="exportGrid('xml', true)">XML</DropdownMenuItem>
            </template>
          </DropdownMenuContent>
        </DropdownMenuPortal>
      </DropdownMenuRoot>
    </div>

    <ContextMenuRoot>
      <ContextMenuTrigger as-child>
        <div ref="scroller" class="scroller" tabindex="0" @scroll="onScroll" @keydown="onKey">
          <div class="canvas" :style="{ width: totalWidth + 'px', height: HEADER_HEIGHT + rows.length * ROW_HEIGHT + 'px' }">
            <div class="head" :style="{ height: HEADER_HEIGHT + 'px' }">
              <div class="hc gutter" :style="{ width: GUTTER + 'px' }">#</div>
              <div v-for="(column, i) in columns" :key="i" class="hc" :style="{ width: widths[i] + 'px' }" :title="`${column.label} · ${column.typeName}`">
                <Icon v-if="column.primaryKey" :path="mdiKeyVariant" :size="13" class="key" />
                <span class="lbl">{{ column.label }}</span>
                <small>{{ column.typeName.toLowerCase() }}</small>
                <span class="resizer" @mousedown="startResize($event, i)" @dblclick.stop="autoSize(i)" />
              </div>
            </div>
            <div class="body" :style="{ top: HEADER_HEIGHT + start * ROW_HEIGHT + 'px' }">
              <div v-for="(row, k) in visible" :key="row.id" class="r" :class="{ sel: selected.has(row.id), added: row.added, odd: (start + k) % 2 === 1 }" :style="{ height: ROW_HEIGHT + 'px' }">
                <div class="c gutter" :style="{ width: GUTTER + 'px' }" @mousedown="clickGutter(start + k, $event)" @contextmenu="onContext(start + k, Math.max(active.col, 0))">{{ start + k + 1 }}</div>
                <div v-for="(column, i) in columns" :key="i" class="c"
                  :class="{ num: column.numeric, nul: row.c[i] === null, chg: isChanged(row, i), act: active.row === start + k && active.col === i, ro: !canEdit(i) }"
                  :style="{ width: widths[i] + 'px' }"
                  @mousedown.left="clickCell(start + k, i, $event)" @dblclick="startEdit(start + k, i)" @contextmenu="onContext(start + k, i)">
                  <input v-if="editing && editing.rowId === row.id && editing.col === i" ref="editInput" v-model="editing.value" class="cell-input mono"
                    @keydown="onEditKey" @blur="commitEdit()" @mousedown.stop @dblclick.stop />
                  <template v-else>{{ row.c[i] === null ? 'NULL' : row.c[i] }}</template>
                </div>
              </div>
            </div>
          </div>
          <div v-if="rows.length === 0" class="no-rows">No rows</div>
        </div>
      </ContextMenuTrigger>
      <ContextMenuPortal>
        <ContextMenuContent class="menu">
          <ContextMenuItem class="menu-item" @select="copyCell"><Icon :path="mdiContentCopy" /> Copy value <span class="shortcut">Ctrl C</span></ContextMenuItem>
          <ContextMenuItem class="menu-item" @select="copyRows"><Icon :path="mdiContentCopy" /> Copy {{ selected.size > 1 ? selected.size + ' rows' : 'row' }}</ContextMenuItem>
          <ContextMenuItem class="menu-item" @select="copyInsert"><Icon :path="mdiCodeBraces" /> Copy as INSERT</ContextMenuItem>
          <ContextMenuItem class="menu-item" @select="viewValue"><Icon :path="mdiEyeOutline" /> View value</ContextMenuItem>
          <template v-if="grid.editable">
            <ContextMenuSeparator class="menu-sep" />
            <ContextMenuItem class="menu-item" :disabled="!canEdit(ctx.col)" @select="startEdit(ctx.row, ctx.col)"><Icon :path="mdiPencilOutline" /> Edit <span class="shortcut">Enter</span></ContextMenuItem>
            <ContextMenuItem class="menu-item" :disabled="!canEdit(ctx.col)" @select="setNull"><Icon :path="mdiCheck" style="visibility: hidden" /> Set NULL</ContextMenuItem>
            <ContextMenuItem class="menu-item" @select="duplicateRow"><Icon :path="mdiContentDuplicate" /> Duplicate row</ContextMenuItem>
            <ContextMenuItem class="menu-item danger" @select="deleteSelected"><Icon :path="mdiDeleteOutline" /> Delete {{ selected.size > 1 ? selected.size + ' rows' : 'row' }} <span class="shortcut">Del</span></ContextMenuItem>
          </template>
          <ContextMenuSeparator class="menu-sep" />
          <ContextMenuSub>
            <ContextMenuSubTrigger class="menu-item"><Icon :path="mdiExport" /> Export <Icon :path="mdiChevronRight" style="margin-left: auto" /></ContextMenuSubTrigger>
            <ContextMenuPortal>
              <ContextMenuSubContent class="menu">
                <ContextMenuItem class="menu-item" @select="exportGrid('csv', selected.size > 0)">CSV</ContextMenuItem>
                <ContextMenuItem class="menu-item" @select="exportGrid('json', selected.size > 0)">JSON</ContextMenuItem>
                <ContextMenuItem class="menu-item" @select="exportGrid('xml', selected.size > 0)">XML</ContextMenuItem>
              </ContextMenuSubContent>
            </ContextMenuPortal>
          </ContextMenuSub>
        </ContextMenuContent>
      </ContextMenuPortal>
    </ContextMenuRoot>

    <div v-if="hasPending" class="pending">
      <span v-if="pending.updates" class="badge">{{ pending.updates }} update{{ pending.updates === 1 ? '' : 's' }}</span>
      <span v-if="pending.inserts" class="badge">{{ pending.inserts }} insert{{ pending.inserts === 1 ? '' : 's' }}</span>
      <span v-if="pending.deletes" class="badge">{{ pending.deletes }} delete{{ pending.deletes === 1 ? '' : 's' }}</span>
      <span class="hint">Applied together in one transaction.</span>
      <span class="sp" />
      <button class="btn sm" :disabled="busy" @click="revert"><Icon :path="mdiUndo" /> Revert</button>
      <button class="btn sm primary" :disabled="busy" @click="apply"><Icon :path="mdiCheck" /> {{ busy ? 'Applying…' : 'Apply changes' }}</button>
    </div>
  </div>
</template>

<style scoped>
.grid-wrap { display: flex; flex-direction: column; height: 100%; min-height: 0; }
.gtools { display: flex; align-items: center; gap: 6px; padding: 4px 10px; border-bottom: 1px solid var(--border); min-height: 34px; }
.count { white-space: nowrap; }
.scroller { flex: 1; min-height: 0; overflow: auto; position: relative; outline: none; font-size: 12.5px; }
.canvas { position: relative; }
.head { position: sticky; top: 0; z-index: 2; display: flex; background: var(--bg-side); border-bottom: 1px solid var(--border-strong); }
.hc { position: relative; flex: none; display: flex; align-items: center; gap: 6px; padding: 0 12px; height: 100%; border-right: 1px solid var(--border); color: var(--text-2); font-weight: 600; overflow: hidden; white-space: nowrap; }
.hc .lbl { overflow: hidden; text-overflow: ellipsis; min-width: 0; flex: 0 1 auto; }
.hc small { color: var(--text-3); font: 400 10.5px var(--mono); flex: none; }
.hc .key { color: var(--text-3); }
.hc.gutter, .c.gutter { position: sticky; left: 0; z-index: 1; justify-content: center; padding: 0; background: var(--bg-side); color: var(--text-3); font-weight: 400; font-size: 11px; }
.hc.gutter { z-index: 3; }
.resizer { position: absolute; right: 0; top: 0; bottom: 0; width: 6px; cursor: col-resize; }
.resizer:hover { background: var(--accent); opacity: 0.5; }
.body { position: absolute; left: 0; }
.r { display: flex; }
.r:hover .c:not(.gutter) { background: var(--bg-hover); }
.c { flex: none; display: flex; align-items: center; padding: 0 12px; border-bottom: 1px solid var(--border); border-right: 1px solid var(--border); overflow: hidden; white-space: nowrap; text-overflow: ellipsis; display: block; line-height: 25px; font-family: var(--mono); font-size: 12px; position: relative; }
.c.gutter { display: block; text-align: center; line-height: 25px; border-right: 1px solid var(--border-strong); cursor: pointer; }
.c.num { text-align: right; }
.c.nul { color: var(--text-3); font-style: italic; }
.r.sel .c:not(.gutter) { background: var(--accent-tint); }
.r.sel .c.gutter { color: var(--accent); background: var(--accent-tint-strong); }
.r.added .c:not(.gutter) { background: color-mix(in srgb, var(--ok) 13%, var(--bg)); }
.c.chg { background: var(--accent-tint-strong) !important; box-shadow: inset 3px 0 0 var(--accent); }
.c.act { outline: 2px solid var(--accent); outline-offset: -2px; z-index: 1; }
.cell-input { position: absolute; inset: 0; width: 100%; height: 100%; border: 0; outline: 2px solid var(--accent); outline-offset: -2px; background: var(--bg-raised); color: var(--text); padding: 0 12px; font-size: 12px; user-select: text; }
.no-rows { position: absolute; top: 60px; left: 0; right: 0; text-align: center; color: var(--text-3); pointer-events: none; }
.pending { display: flex; align-items: center; gap: 8px; padding: 6px 10px; background: var(--accent-tint); border-top: 1px solid var(--border); }
</style>
