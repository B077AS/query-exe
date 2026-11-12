import { reactive, watch } from 'vue';
import {
  api,
  events,
  type ConnectionInfo,
  type EngineType,
  type GridPatch,
  type GridSnapshot,
  type QueryFinishedEvent,
  type QueryResultEvent,
  type RecentFile,
  type SchemaTree,
  type TreeContainer,
} from './api';
import { fail, prefs, toast } from './ui';

// The application state: saved connections, the open connections ("sessions": their tree and current database), the query
// tabs (each belongs to one connection) and the dialogs. There is a single view: connections are the roots of the tree.

export type ResultItem =
  | { key: number; kind: 'grid'; sql: string; grid: GridSnapshot }
  | { key: number; kind: 'count'; sql: string; count: number; millis: number }
  | { key: number; kind: 'error'; sql: string; message: string; from: number; to: number };

export interface QueryTab {
  id: string;
  connectionId: string;
  title: string;
  text: string;
  savedText: string;
  path: string | null;
  running: boolean;
  items: ResultItem[];
  active: number;
  /** Set by a failed statement: where in the text it is, so the editor can mark it. */
  errorRange: { from: number; to: number } | null;
  lastMillis: number | null;
  lastOk: boolean | null;
  /** Set once the script finished, to show "n results". */
  ran: boolean;
}

export interface Session {
  connectionId: string;
  info: ConnectionInfo;
  tree: SchemaTree | null;
  loadingTree: boolean;
  current: string | null;
}

export const app = reactive({
  ready: false,
  types: [] as EngineType[],
  connections: [] as ConnectionInfo[],
  folders: [] as string[],
  collapsedFolders: [] as string[],
  /** Accent key chosen for a folder (see accents.ts), by folder name. */
  folderColors: {} as Record<string, string>,
  /** The arrangement made by dragging: connection ids and folder names in order. Empty = alphabetical. */
  order: [] as string[],
  folderOrder: [] as string[],
  recent: [] as RecentFile[],
  sessions: {} as Record<string, Session>,
  tabs: [] as QueryTab[],
  activeTab: null as string | null,
  /** The connection the tree has selected (or that was used last): where "New query" goes. */
  selected: null as string | null,
  /** In the one-connection-at-a-time tree: the connection that fills the tree (null shows the list of connections). */
  focused: null as string | null,
  connecting: {} as Record<string, boolean>,
});

export const dialogs = reactive({
  connection: null as null | { id?: string; type?: string; folder?: string },
  password: null as null | { info: ConnectionInfo; error: string },
  settings: false,
  /** Which page of the settings window is showing. */
  settingsSection: 'appearance' as 'appearance' | 'editor' | 'connections' | 'shortcuts' | 'about',
  tableEditor: null as null | { connectionId: string; container: string; table?: string },
  tableInfo: null as null | { connectionId: string; container: string; table: string },
  columnInfo: null as null | { connectionId: string; container: string; table: string; column: string },
  script: null as null | { title: string; sql: string },
  diagram: null as null | { connectionId: string; container: string },
  monitor: null as null | { connectionId: string },
  newContainer: null as null | { connectionId: string; kind: string },
  activeConnections: false,
  /** Script files dropped on the window while no connection was selected: asks which one they open on. */
  dropFiles: null as null | { files: { path: string; name: string }[] },
});

let tabCounter = 1;
let resultKey = 1;
const queryCounters: Record<string, number> = {};

export const infoOf = (connectionId: string): ConnectionInfo | undefined => app.connections.find((c) => c.id === connectionId);

export function activeTab(): QueryTab | null {
  return app.tabs.find((t) => t.id === app.activeTab) ?? null;
}

/** The session of the connection the user is working with: the active tab's, else the selected one, else the first open. */
export function currentSession(): Session | null {
  const tab = activeTab();
  const id = tab?.connectionId ?? app.selected ?? Object.keys(app.sessions)[0];
  return id ? app.sessions[id] ?? null : null;
}

// ---- connections --------------------------------------------------------------------------------------------------

export async function loadConnections(): Promise<void> {
  try {
    const [types, connections, folders, recent] = await Promise.all([api.types(), api.connections(), api.folders(), api.recentFiles()]);
    app.types = types;
    app.connections = connections;
    app.folders = folders.folders;
    app.collapsedFolders = folders.collapsed;
    app.folderColors = folders.colors ?? {};
    app.order = folders.order ?? [];
    app.folderOrder = folders.folderOrder ?? [];
    app.recent = recent;
    for (const info of connections) {
      if (app.sessions[info.id]) app.sessions[info.id].info = { ...info, connected: true };
    }
  } catch (e) {
    fail('Could not load connections', e);
  }
}

