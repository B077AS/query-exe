<script setup lang="ts">
import {
  mdiChevronDown, mdiChevronRight, mdiCodeBraces, mdiCollapseAllOutline, mdiContentCopy, mdiDatabase, mdiDatabaseOutline, mdiDatabasePlusOutline,
  mdiDeleteOutline, mdiFlashOutline, mdiFolderOutline, mdiFolderPlusOutline, mdiFormatListNumbered, mdiInformationOutline, mdiKeyVariant, mdiLanConnect,
  mdiLanDisconnect, mdiLinkVariant, mdiMagnify, mdiPencilOutline, mdiPin, mdiPinOutline, mdiPlay, mdiPlus, mdiRefresh, mdiSortAscending, mdiTable,
  mdiTableColumn, mdiViewAgendaOutline, mdiPaletteOutline, mdiTableMultiple, mdiTablePlus, mdiEyeOutline, mdiClose, mdiArrowLeft, mdiServerNetwork, mdiFolderOpenOutline, mdiGraphOutline,
} from '@mdi/js';
import {
  ContextMenuContent, ContextMenuItem, ContextMenuPortal, ContextMenuRoot, ContextMenuSeparator, ContextMenuSub, ContextMenuSubContent, ContextMenuSubTrigger,
  ContextMenuTrigger, DropdownMenuContent, DropdownMenuItem, DropdownMenuPortal, DropdownMenuRoot, DropdownMenuSeparator, DropdownMenuTrigger,
} from 'reka-ui';
import { computed, reactive, ref, watchEffect } from 'vue';
import { api, type ConnectionInfo } from '../api';
import {
  duplicateConnection, moveConnection, moveToNewFolder, newFolder, removeConnection, removeFolder, renameFolder, setFolderColor, toggleFolder, togglePin,
} from '../actions';
import { ACCENTS } from '../accents';
import { scheme } from '../theme';
import {
  app, connect, currentSession, dialogs, disconnect, loadTree, newTab, openFileOnConnection, openScript, openTable, refreshContainer, selectContainer, type Session,
} from '../store';
import { confirm, engineBadge, fail, prefs, toast } from '../ui';
import Icon from './Icon.vue';

type Kind = 'folder' | 'connection' | 'database' | 'container' | 'tables' | 'table' | 'column' | 'group' | 'object' | 'partition';
interface Node {
  id: string;
  kind: Kind;
  label: string;
  depth: number;
  connectionId?: string;
  container?: string;
  table?: string;
  meta?: string;
  badge?: string;
  icon: string;
  pk?: boolean;
  hasChildren: boolean;
  bold?: boolean;
  dim?: boolean;
  open?: boolean;
  color?: string;
}

/** The colour a folder was given, in the shade that suits the current theme (undefined = the default grey). */
const tint = (key: string | undefined): string | undefined => {
  const accent = ACCENTS.find((a) => a.key === key);
  return accent ? (scheme.value === 'dark' ? accent.dark : accent.light) : undefined;
};

const filter = ref('');
const expanded = reactive(new Set<string>());
const selected = ref<string | null>(null);
const seeded = new Set<string>();

// the first time a connection's structure arrives, its few top level containers are opened
watchEffect(() => {
  for (const session of Object.values(app.sessions)) {
    const tree = session.tree;
    if (!tree || seeded.has(session.connectionId)) continue;
    seeded.add(session.connectionId);
    const id = session.connectionId;
    expanded.add('cn:' + id);
    tree.containers.forEach((c) => c.parent && expanded.add(`d:${id}:${c.parent}`));
    for (const c of tree.containers) {
      if (tree.containers.length <= 3 || c.name === tree.current) {
        expanded.add(`c:${id}:${c.name}`);
        expanded.add(`t:${id}:${c.name}`);
      }
    }
  }
  for (const id of [...seeded]) if (!app.sessions[id]) seeded.delete(id);
});

