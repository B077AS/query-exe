// One typed function per Java handler (see api/*.java). Keep both in sync by hand.
import { call, onEvent } from './jolt';

// ---- connections -------------------------------------------------------------------------------------------------

export interface DriverInfo {
  name: string;
  version: string;
  driverClass: string;
  downloadUrl: string;
  fileName: string;
  downloaded?: boolean;
}

export interface EngineType {
  key: string;
  defaultPort: string;
  fileBased: boolean;
  jdbcPrefix: string;
  driver: DriverInfo;
}

export interface ConnectionInfo {
  id: string;
  name: string;
  type: string;
  host: string | null;
  port: string | null;
  database: string | null;
  username: string | null;
  url: string | null;
  target: string;
  folder: string | null;
  pinned: boolean;
  hasPassword: boolean;
  connected: boolean;
  driver: DriverInfo | null;
}

export interface ConnectionForm {
  id?: string;
  name: string;
  type: string;
  host: string;
  port: string;
  database: string;
  username: string;
  password: string;
  savePassword: boolean;
  url: string;
  driver: DriverInfo | null;
}

// ---- schema ------------------------------------------------------------------------------------------------------

export interface TreeColumn {
  name: string;
  type: string;
  primaryKey: boolean;
  notNull: boolean;
  autoIncrement: boolean;
}
export interface TreePartition {
  name: string;
  /** The partition this is a sub-partition of; null for a partition of the table. */
  parent: string | null;
  method: string | null;
  bound: string | null;
  rows: number | null;
  /** A table of its own (PostgreSQL): can be opened like one. */
  table: boolean;
}
export interface TreeTable {
  name: string;
  columns: TreeColumn[];
  partitions: TreePartition[];
}
export interface TreeGroup {
  title: string;
  items: string[];
}
export interface TreeContainer {
  name: string;
  kind: 'database' | 'schema';
  parent: string | null;
  tables: TreeTable[];
  groups: TreeGroup[];
}
export interface SchemaTree {
  connectionId: string;
  name: string;
  type: string;
  containers: TreeContainer[];
  current: string | null;
}

export interface ColumnDef {
  columnName?: string;
  name: string;
  type: string;
  primaryKey: boolean;
  notNull: boolean;
  unique: boolean;
  autoIncrement: boolean;
  defaultValue: string;
  /** The name the column had when the editor opened (empty for a new one), to detect renames. */
  original: string;
}

export interface ForeignKeyDef {
  constraintName: string;
  localColumn: string;
  referenceTable: string;
  referenceColumn: string;
  onDelete: string;
  onUpdate: string;
  referenceSchema?: string;
}

export interface TableDescription {
  dataTypes: string[];
  tables: string[];
  columns: { columnName: string; dataType: string; primaryKey: boolean; notNull: boolean; unique: boolean; autoIncrement: boolean; defaultValue: string | null }[];
  foreignKeys: ForeignKeyDef[];
}

export interface TableInfo {
  columns: TableDescription['columns'];
  foreignKeys: ForeignKeyDef[];
  indexes: { name: string; unique: boolean; primary: boolean; type: string | null; columns: string[] }[];
  sizeMb: number | null;
}

export interface ColumnDetails {
  columnName: string;
  tableName: string;
  schemaName: string;
  ordinalPosition: number;
  dataType: string;
  columnType: string;
  characterMaximumLength: number | null;
  numericPrecision: number | null;
  numericScale: number | null;
  collationName: string | null;
  isPrimaryKey: boolean;
  isNullable: boolean;
  isUnique: boolean;
  isAutoIncrement: boolean;
  columnDefault: string | null;
  columnComment: string | null;
  indexes: Record<string, string>[] | null;
  foreignKeyReferences: Record<string, string>[] | null;
  referencedByForeignKeys: Record<string, string>[] | null;
}

// ---- queries and grids -------------------------------------------------------------------------------------------

export interface GridColumn {
  label: string;
  name: string;
  typeName: string;
  sqlType: number;
  primaryKey: boolean;
  editable: boolean;
  numeric: boolean;
}
export interface GridRow {
  id: number;
  /** Cell texts; null is SQL NULL. */
  c: (string | null)[];
  added: boolean;
  /** Edited column indexes. */
  m: number[];
}
export interface Pending {
  updates: number;
  inserts: number;
  deletes: number;
}
export interface GridSnapshot {
  id: string;
  columns: GridColumn[];
  rows: GridRow[];
  editable: boolean;
  truncated: boolean;
  table: string | null;
  millis: number;
  pending: Pending;
}
export interface GridPatch {
  rows: GridRow[];
  removed: number[];
  pending: Pending;
  focusRow: number;
}

export type QueryResultEvent =
  | { tabId: string; runId: string; index: number; sql: string; kind: 'grid'; grid: GridSnapshot }
  | { tabId: string; runId: string; index: number; sql: string; kind: 'count'; count: number; millis: number }
  | { tabId: string; runId: string; index: number; sql: string; kind: 'error'; message: string; from: number; to: number };