export async function reloadConnections(): Promise<void> {
  try {
    app.connections = await api.connections();
    app.recent = await api.recentFiles();
    for (const info of app.connections) {
      if (app.sessions[info.id]) app.sessions[info.id].info = { ...info, connected: true };
    }
  } catch (e) {
    fail('Could not load connections', e);
  }
}

export async function connect(info: ConnectionInfo, password?: string, remember?: boolean): Promise<boolean> {
  if (app.connecting[info.id]) return false;
  app.connecting[info.id] = true;
  try {
    const result = await api.connect(info.id, password, remember);
    if (result.needsPassword) {
      dialogs.password = { info, error: '' };
      return false;
    }
    dialogs.password = null;
    openSession(info);
    return true;
  } catch (e) {
    const message = e instanceof Error ? e.message : String(e);
    // a wrong password keeps the prompt open so it can be typed again; the reason comes as a toast
    if (password !== undefined) dialogs.password = { info, error: '' };
    toast('error', 'Connection failed', message);
    return false;
  } finally {
    delete app.connecting[info.id];
  }
}

function openSession(info: ConnectionInfo): void {
  let session = app.sessions[info.id];
  if (!session) {
    session = { connectionId: info.id, info, tree: null, loadingTree: false, current: null };
    app.sessions[info.id] = session;
    session = app.sessions[info.id]; // the reactive proxy: changes to the raw object would not update the page
  }
  session.info = { ...info, connected: true };
  app.selected = info.id;
  if (prefs.treeMode === 'focus') app.focused = info.id;
  void loadTree(session);
  // the first connection of a fresh window gets a query tab to write in
  if (app.tabs.length === 0) newTab(info.id);
}

export async function loadTree(session: Session): Promise<void> {
  session.loadingTree = true;
  try {
    const tree = await api.loadSchema(session.connectionId);
    session.tree = tree;
    session.current = tree.current;
  } catch (e) {
    fail('Could not read the database structure', e);
  } finally {
    session.loadingTree = false;
  }
}

export async function refreshContainer(session: Session, name: string): Promise<void> {
  try {
    const fresh = await api.reloadContainer(session.connectionId, name);
    replaceContainer(session, fresh);
  } catch (e) {
    fail('Could not refresh', e);
  }
}

function replaceContainer(session: Session, fresh: TreeContainer): void {
  if (!session.tree) return;
  const index = session.tree.containers.findIndex((c) => c.name === fresh.name);
  if (index >= 0) session.tree.containers[index] = { ...fresh, parent: session.tree.containers[index].parent };
  else session.tree.containers.push(fresh);
}

export async function disconnect(id: string): Promise<void> {
  try {
    await api.disconnect(id);
  } catch (e) {
    fail('Could not disconnect', e);
  }
  delete app.sessions[id];
  if (app.selected === id) app.selected = Object.keys(app.sessions)[0] ?? null;
  if (app.focused === id) app.focused = null;
  void reloadConnections();
}

export async function selectContainer(session: Session, name: string): Promise<void> {
  try {
    await api.useContainer(session.connectionId, name);
    session.current = name;
  } catch (e) {
    fail('Could not switch to ' + name, e);
  }
}

// ---- tabs ---------------------------------------------------------------------------------------------------------

export function newTab(connectionId: string, text = '', title?: string, path: string | null = null): QueryTab {
  queryCounters[connectionId] = (queryCounters[connectionId] ?? 0) + 1;
  const tab: QueryTab = {
    id: 't' + tabCounter++,
    connectionId,
    title: title ?? `Query ${queryCounters[connectionId]}`,
    text,
    savedText: path ? text : '',
    path,
    running: false,
    items: [],
    active: 0,
    errorRange: null,
    lastMillis: null,
    lastOk: null,
    ran: false,
  };
  app.tabs.push(tab);
  app.activeTab = tab.id;
  app.selected = connectionId;
  if (prefs.treeMode === 'focus' && app.focused && app.focused !== connectionId && app.sessions[connectionId]) app.focused = connectionId;
  return app.tabs[app.tabs.length - 1]; // the reactive proxy, so later changes (running, results) update the page
}