const groupIcon: Record<string, string> = { Sequences: mdiFormatListNumbered, Synonyms: mdiLinkVariant, Triggers: mdiFlashOutline, Indexes: mdiSortAscending };
const label = (type: string): string => (type === 'SQLServer' ? 'SQL Server' : type);

function sessionNodes(session: Session, depth: number, q: string, out: Node[]): void {
  const tree = session.tree;
  const cid = session.connectionId;
  if (!tree) return;
  const open = (id: string): boolean => q !== '' || expanded.has(id);
  const parents = [...new Set(tree.containers.map((c) => c.parent).filter((p): p is string => !!p))];

  const addContainer = (c: (typeof tree.containers)[number], d: number): void => {
    const tables = q === '' ? c.tables : c.tables.filter((t) => t.name.toLowerCase().includes(q) || t.columns.some((col) => col.name.toLowerCase().includes(q)));
    if (q !== '' && tables.length === 0 && !c.name.toLowerCase().includes(q)) return;
    const cid2 = `c:${cid}:${c.name}`;
    out.push({ id: cid2, kind: 'container', label: c.name, depth: d, connectionId: cid, container: c.name, icon: c.kind === 'schema' ? mdiFolderOutline : mdiDatabaseOutline,
      hasChildren: true, bold: session.current === c.name, meta: String(c.tables.length) });
    if (!open(cid2)) return;
    const tid = `t:${cid}:${c.name}`;
    out.push({ id: tid, kind: 'tables', label: 'Tables', depth: d + 1, connectionId: cid, container: c.name, icon: mdiTableMultiple, hasChildren: tables.length > 0, meta: String(tables.length) });
    if (open(tid)) {
      for (const t of tables) {
        const id = `tb:${cid}:${c.name}:${t.name}`;
        out.push({ id, kind: 'table', label: t.name, depth: d + 2, connectionId: cid, container: c.name, table: t.name, icon: mdiTable,
          hasChildren: t.columns.length > 0 || t.partitions.length > 0 });
        const columnMatch = q !== '' && t.columns.some((col) => col.name.toLowerCase().includes(q)) && !t.name.toLowerCase().includes(q);
        if (!(expanded.has(id) || columnMatch)) continue;
        const columnNodes = (depth: number): void => {
          for (const col of t.columns) {
            out.push({ id: `${id}:${col.name}`, kind: 'column', label: col.name, depth, connectionId: cid, container: c.name, table: t.name,
              icon: col.primaryKey ? mdiKeyVariant : mdiTableColumn, meta: col.type, pk: col.primaryKey, hasChildren: false });
          }
        };
        if (t.partitions.length === 0) {
          columnNodes(d + 3);
          continue;
        }
        // a partitioned table keeps its columns and its partitions in two subtrees
        const colsId = `${id}:#columns`;
        out.push({ id: colsId, kind: 'group', label: 'Columns', depth: d + 3, connectionId: cid, container: c.name, table: t.name, icon: mdiTableColumn,
          hasChildren: t.columns.length > 0, meta: String(t.columns.length) });
        if (expanded.has(colsId) || columnMatch) columnNodes(d + 4);
        const pid = `${id}:#partitions`;
        const method = t.partitions.find((p) => p.method)?.method;
        out.push({ id: pid, kind: 'group', label: 'Partitions', depth: d + 3, connectionId: cid, container: c.name, table: t.name, icon: mdiViewAgendaOutline,
          hasChildren: true, meta: (method ? method + ' | ' : '') + t.partitions.filter((p) => !p.parent).length });
        if (expanded.has(pid)) {
          // Java leaves a missing parent out of the JSON, so "no parent" is undefined here, not null
          const add = (parent: string | null, depth: number): void => {
            for (const p of t.partitions.filter((x) => (x.parent ?? null) === parent)) {
              const subs = t.partitions.some((x) => x.parent === p.name);
              const partId = `${pid}:${p.name}`;
              out.push({ id: partId, kind: 'partition', label: p.name, depth, connectionId: cid, container: c.name, table: p.table ? p.name : t.name, icon: mdiViewAgendaOutline,
                hasChildren: subs, meta: p.rows != null ? p.rows.toLocaleString() + ' rows' : undefined });
              if (subs && expanded.has(partId)) add(p.name, depth + 1);
            }
          };
          add(null, d + 4);
        }
      }
    }
    for (const g of c.groups) {
      if (q !== '') continue;
      const gid = `g:${cid}:${c.name}:${g.title}`;
      out.push({ id: gid, kind: 'group', label: g.title, depth: d + 1, connectionId: cid, container: c.name, icon: groupIcon[g.title] ?? mdiFolderOutline, hasChildren: g.items.length > 0, meta: String(g.items.length) });
      if (expanded.has(gid)) {
        for (const item of g.items) {
          out.push({ id: `${gid}:${item}`, kind: 'object', label: item, depth: d + 2, connectionId: cid, container: c.name, icon: groupIcon[g.title] ?? mdiFolderOutline, hasChildren: false });
        }
      }
    }
  };

  if (parents.length > 0) {
    for (const p of parents) {
      const id = `d:${cid}:${p}`;
      out.push({ id, kind: 'database', label: p, depth, connectionId: cid, container: p, icon: mdiDatabaseOutline, hasChildren: true });
      if (open(id)) tree.containers.filter((c) => c.parent === p).forEach((c) => addContainer(c, depth + 1));
    }
  } else {
    tree.containers.forEach((c) => addContainer(c, depth));
  }
}

