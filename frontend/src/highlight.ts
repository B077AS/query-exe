import { HighlightStyle } from '@codemirror/language';
import { tags as t } from '@lezer/highlight';

/** Syntax colors shared by the code editors (SQL, JSON, XML). */
export const highlight = HighlightStyle.define([
  { tag: [t.keyword, t.operatorKeyword, t.modifier], color: 'var(--syn-keyword)', fontWeight: '600' },
  { tag: [t.string, t.special(t.string), t.attributeValue], color: 'var(--syn-string)' },
  { tag: [t.number, t.bool, t.null], color: 'var(--syn-number)' },
  { tag: [t.comment, t.lineComment, t.blockComment], color: 'var(--syn-comment)', fontStyle: 'italic' },
  { tag: [t.function(t.variableName), t.standard(t.name), t.propertyName, t.attributeName], color: 'var(--syn-function)' },
  { tag: [t.typeName, t.className, t.tagName], color: 'var(--syn-type)' },
  { tag: [t.operator, t.punctuation, t.angleBracket], color: 'var(--text-2)' },
]);