/** A new query tab on the connection the user is working with; says what to do when there is none. */
export function newQuery(): QueryTab | null {
  const session = currentSession();
  if (!session) {
    toast('info', 'No connection open', 'Double-click a connection on the left to connect to it first.');
    return null;
  }
  return newTab(session.connectionId);
}

export function isDirty(tab: QueryTab): boolean {
  return tab.text !== tab.savedText && (tab.path !== null || tab.text.trim() !== '');
}

export function closeTab(id: string): void {
  const index = app.tabs.findIndex((t) => t.id === id);
  if (index < 0) return;
  app.tabs.splice(index, 1);
  void api.closeTab(id).catch(() => undefined);
  if (app.activeTab === id) {
    app.activeTab = app.tabs[Math.min(index, app.tabs.length - 1)]?.id ?? null;
  }
}

function findTab(id: string): QueryTab | null {
  return app.tabs.find((t) => t.id === id) ?? null;
}

export interface RunRequest {
  mode: 'all' | 'selection' | 'statement';
  text: string;
  caret?: number;
  offset?: number;
}

export async function runTab(tab: QueryTab, request: RunRequest): Promise<void> {
  if (tab.running) return;
  if (!app.sessions[tab.connectionId]) {
    toast('info', 'Not connected', `Connect to ${infoOf(tab.connectionId)?.name ?? 'this connection'} first (double-click it on the left).`);
    return;
  }
  if (request.text.trim() === '') {
    toast('info', 'Nothing to run', 'Write a statement first.');
    return;
  }
  tab.running = true;
  tab.items = [];
  tab.active = 0;
  tab.errorRange = null;
  tab.ran = false;
  tab.lastOk = null;
  try {
    await api.run({ tabId: tab.id, connectionId: tab.connectionId, ...request });
  } catch (e) {
    tab.running = false;
    fail('Could not run the query', e);
  }
}

export async function cancelTab(tab: QueryTab): Promise<void> {
  try {
    await api.cancel(tab.id);
  } catch (e) {
    fail('Could not cancel', e);
  }
}

function onResult(event: QueryResultEvent): void {
  const tab = findTab(event.tabId);
  if (!tab) return;
  if (event.kind === 'grid') {
    tab.items.push({ key: resultKey++, kind: 'grid', sql: event.sql, grid: event.grid });
  } else if (event.kind === 'count') {
    tab.items.push({ key: resultKey++, kind: 'count', sql: event.sql, count: event.count, millis: event.millis });
  } else {
    tab.items.push({ key: resultKey++, kind: 'error', sql: event.sql, message: event.message, from: event.from, to: event.to });
    tab.errorRange = { from: event.from, to: event.to };
  }
  // show the most useful result: the last grid, or the error
  const lastGrid = tab.items.map((i) => i.kind).lastIndexOf('grid');
  tab.active = event.kind === 'error' ? tab.items.length - 1 : lastGrid >= 0 ? lastGrid : tab.items.length - 1;
}

function onFinished(event: QueryFinishedEvent): void {
  const tab = findTab(event.tabId);
  if (!tab) return;
  const session = app.sessions[tab.connectionId];
  tab.running = false;
  tab.ran = true;
  tab.lastOk = event.ok;
  tab.lastMillis = event.millis;
  if (!session) return;
  if (event.schema) session.current = event.schema;
  if (event.databasesChanged) {
    void loadTree(session);
  } else if (event.structureChanged) {
    const target = session.current;
    if (target) void refreshContainer(session, target);
    else void loadTree(session);
  }
}

// ---- grids --------------------------------------------------------------------------------------------------------

export function applyPatch(grid: GridSnapshot, patch: GridPatch): void {
  for (const row of patch.rows) {
    const index = grid.rows.findIndex((r) => r.id === row.id);
    if (index >= 0) grid.rows[index] = row;
    else grid.rows.push(row);
  }
  if (patch.removed.length > 0) {
    const gone = new Set(patch.removed);
    grid.rows = grid.rows.filter((r) => !gone.has(r.id));
  }
  grid.pending = patch.pending;
}

export function replaceGrid(grid: GridSnapshot, fresh: GridSnapshot): void {
  grid.rows = fresh.rows;
  grid.pending = fresh.pending;
  grid.truncated = fresh.truncated;
}

// ---- opening things -----------------------------------------------------------------------------------------------