/** In the one-connection-at-a-time mode: the connection that fills the tree right now. */
const focusSession = computed(() => (prefs.treeMode === 'focus' && app.focused ? app.sessions[app.focused] ?? null : null));

const nodes = computed<Node[]>(() => {
  const q = filter.value.trim().toLowerCase();
  const out: Node[] = [];
  if (focusSession.value) {
    sessionNodes(focusSession.value, 0, q, out);
    return out;
  }
  const matches = (c: ConnectionInfo): boolean => {
    if (q === '') return true;
    if ([c.name, c.type, c.target].some((v) => (v ?? '').toLowerCase().includes(q))) return true;
    const tree = app.sessions[c.id]?.tree;
    return !!tree?.containers.some((k) => k.name.toLowerCase().includes(q) || k.tables.some((t) => t.name.toLowerCase().includes(q) || t.columns.some((col) => col.name.toLowerCase().includes(q))));
  };
  const byName = (a: ConnectionInfo, b: ConnectionInfo): number => Number(b.pinned) - Number(a.pinned) || a.name.localeCompare(b.name);

  const addConnection = (c: ConnectionInfo, depth: number): void => {
    const session = app.sessions[c.id];
    const id = 'cn:' + c.id;
    out.push({ id, kind: 'connection', label: c.name, depth, connectionId: c.id, icon: session ? mdiDatabase : mdiDatabaseOutline, badge: engineBadge(c.type),
      hasChildren: !!session && prefs.treeMode === 'all', bold: !!session, dim: !session, open: expanded.has(id) });
    if (session && prefs.treeMode === 'all' && (q !== '' || expanded.has(id))) sessionNodes(session, depth + 1, q, out);
  };

  const visible = app.connections.filter(matches);
  [...visible].filter((c) => !c.folder).sort(byName).forEach((c) => addConnection(c, 0));
  const names = new Set([...app.folders, ...app.connections.map((c) => c.folder).filter((f): f is string => !!f)]);
  for (const name of [...names].sort((a, b) => a.localeCompare(b))) {
    const items = visible.filter((c) => c.folder === name).sort(byName);
    if (items.length === 0 && q !== '') continue;
    const open = q !== '' || !app.collapsedFolders.includes(name);
    out.push({ id: 'f:' + name, kind: 'folder', label: name, depth: 0, icon: mdiFolderOutline, hasChildren: items.length > 0, meta: String(items.length), open, color: tint(app.folderColors[name]) });
    if (open) items.forEach((c) => addConnection(c, 1));
  }
  return out;
});

