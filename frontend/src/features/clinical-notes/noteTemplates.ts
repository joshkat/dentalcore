/** Pure helpers for {{placeholder}} note templates — kept dependency-free for tests. */

const PLACEHOLDER = /\{\{\s*([\w.-]+)\s*\}\}/g;

/** Client-side preview of the prompts the server extracts from a template body. */
export function extractPrompts(body: string): string[] {
  const keys: string[] = [];
  for (const match of body.matchAll(PLACEHOLDER)) {
    if (!keys.includes(match[1])) keys.push(match[1]);
  }
  return keys;
}

/**
 * Replace each {{key}} with the entered value. Placeholders the user left
 * blank stay visible so nothing silently disappears from the note.
 */
export function interpolateTemplate(body: string, values: Record<string, string>): string {
  return body.replace(PLACEHOLDER, (match, key: string) => {
    const value = values[key];
    return value !== undefined && value.trim() !== '' ? value : match;
  });
}
