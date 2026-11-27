// Pretty-printing of JSON and XML cell values. Both only add or remove whitespace: the data itself is never rebuilt from a
// parsed form, which would round big numbers, turn 1.0 into 1, drop duplicate keys or rewrite entities.

export type ValueKind = 'json' | 'xml' | 'text';

function isJson(text: string): boolean {
  try {
    JSON.parse(text);
    return true;
  } catch {
    return false;
  }
}

function isXml(text: string): boolean {
  return new DOMParser().parseFromString(text, 'application/xml').getElementsByTagName('parsererror').length === 0;
}

/** Whether the text is well formed for its kind. */
export const isValid = (kind: ValueKind, text: string): boolean => (kind === 'json' ? isJson(text) : kind === 'xml' ? isXml(text) : true);

/**
 * What a value holds. JSON and XML are often kept in plain text columns, so the content decides; the column type only matters for
 * values that give no hint (NULL, a scalar in a JSON column).
 */
export function detectKind(text: string | null, typeName: string): ValueKind {
  const type = typeName.toLowerCase();
  const typed: ValueKind = type.includes('json') ? 'json' : type.includes('xml') ? 'xml' : 'text';
  const start = text?.trimStart()[0];
  if (start === undefined) return typed;
  if ((start === '{' || start === '[' || typed === 'json') && isJson(text!)) return 'json';
  if (start === '<' && isXml(text!)) return 'xml';
  return typed;
}

// ---- JSON ---------------------------------------------------------------------------------------------------------

/** The tokens of valid JSON text, each exactly as written. */
const jsonTokens = (text: string): string[] => text.match(/"(?:\\[\s\S]|[^"\\])*"|[{}[\]:,]|[^\s{}[\]:,"]+/g) ?? [];

export function formatJson(text: string): string {
  const tokens = jsonTokens(text);
  const out: string[] = [];
  let depth = 0;
  const line = (): string => '\n' + '  '.repeat(depth);
  for (let i = 0; i < tokens.length; i++) {
    const token = tokens[i];
    if (token === '{' || token === '[') {
      const close = token === '{' ? '}' : ']';
      if (tokens[i + 1] === close) {
        out.push(token, close);
        i++;
      } else {
        depth++;
        out.push(token, line());
      }
    } else if (token === '}' || token === ']') {
      depth--;
      out.push(line(), token);
    } else if (token === ',') {
      out.push(token, line());
    } else if (token === ':') {
      out.push(': ');
    } else {
      out.push(token);
    }
  }
  return out.join('');
}

export const compactJson = (text: string): string => jsonTokens(text).join('');

// ---- XML ----------------------------------------------------------------------------------------------------------

interface XmlNode {
  kind: 'element' | 'text' | 'cdata' | 'other';
  /** Where the node is in the source text. */
  from: number;
  to: number;
  open: string;
  close: string;
  children: XmlNode[];
}

const XML_TOKEN = /<!--[\s\S]*?-->|<!\[CDATA\[[\s\S]*?\]\]>|<\?[\s\S]*?\?>|<!DOCTYPE(?:[^[>]|\[[\s\S]*?\])*>|<(?:"[^"]*"|'[^']*'|[^'">])*>|[^<]+/g;

/** The nodes of well-formed XML text. */
function parseXml(text: string): XmlNode[] {
  const root: XmlNode = { kind: 'element', from: 0, to: text.length, open: '', close: '', children: [] };
  const stack = [root];
  for (const match of text.matchAll(XML_TOKEN)) {
    const token = match[0];
    const from = match.index;
    const to = from + token.length;
    if (token.startsWith('</')) {
      const element = stack.pop()!;
      element.close = token;
      element.to = to;
      continue;
    }
    const kind = !token.startsWith('<') ? 'text' : token.startsWith('<![CDATA[') ? 'cdata' : token[1] === '!' || token[1] === '?' ? 'other' : 'element';
    const node: XmlNode = { kind, from, to, open: token, close: '', children: [] };
    stack[stack.length - 1].children.push(node);
    if (kind === 'element' && !token.endsWith('/>')) stack.push(node);
  }
  return root.children;
}

const isIndentation = (node: XmlNode): boolean => node.kind === 'text' && node.open.trim() === '' && node.open.includes('\n');

/**
 * Whether the whitespace between an element's children is only layout, so it may be changed: it holds other nodes, and between
 * them nothing but line breaks and indentation. Anything else (text, CDATA, a lone space) is content.
 */
function isContainer(node: XmlNode): boolean {
  if (/\sxml:space\s*=\s*(["'])preserve\1/.test(node.open)) return false;
  return node.children.some((c) => c.kind === 'element' || c.kind === 'other') && node.children.every((c) => c.kind === 'element' || c.kind === 'other' || isIndentation(c));
}

export function formatXml(text: string): string {
  const lines: string[] = [];
  const write = (node: XmlNode, depth: number): void => {
    const pad = '  '.repeat(depth);
    if (node.kind === 'element' && isContainer(node)) {
      lines.push(pad + node.open);
      for (const child of node.children) if (!isIndentation(child)) write(child, depth + 1);
      lines.push(pad + node.close);
    } else {
      lines.push(pad + text.slice(node.from, node.to));
    }
  };
  for (const node of parseXml(text)) if (node.kind !== 'text' || node.open.trim() !== '') write(node, 0);
  return lines.join('\n');
}

/** Removes the layout whitespace, undoing {@link formatXml}. */
export function compactXml(text: string): string {
  const write = (node: XmlNode): string => {
    if (node.kind !== 'element' || !isContainer(node)) return text.slice(node.from, node.to);
    return node.open + node.children.filter((c) => !isIndentation(c)).map(write).join('') + node.close;
  };
  return parseXml(text).filter((n) => n.kind !== 'text' || n.open.trim() !== '').map(write).join('');
}

// ---- either -------------------------------------------------------------------------------------------------------

/** The value indented for reading; text that is not well formed comes back unchanged. */
export function pretty(kind: ValueKind, text: string): string {
  if (kind === 'text' || !isValid(kind, text)) return text;
  return kind === 'json' ? formatJson(text) : formatXml(text);
}

/** The value on as few lines as its content allows; text that is not well formed comes back unchanged. */
export function compact(kind: ValueKind, text: string): string {
  if (kind === 'text' || !isValid(kind, text)) return text;
  return kind === 'json' ? compactJson(text) : compactXml(text);
}