const isOpen = (n: Node): boolean => (n.kind === 'folder' || n.kind === 'connection' ? !!n.open || filter.value !== '' : expanded.has(n.id) || filter.value !== '');

function toggle(n: Node): void {
  if (!n.hasChildren) return;
  if (n.kind === 'folder') {
    toggleFolder(n.label);
    return;
  }
  if (expanded.has(n.id)) expanded.delete(n.id);
  else expanded.add(n.id);
}

const sessionOf = (n: Node): Session | undefined => (n.connectionId ? app.sessions[n.connectionId] : undefined);
const infoOf = (n: Node): ConnectionInfo | undefined => app.connections.find((c) => c.id === n.connectionId);

/** One click only selects; nothing expands. A database or schema becomes the one queries run in through "Use". */
function click(n: Node): void {
  selected.value = n.id;
  if (n.connectionId) app.selected = n.connectionId;
}

async function openConnection(c: ConnectionInfo): Promise<void> {
  if (app.sessions[c.id]) {
    if (prefs.treeMode === 'focus') app.focused = c.id;
    return;
  }
  if (await connect(c)) expanded.add('cn:' + c.id); // (in the one-connection mode connect() also focuses it)
}

/** Double click expands or collapses; on a connection that is not open yet it connects first. */
async function dbl(n: Node): Promise<void> {
  if (n.kind === 'connection') {
    const info = infoOf(n);
    if (info && (!app.sessions[info.id] || prefs.treeMode === 'focus')) await openConnection(info);
    else toggle(n);
    return;
  }
  toggle(n);
}

async function copyText(text: string): Promise<void> {
  try {
    await navigator.clipboard.writeText(text);
    toast('info', 'Copied', text.length > 80 ? text.slice(0, 80) + '…' : text);
  } catch (e) {
    fail('Could not copy', e);
  }
}

async function dropTable(n: Node): Promise<void> {
  const session = sessionOf(n);
  if (!session || !n.table) return;
  if (!(await confirm('Drop table', `Delete table "${n.table}"? This cannot be undone.`, 'Drop table', true))) return;
  try {
    await api.dropTable(session.connectionId, n.container!, n.table);
    await refreshContainer(session, n.container!);
    toast('ok', 'Table dropped', n.table);
  } catch (e) {
    fail('Could not drop the table', e);
  }
}

const isSchema = (session: Session | undefined): boolean => session?.info.type === 'PostgreSQL';

async function dropContainer(n: Node): Promise<void> {
  const session = sessionOf(n);
  if (!session) return;
  const what = isSchema(session) ? 'schema' : 'database';
  if (!(await confirm(`Drop ${what}`, `Delete ${what} "${n.container}" and everything in it? This cannot be undone.`, `Drop ${what}`, true))) return;
  try {
    await api.dropContainer(session.connectionId, n.container!);
    await loadTree(session);
    toast('ok', `${what[0].toUpperCase()}${what.slice(1)} dropped`, n.container);
  } catch (e) {
    fail(`Could not drop the ${what}`, e);
  }
}

async function disconnectNode(n: Node): Promise<void> {
  if (!n.connectionId) return;
  await disconnect(n.connectionId);
  expanded.delete('cn:' + n.connectionId);
}

function refreshAll(): void {
  for (const session of Object.values(app.sessions)) void loadTree(session);
  void api.connections().then((c) => (app.connections = c));
}

function collapseAll(): void {
  expanded.clear();
  for (const f of new Set([...app.folders, ...app.connections.map((c) => c.folder).filter((x): x is string => !!x)])) {
    if (!app.collapsedFolders.includes(f)) toggleFolder(f);
  }
  filter.value = '';
}

const working = computed(() => currentSession());
</script>