export interface QueryFinishedEvent {
  tabId: string;
  runId: string;
  ok: boolean;
  millis: number;
  connectionId: string;
  schema: string | null;
  databasesChanged: boolean;
  structureChanged: boolean;
}

export interface OpenedFile {
  path?: string;
  name?: string;
  text?: string;
  cancelled?: boolean;
}

export interface RecentFile {
  path: string;
  name: string;
  connectionId: string | null;
  lastOpened: number;
}

export interface DiagramData {
  tables: { name: string; columns: { name: string; type: string; primaryKey: boolean; notNull: boolean }[] }[];
  relations: { name: string; from: string; fromColumns: string[]; to: string; toColumns: string[]; onDelete: string; onUpdate: string }[];
}

export interface MonitorChartDef {
  id: string;
  title: string;
  /** "" (a count), "/s", "B/s" or "s". */
  unit: string;
}

export interface MonitorMetric {
  key: string;
  label: string;
  /** The chart it is drawn on; null when it is only a number. */
  chart: string | null;
  kind: 'GAUGE' | 'RATE';
  headline: boolean;
  unit: string;
}

export interface MonitorOpen {
  supported: boolean;
  reason?: string;
  charts?: MonitorChartDef[];
  metrics?: MonitorMetric[];
}

export interface MonitorTable {
  columns: string[];
  rows: (string | null)[][];
  /** Why there is nothing to show (the server's own message, for example a missing privilege). */
  note: string | null;
}

export interface MonitorSample {
  values: Record<string, number>;
  sessions?: MonitorTable;
  locks?: MonitorTable;
}

export interface StatementRange {
  text: string;
  from: number;
  to: number;
}

export interface TableDraft {
  connectionId: string;
  container: string;
  table: string;
  originalTable?: string;
  columns: { name: string; type: string; primaryKey: boolean; notNull: boolean; unique: boolean; autoIncrement: boolean; defaultValue: string }[];
  foreignKeys: ForeignKeyDef[];
  renames: Record<string, string>;
}

