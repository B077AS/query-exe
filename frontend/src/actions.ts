import { api, type ConnectionInfo } from './api';
import { app, disconnect, reloadConnections } from './store';
import { askFolder, confirm, fail, prompt, toast } from './ui';

// What the connections tree can do with folders and saved connections.

export async function saveFolders(): Promise<void> {
  await api.setFolders(app.folders, app.collapsedFolders, app.folderColors).catch((e) => fail('Could not save folders', e));
}

// ---- order ---------------------------------------------------------------------------------------------------------
// Alphabetical until the user drags something; after that the dragged arrangement (app.order / app.folderOrder) leads and
// anything it does not mention (a new connection) follows alphabetically. Pinned connections always stay on top.

const rank = (list: string[], key: string): number => {
  const i = list.indexOf(key);
  return i < 0 ? Infinity : i;
};

export function compareConnections(a: ConnectionInfo, b: ConnectionInfo): number {
  return Number(b.pinned) - Number(a.pinned) || rank(app.order, a.id) - rank(app.order, b.id) || a.name.localeCompare(b.name);
}

export function compareFolders(a: string, b: string): number {
  return rank(app.folderOrder, a) - rank(app.folderOrder, b) || a.localeCompare(b);
}

export function folderNames(): string[] {
  return [...new Set([...app.folders, ...app.connections.map((c) => c.folder).filter((f): f is string => !!f)])].sort(compareFolders);
}

export type DropPos = 'before' | 'after' | 'into';

async function saveOrder(): Promise<void> {
  await api.setOrder(app.order, app.folderOrder).catch((e) => fail('Could not save the order', e));
}

/** Puts `item` before or after `anchor` in `list` (or last when there is no anchor). */
function place(list: string[], item: string, anchor: string | null, pos: DropPos): string[] {
  const rest = list.filter((x) => x !== item);
  const at = anchor === null ? -1 : rest.indexOf(anchor);
  if (at < 0) return [...rest, item];
  rest.splice(pos === 'after' ? at + 1 : at, 0, item);
  return rest;
}

/** Drops a connection next to another one (same folder), into a folder, or (no target) out of every folder. */
export async function dropConnection(id: string, target: { connection?: string; folder?: string }, pos: DropPos): Promise<void> {
  const info = app.connections.find((c) => c.id === id);
  if (!info || target.connection === id) return;
  const anchor = target.connection ? app.connections.find((c) => c.id === target.connection) : undefined;
  const folder = anchor ? anchor.folder ?? '' : target.folder ?? '';
  const ids = [...app.connections].sort(compareConnections).map((c) => c.id);
  app.order = place(ids, id, anchor ? anchor.id : null, pos);
  try {
    if ((info.folder ?? '') !== folder) await api.setConnectionFolder(id, folder);
    await api.setOrder(app.order, app.folderOrder);
    await reloadConnections();
  } catch (e) {
    fail('Could not move the connection', e);
  }
}

export async function dropFolder(name: string, targetName: string, pos: DropPos): Promise<void> {
  if (name === targetName) return;
  app.folderOrder = place(folderNames(), name, targetName, pos);
  await saveOrder();
}

export function hasCustomOrder(): boolean {
  return app.order.length > 0 || app.folderOrder.length > 0;
}

export async function resetOrder(): Promise<void> {
  app.order = [];
  app.folderOrder = [];
  await saveOrder();
}

export function toggleFolder(name: string): void {
  const i = app.collapsedFolders.indexOf(name);
  if (i >= 0) app.collapsedFolders.splice(i, 1);
  else app.collapsedFolders.push(name);
  void saveFolders();
}

/** Gives a folder an accent colour (a key from accents.ts), or back its default one with null. */
export async function setFolderColor(name: string, color: string | null): Promise<void> {
  if (color) app.folderColors[name] = color;
  else delete app.folderColors[name];
  await saveFolders();
}

export async function newFolder(): Promise<string | null> {
  const answer = await askFolder();
  if (!answer) return null;
  const { name, color } = answer;
  if (!app.folders.includes(name)) app.folders.push(name);
  if (color) app.folderColors[name] = color;
  await saveFolders();
  return name;
}

export async function renameFolder(name: string): Promise<void> {
  const to = await prompt('Rename folder', 'Folder name', name, 'Rename');
  if (!to || to === name) return;
  try {
    await api.renameFolder(name, to);
    app.folders = app.folders.map((f) => (f === name ? to : f));
    app.collapsedFolders = app.collapsedFolders.map((f) => (f === name ? to : f));
    app.folderOrder = app.folderOrder.map((f) => (f === name ? to : f));
    await saveOrder();
    if (app.folderColors[name]) {
      app.folderColors[to] = app.folderColors[name];
      delete app.folderColors[name];
    }
    await saveFolders();
    await reloadConnections();
  } catch (e) {
    fail('Could not rename the folder', e);
  }
}

export async function removeFolder(name: string): Promise<void> {
  if (!(await confirm('Delete folder', `The connections in "${name}" are kept and moved out of the folder.`, 'Delete folder', true))) return;
  try {
    await api.renameFolder(name, '');
    app.folders = app.folders.filter((f) => f !== name);
    delete app.folderColors[name];
    await saveFolders();
    await reloadConnections();
  } catch (e) {
    fail('Could not delete the folder', e);
  }
}

export async function moveConnection(info: ConnectionInfo, folder: string): Promise<void> {
  try {
    await api.setConnectionFolder(info.id, folder);
    await reloadConnections();
  } catch (e) {
    fail('Could not move the connection', e);
  }
}

export async function moveToNewFolder(info: ConnectionInfo): Promise<void> {
  const name = await newFolder();
  if (name) await moveConnection(info, name);
}

export async function togglePin(info: ConnectionInfo): Promise<void> {
  try {
    await api.setConnectionPinned(info.id, !info.pinned);
    await reloadConnections();
  } catch (e) {
    fail('Could not pin the connection', e);
  }
}

export async function duplicateConnection(info: ConnectionInfo): Promise<void> {
  const name = await prompt('Duplicate connection', 'Name of the copy', info.name + ' (copy)', 'Duplicate');
  if (!name) return;
  try {
    await api.cloneConnection(info.id, name);
    await reloadConnections();
  } catch (e) {
    fail('Could not duplicate the connection', e);
  }
}

export async function removeConnection(info: ConnectionInfo): Promise<void> {
  if (!(await confirm('Delete connection', `"${info.name}" and its saved password will be removed. The database itself is not touched.`, 'Delete', true))) return;
  try {
    await api.deleteConnection(info.id);
    if (app.sessions[info.id]) await disconnect(info.id);
    await reloadConnections();
    toast('ok', 'Connection deleted');
  } catch (e) {
    fail('Could not delete the connection', e);
  }
}