<template>
  <div class="tree-wrap">
    <div class="sh">
      <template v-if="focusSession">
        <button class="ib" title="Back to all connections" @click="app.focused = null"><Icon :path="mdiArrowLeft" /></button>
        <span class="badge">{{ engineBadge(focusSession.info.type) }}</span>
        <span class="t focus-name" :title="focusSession.info.name">{{ focusSession.info.name }}</span>
      </template>
      <span v-else class="t">Connections</span>
      <DropdownMenuRoot>
        <DropdownMenuTrigger class="ib" title="Create"><Icon :path="mdiPlus" /></DropdownMenuTrigger>
        <DropdownMenuPortal>
          <DropdownMenuContent class="menu" align="end" :side-offset="4">
            <DropdownMenuItem class="menu-item" @select="dialogs.connection = {}"><Icon :path="mdiLanConnect" /> New connection</DropdownMenuItem>
            <DropdownMenuItem class="menu-item" @select="newFolder()"><Icon :path="mdiFolderPlusOutline" /> New folder</DropdownMenuItem>
            <template v-if="working">
              <DropdownMenuSeparator class="menu-sep" />
              <div class="menu-label">{{ working.info.name }}</div>
              <DropdownMenuItem class="menu-item" :disabled="!working.current" @select="working.current && (dialogs.tableEditor = { connectionId: working.connectionId, container: working.current })"><Icon :path="mdiTablePlus" /> New table</DropdownMenuItem>
              <DropdownMenuItem v-if="working.info.type !== 'SQLite'" class="menu-item" @select="dialogs.newContainer = { connectionId: working.connectionId, kind: isSchema(working) ? 'schema' : 'database' }"><Icon :path="mdiDatabasePlusOutline" /> New {{ isSchema(working) ? 'schema' : 'database' }}</DropdownMenuItem>
            </template>
          </DropdownMenuContent>
        </DropdownMenuPortal>
      </DropdownMenuRoot>
      <button class="ib" title="Refresh" @click="refreshAll"><Icon :path="mdiRefresh" /></button>
      <button class="ib" title="Collapse all" @click="collapseAll"><Icon :path="mdiCollapseAllOutline" /></button>
      <button class="ib" :title="'Active connections (' + Object.keys(app.sessions).length + ')'" @click="dialogs.activeConnections = true"><Icon :path="mdiServerNetwork" /></button>
    </div>
    <label class="search"><Icon :path="mdiMagnify" /><input v-model="filter" placeholder="Filter connections and objects" /><button v-if="filter" class="ib sm" @click="filter = ''"><Icon :path="mdiClose" /></button></label>

    <div class="tree" role="tree">
      <div v-if="focusSession && focusSession.loadingTree && !focusSession.tree" class="empty">Reading structure…</div>
      <div v-if="app.ready && app.connections.length === 0" class="empty">
        No connections yet.<br /><button class="btn" style="margin-top: 10px" @click="dialogs.connection = {}"><Icon :path="mdiPlus" /> New connection</button>
      </div>
      <ContextMenuRoot v-for="n in nodes" :key="n.id">
        <ContextMenuTrigger as-child>
          <div class="node" :class="{ sel: selected === n.id, dim: n.dim, bold: n.bold, live: n.kind === 'connection' && !n.dim, current: n.kind === 'container' && n.bold }" :style="{ paddingLeft: n.depth * 14 + 6 + 'px' }" role="treeitem"
            :data-connection="n.kind === 'connection' ? n.connectionId : undefined" @click="click(n)" @dblclick="dbl(n)" @contextmenu="click(n)">
            <button class="chev" tabindex="-1" @click.stop="toggle(n)">
              <Icon v-if="n.hasChildren" :path="isOpen(n) ? mdiChevronDown : mdiChevronRight" :size="16" />
            </button>
            <Icon :path="n.icon" class="ic" :class="{ key: n.pk }" :style="n.color ? { color: n.color } : undefined" />
            <span class="label">{{ n.label }}</span>
            <span v-if="n.badge" class="badge">{{ n.badge }}</span>
            <span v-if="n.kind === 'connection' && app.connecting[n.connectionId!]" class="meta">connecting…</span>
            <span v-else-if="n.meta" class="meta mono"><template v-for="(m, i) in n.meta.split(' | ')" :key="i"><i v-if="i" class="vsep" />{{ m }}</template></span>
          </div>
        </ContextMenuTrigger>
        <ContextMenuPortal>
          <ContextMenuContent v-if="n.kind === 'connection'" class="menu">
            <ContextMenuItem v-if="!sessionOf(n)" class="menu-item" @select="openConnection(infoOf(n)!)"><Icon :path="mdiLanConnect" /> Connect</ContextMenuItem>
            <template v-else>
              <ContextMenuItem class="menu-item" @select="newTab(n.connectionId!)"><Icon :path="mdiPlay" /> New query</ContextMenuItem>
              <ContextMenuItem class="menu-item" @select="loadTree(sessionOf(n)!)"><Icon :path="mdiRefresh" /> Refresh</ContextMenuItem>
              <ContextMenuItem class="menu-item" @select="disconnectNode(n)"><Icon :path="mdiLanDisconnect" /> Disconnect</ContextMenuItem>
            </template>
            <ContextMenuItem class="menu-item" @select="openFileOnConnection(n.connectionId!)"><Icon :path="mdiFolderOpenOutline" /> Open SQL file…</ContextMenuItem>
            <ContextMenuSeparator class="menu-sep" />
            <ContextMenuItem class="menu-item" @select="dialogs.connection = { id: n.connectionId }"><Icon :path="mdiPencilOutline" /> Edit connection</ContextMenuItem>
            <ContextMenuItem class="menu-item" @select="duplicateConnection(infoOf(n)!)"><Icon :path="mdiContentCopy" /> Duplicate</ContextMenuItem>
            <ContextMenuItem class="menu-item" @select="togglePin(infoOf(n)!)"><Icon :path="infoOf(n)?.pinned ? mdiPinOutline : mdiPin" /> {{ infoOf(n)?.pinned ? 'Unpin' : 'Pin to top' }}</ContextMenuItem>
            <ContextMenuSub>
              <ContextMenuSubTrigger class="menu-item"><Icon :path="mdiFolderOutline" /> Move to folder <Icon :path="mdiChevronRight" style="margin-left: auto" /></ContextMenuSubTrigger>
              <ContextMenuPortal>
                <ContextMenuSubContent class="menu">
                  <ContextMenuItem v-if="infoOf(n)?.folder" class="menu-item" @select="moveConnection(infoOf(n)!, '')">No folder</ContextMenuItem>
                  <ContextMenuItem v-for="f in [...new Set([...app.folders, ...app.connections.map((c) => c.folder).filter((x) => !!x)])]" :key="f as string" class="menu-item" :disabled="f === infoOf(n)?.folder" @select="moveConnection(infoOf(n)!, f as string)">{{ f }}</ContextMenuItem>
                  <ContextMenuSeparator class="menu-sep" />
                  <ContextMenuItem class="menu-item" @select="moveToNewFolder(infoOf(n)!)"><Icon :path="mdiPlus" /> New folder…</ContextMenuItem>
                </ContextMenuSubContent>
              </ContextMenuPortal>
            </ContextMenuSub>
            <ContextMenuSeparator class="menu-sep" />
            <ContextMenuItem class="menu-item danger" @select="removeConnection(infoOf(n)!)"><Icon :path="mdiDeleteOutline" /> Delete</ContextMenuItem>
          </ContextMenuContent>
          <ContextMenuContent v-else-if="n.kind === 'folder'" class="menu">
            <ContextMenuItem class="menu-item" @select="dialogs.connection = { folder: n.label }"><Icon :path="mdiLanConnect" /> New connection here</ContextMenuItem>
            <ContextMenuItem class="menu-item" @select="renameFolder(n.label)"><Icon :path="mdiPencilOutline" /> Rename</ContextMenuItem>
            <ContextMenuSub>
              <ContextMenuSubTrigger class="menu-item"><Icon :path="mdiPaletteOutline" /> Color <Icon :path="mdiChevronRight" style="margin-left: auto" /></ContextMenuSubTrigger>
              <ContextMenuPortal>
                <ContextMenuSubContent class="menu">
                  <ContextMenuItem class="menu-item" :class="{ on: !app.folderColors[n.label] }" @select="setFolderColor(n.label, null)"><span class="swatch none" />Default</ContextMenuItem>
                  <ContextMenuItem v-for="a in ACCENTS" :key="a.key" class="menu-item" :class="{ on: app.folderColors[n.label] === a.key }" @select="setFolderColor(n.label, a.key)"><span class="swatch" :style="{ background: tint(a.key) }" />{{ a.name }}</ContextMenuItem>
                </ContextMenuSubContent>
              </ContextMenuPortal>
            </ContextMenuSub>
            <ContextMenuSeparator class="menu-sep" />
            <ContextMenuItem class="menu-item danger" @select="removeFolder(n.label)"><Icon :path="mdiDeleteOutline" /> Delete folder</ContextMenuItem>
          </ContextMenuContent>
          <ContextMenuContent v-else-if="n.kind === 'table'" class="menu">
            <ContextMenuItem class="menu-item" @select="openTable(sessionOf(n)!, n.container!, n.table!)"><Icon :path="mdiPlay" /> Open (SELECT *)</ContextMenuItem>
            <ContextMenuItem class="menu-item" @select="openScript(sessionOf(n)!, n.container!, n.table!, 'select')"><Icon :path="mdiCodeBraces" /> Script: SELECT</ContextMenuItem>
            <ContextMenuItem class="menu-item" @select="openScript(sessionOf(n)!, n.container!, n.table!, 'insert')"><Icon :path="mdiCodeBraces" /> Script: INSERT</ContextMenuItem>
            <ContextMenuItem class="menu-item" @select="openScript(sessionOf(n)!, n.container!, n.table!, 'create')"><Icon :path="mdiCodeBraces" /> Script: CREATE</ContextMenuItem>
            <ContextMenuSeparator class="menu-sep" />
            <ContextMenuItem class="menu-item" @select="dialogs.tableInfo = { connectionId: n.connectionId!, container: n.container!, table: n.table! }"><Icon :path="mdiInformationOutline" /> Properties</ContextMenuItem>
            <ContextMenuItem class="menu-item" @select="dialogs.tableEditor = { connectionId: n.connectionId!, container: n.container!, table: n.table }"><Icon :path="mdiPencilOutline" /> Edit table</ContextMenuItem>
            <ContextMenuItem class="menu-item" @select="copyText(n.table!)"><Icon :path="mdiContentCopy" /> Copy name</ContextMenuItem>
            <ContextMenuSeparator class="menu-sep" />
            <ContextMenuItem class="menu-item danger" @select="dropTable(n)"><Icon :path="mdiDeleteOutline" /> Drop table</ContextMenuItem>
          </ContextMenuContent>
          <ContextMenuContent v-else-if="n.kind === 'column'" class="menu">
            <ContextMenuItem class="menu-item" @select="dialogs.columnInfo = { connectionId: n.connectionId!, container: n.container!, table: n.table!, column: n.label }"><Icon :path="mdiInformationOutline" /> Properties</ContextMenuItem>
            <ContextMenuItem class="menu-item" @select="copyText(n.label)"><Icon :path="mdiContentCopy" /> Copy name</ContextMenuItem>
          </ContextMenuContent>
          <ContextMenuContent v-else-if="n.kind === 'container' || n.kind === 'tables'" class="menu">
            <ContextMenuItem v-if="n.kind === 'container'" class="menu-item" @select="selectContainer(sessionOf(n)!, n.container!)"><Icon :path="mdiPlay" /> Use</ContextMenuItem>
            <ContextMenuItem class="menu-item" @select="dialogs.tableEditor = { connectionId: n.connectionId!, container: n.container! }"><Icon :path="mdiTablePlus" /> New table</ContextMenuItem>
            <ContextMenuItem class="menu-item" @select="refreshContainer(sessionOf(n)!, n.container!)"><Icon :path="mdiRefresh" /> Refresh</ContextMenuItem>
            <ContextMenuItem class="menu-item" @select="dialogs.diagram = { connectionId: n.connectionId!, container: n.container! }"><Icon :path="mdiGraphOutline" /> ER diagram</ContextMenuItem>
            <template v-if="n.kind === 'container'">
              <ContextMenuItem class="menu-item" @select="copyText(n.container!)"><Icon :path="mdiContentCopy" /> Copy name</ContextMenuItem>
              <template v-if="sessionOf(n)?.info.type !== 'SQLite'">
                <ContextMenuSeparator class="menu-sep" />
                <ContextMenuItem class="menu-item danger" @select="dropContainer(n)"><Icon :path="mdiDeleteOutline" /> Drop {{ isSchema(sessionOf(n)) ? 'schema' : 'database' }}</ContextMenuItem>
              </template>
            </template>
          </ContextMenuContent>
          <!-- a partition is only worth a menu where it is a table of its own; the label nodes (groups, objects) have none -->
          <ContextMenuContent v-else-if="n.kind === 'partition' && sessionOf(n)?.info.type === 'PostgreSQL'" class="menu">
            <ContextMenuItem class="menu-item" @select="openTable(sessionOf(n)!, n.container!, n.table!)"><Icon :path="mdiPlay" /> Open (SELECT *)</ContextMenuItem>
          </ContextMenuContent>
        </ContextMenuPortal>
      </ContextMenuRoot>
    </div>
  </div>