export const api = {
  appInfo: () => call<{ version: string; os: string; java: string }>('app.info'),
  windowReady: () => call<{ ok: boolean }>('window.ready'),
  setTheme: (theme: Record<string, unknown>) => call<{ ok: boolean }>('window.setTheme', theme),
  setUi: (key: string, value: string | number | boolean) => call<{ ok: boolean }>('settings.setUi', { key, value }),
  readClipboardText: () => call<{ text: string }>('clipboard.readText').then((r) => r.text),
  openExternal: (url: string) => call<{ ok: boolean }>('app.openExternal', { url }),

  types: () => call<EngineType[]>('connections.types'),
  connections: () => call<ConnectionInfo[]>('connections.list'),
  connectionStatus: () => call<{ id: string; alive: boolean; sessions: number }[]>('connections.status'),
  connection: (id: string) => call<ConnectionInfo & { password: string }>('connections.get', { id }),
  saveConnection: (form: ConnectionForm) => call<{ id: string }>('connections.save', { ...form }),
  testConnection: (form: ConnectionForm) => call<{ ok: boolean }>('connections.test', { ...form }),
  deleteConnection: (id: string) => call<{ ok: boolean }>('connections.delete', { id }),
  cloneConnection: (id: string, name: string) => call<{ id: string }>('connections.clone', { id, name }),
  setConnectionFolder: (id: string, folder: string) => call<{ ok: boolean }>('connections.setFolder', { id, folder }),
  setConnectionPinned: (id: string, pinned: boolean) => call<{ ok: boolean }>('connections.setPinned', { id, pinned }),
  folders: () => call<{ folders: string[]; collapsed: string[]; colors: Record<string, string> }>('connections.folders'),
  setFolders: (folders: string[], collapsed: string[], colors: Record<string, string>) => call<{ ok: boolean }>('connections.setFolders', { folders, collapsed, colors }),
  renameFolder: (from: string, to: string) => call<{ ok: boolean }>('connections.renameFolder', { from, to }),
  connect: (id: string, password?: string, remember?: boolean) =>
    call<{ needsPassword?: boolean; ok?: boolean }>('connections.connect', password === undefined ? { id } : { id, password, remember: !!remember }),
  disconnect: (id: string) => call<{ ok: boolean }>('connections.disconnect', { id }),
  reconnect: (id: string) => call<{ ok: boolean }>('connections.reconnect', { id }),
  drivers: (type: string) => call<DriverInfo[]>('drivers.list', { type }),
  downloadDriver: (driver: DriverInfo) => call<{ ok: boolean }>('drivers.download', { ...driver }),

  loadSchema: (connectionId: string) => call<SchemaTree>('schema.load', { connectionId }),
  reloadContainer: (connectionId: string, container: string) => call<TreeContainer>('schema.reload', { connectionId, container }),
  currentContainer: (connectionId: string) => call<{ container: string | null }>('schema.current', { connectionId }),
  useContainer: (connectionId: string, container: string) => call<{ ok: boolean }>('schema.use', { connectionId, container }),
  script: (connectionId: string, container: string, table: string, kind: 'select' | 'create' | 'insert') =>
    call<{ sql: string }>('schema.script', { connectionId, container, table, kind }),
  tableInfo: (connectionId: string, container: string, table: string) => call<TableInfo>('schema.tableInfo', { connectionId, container, table }),
  monitorOpen: (connectionId: string) => call<MonitorOpen>('monitor.open', { connectionId }),
  monitorSample: (connectionId: string, tables: boolean) => call<MonitorSample>('monitor.sample', { connectionId, tables }),
  monitorClose: (connectionId: string) => call<{ ok: boolean }>('monitor.close', { connectionId }),
  diagram: (connectionId: string, container: string) => call<DiagramData>('schema.diagram', { connectionId, container }),
  columnInfo: (connectionId: string, container: string, table: string, column: string) =>
    call<ColumnDetails>('schema.columnInfo', { connectionId, container, table, column }),
  createContainer: (connectionId: string, name: string) => call<{ ok: boolean }>('schema.createContainer', { connectionId, name }),
  dropContainer: (connectionId: string, name: string) => call<{ ok: boolean }>('schema.dropContainer', { connectionId, name }),
  dropTable: (connectionId: string, container: string, table: string) => call<{ ok: boolean }>('schema.dropTable', { connectionId, container, table }),
  describeTable: (connectionId: string, container: string, table: string) =>
    call<TableDescription>('table.describe', { connectionId, container, table }),
  previewTable: (draft: TableDraft) => call<{ sql: string }>('table.preview', { ...draft }),
  executeScript: (connectionId: string, sql: string) => call<{ ok: boolean }>('table.execute', { connectionId, sql }),
  formatSql: (connectionId: string, sql: string) => call<{ sql: string }>('sql.format', { connectionId, sql }),
  splitSql: (connectionId: string, text: string) => call<StatementRange[]>('sql.split', { connectionId, text }),

  run: (p: { tabId: string; connectionId: string; text: string; mode: 'all' | 'selection' | 'statement'; caret?: number; offset?: number }) =>
    call<{ runId: string; count: number }>('query.run', p),
  cancel: (tabId: string) => call<{ ok: boolean }>('query.cancel', { tabId }),
  closeTab: (tabId: string) => call<{ ok: boolean }>('query.closeTab', { tabId }),

  gridEdit: (gridId: string, row: number, column: number, value: string | null) => call<GridPatch>('grid.edit', { gridId, row, column, value }),
  gridCell: (gridId: string, row: number, column: number) => call<{ text: string | null }>('grid.cell', { gridId, row, column }),
  gridAddRow: (gridId: string) => call<GridPatch>('grid.addRow', { gridId }),
  gridDuplicateRow: (gridId: string, row: number) => call<GridPatch>('grid.duplicateRow', { gridId, row }),
  gridDeleteRows: (gridId: string, rows: number[]) => call<GridPatch>('grid.deleteRows', { gridId, rows }),
  gridRevert: (gridId: string) => call<GridSnapshot>('grid.revert', { gridId }),
  gridApply: (gridId: string) => call<GridSnapshot>('grid.apply', { gridId }),
  gridRowScript: (gridId: string, row: number) => call<{ sql: string }>('grid.rowScript', { gridId, row }),
  gridExport: (gridId: string, format: 'csv' | 'json' | 'xml', rows?: number[]) =>
    call<{ path?: string; cancelled?: boolean }>('grid.export', rows ? { gridId, format, rows } : { gridId, format }),
  gridCopy: (gridId: string, rows?: number[]) => call<{ text: string }>('grid.copy', rows ? { gridId, rows } : { gridId }),

  openFile: (connectionId?: string) => call<OpenedFile>('file.open', { connectionId: connectionId ?? '' }),
  openPath: (path: string, connectionId?: string) => call<OpenedFile>('file.openPath', { path, connectionId: connectionId ?? '' }),
  saveFile: (p: { path?: string; text: string; name?: string; saveAs?: boolean; connectionId?: string }) =>
    call<{ path?: string; name?: string; cancelled?: boolean }>('file.save', p),
  pickDatabase: (type: string) => call<{ path?: string; cancelled?: boolean }>('file.pickDatabase', { type }),
  recentFiles: () => call<RecentFile[]>('file.recent'),
  removeRecent: (path: string) => call<{ ok: boolean }>('file.recentRemove', { path }),
  clearRecent: () => call<{ ok: boolean }>('file.recentClear'),
};

export const events = {
  onResult: (cb: (e: QueryResultEvent) => void) => onEvent<QueryResultEvent>('query.result', cb),
  onFinished: (cb: (e: QueryFinishedEvent) => void) => onEvent<QueryFinishedEvent>('query.finished', cb),
  onStarted: (cb: (e: { tabId: string; runId: string; count: number }) => void) => onEvent('query.started', cb),
};