/** Opens a table in a new tab as a SELECT and runs it. */
export async function openTable(session: Session, container: string, table: string, run = true): Promise<void> {
  try {
    const { sql } = await api.script(session.connectionId, container, table, 'select');
    if (container && container !== session.current) await selectContainer(session, container);
    const tab = newTab(session.connectionId, sql, table);
    tab.savedText = sql; // generated, not something the user wrote: closing it should not ask
    if (run) await runTab(tab, { mode: 'all', text: sql });
  } catch (e) {
    fail('Could not open the table', e);
  }
}

/** Opens an empty query tab for a database/schema, switching to it first. */
export async function openQuery(session: Session, container: string): Promise<void> {
  if (container && container !== session.current) await selectContainer(session, container);
  newTab(session.connectionId);
}

export async function openScript(session: Session, container: string, table: string, kind: 'select' | 'create' | 'insert'): Promise<void> {
  try {
    const { sql } = await api.script(session.connectionId, container, table, kind);
    newTab(session.connectionId, sql, `${table} (${kind})`).savedText = sql;
  } catch (e) {
    fail('Could not generate the script', e);
  }
}

export async function openRecent(file: RecentFile): Promise<void> {
  let connectionId = file.connectionId && infoOf(file.connectionId) ? file.connectionId : null;
  if (connectionId && !app.sessions[connectionId]) {
    const info = infoOf(connectionId)!;
    if (!(await connect(info))) return;
  }
  connectionId = connectionId ?? currentSession()?.connectionId ?? null;
  if (!connectionId) {
    toast('info', 'Pick a connection first', 'Connect to a database to open this file.');
    return;
  }
  try {
    const opened = await api.openPath(file.path, connectionId);
    if (opened.path) addFileTab(connectionId, opened.path, opened.name ?? file.name, opened.text ?? '');
  } catch (e) {
    fail('Could not open the file', e);
    void api.removeRecent(file.path).then(reloadConnections);
  }
}

/** Lets the user pick a .sql file and opens it in a new tab on this connection (connecting first when it is not open). */
export async function openFileOnConnection(connectionId: string): Promise<void> {
  if (!app.sessions[connectionId]) {
    const info = infoOf(connectionId);
    if (!info || !(await connect(info))) return;
  }
  try {
    const opened = await api.openFile(connectionId);
    if (!opened.cancelled && opened.path) addFileTab(connectionId, opened.path, opened.name ?? 'Script', opened.text ?? '');
  } catch (e) {
    fail('Could not open the file', e);
  }
}

export function addFileTab(connectionId: string, path: string, name: string, text: string, replaceBlank = true): void {
  // an untouched blank tab is replaced by the file rather than left next to it
  const blank = replaceBlank && app.tabs.length === 1 && app.tabs[0].text.trim() === '' && !app.tabs[0].path ? app.tabs[0] : null;
  newTab(connectionId, text, name, path);
  if (blank) closeTab(blank.id);
}

let started = false;

export function initStore(): void {
  if (started) return;
  started = true;
  events.onResult(onResult);
  events.onFinished(onFinished);
  events.onFilesDropped(onFilesDropped);
  // a moving bar on the taskbar icon while any query runs
  watch(() => app.tabs.some((t) => t.running), (busy) => void api.setBusy(busy).catch(() => {}));
}

/** Files dropped on the window: each .sql file opens as a tab on the current connection. */
async function onFilesDropped(event: { files: { path: string; name: string }[] }): Promise<void> {
  const scripts = event.files.filter((f) => /\.sql$/i.test(f.name));
  if (scripts.length === 0) {
    toast('info', 'Only .sql files can be opened', 'Drop a SQL script on the window.');
    return;
  }
  const connectionId = currentSession()?.connectionId ?? null;
  if (!connectionId) {
    // nothing selected: let the user choose which connection the scripts open on
    if (app.connections.length === 0) toast('info', 'No connections yet', 'Create a connection first, then drop the file again.');
    else dialogs.dropFiles = { files: scripts };
    return;
  }
  await openDroppedFiles(scripts, connectionId);
}

/** Opens each script as a tab on a connection (which must already be open). */
export async function openDroppedFiles(scripts: { path: string; name: string }[], connectionId: string): Promise<void> {
  for (const file of scripts) {
    try {
      const opened = await api.openPath(file.path, connectionId);
      if (opened.path) addFileTab(connectionId, opened.path, opened.name ?? file.name, opened.text ?? '', false);
    } catch (e) {
      fail('Could not open ' + file.name, e);
    }
  }
}