</template>

<style scoped>
.tree-wrap { flex: 1; min-height: 0; display: flex; flex-direction: column; }
.sh { display: flex; align-items: center; gap: 2px; padding: 8px 6px 6px 12px; }
.sh .t { font-weight: 600; flex: 1; }
.search { margin: 0 8px 8px; display: flex; align-items: center; gap: 8px; height: 28px; padding: 0 4px 0 8px; border: 1px solid var(--border); border-radius: var(--radius); background: var(--bg-raised); color: var(--text-3); }
.search:focus-within { border-color: var(--accent); }
.search input { flex: 1; min-width: 0; border: 0; outline: 0; background: none; font-size: 12.5px; }
.tree { flex: 1; overflow: auto; padding: 0 6px 8px; }
.node { display: flex; align-items: center; gap: 4px; height: 26px; padding-right: 6px; border-radius: 0; white-space: nowrap; }
.node:hover { background: var(--bg-hover); }
.node.sel { background: var(--accent-tint-strong); }
.node.dim .label { color: var(--text-2); }
/* a connection that is open: accent bar, accent icon, solid engine badge; a closed one is quiet */
.node.live { box-shadow: inset 2px 0 0 var(--accent); background: var(--accent-tint); }
.node.live:hover, .node.live.sel { background: var(--accent-tint-strong); }
.node.live .ic { color: var(--accent); }
.node.live .badge { background: var(--accent); color: var(--on-accent); }
.node.dim .badge { background: var(--bg-hover); color: var(--text-3); }
.focus-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.node.bold .label { font-weight: 600; }
/* the database/schema queries run in: tinted row and accent icon, without the bar an open connection has */
.node.current { background: var(--accent-tint); }
.node.current:hover, .node.current.sel { background: var(--accent-tint-strong); }
.node.current .ic, .node.current .label { color: var(--accent); }
.chev { width: 16px; height: 16px; display: grid; place-items: center; color: var(--text-3); flex: none; }
.ic { color: var(--text-2); }
.swatch { width: 12px; height: 12px; border-radius: 2px; flex: none; }
.swatch.none { border: 1px solid var(--text-3); }
.node.sel .ic { color: var(--accent); }
.ic.key { color: var(--text); }
.label { overflow: hidden; text-overflow: ellipsis; }
.badge { font: 700 9.5px var(--mono); padding: 1px 4px; border-radius: 3px; background: var(--accent-tint-strong); color: var(--accent); flex: none; }
.meta { margin-left: auto; padding-left: 8px; color: var(--text-3); font-size: 11px; }
</style>
