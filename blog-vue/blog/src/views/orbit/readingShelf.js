export const READING_SHELF_STORAGE_KEY = "ticastr:reading-shelf";

function getDefaultStorage() {
  try {
    return typeof globalThis === "undefined" ? null : globalThis.localStorage;
  } catch {
    return null;
  }
}

export function normalizeShelfIds(value) {
  if (!Array.isArray(value)) return [];
  return [...new Set(value.map(id => String(id ?? "").trim()).filter(Boolean))];
}

export function toggleShelfId(ids, id) {
  const normalized = normalizeShelfIds(ids);
  const key = String(id ?? "").trim();
  if (!key) return normalized;
  return normalized.includes(key)
    ? normalized.filter(item => item !== key)
    : [...normalized, key];
}

export function getShelfArticles(articles, savedIds) {
  const byId = new Map(
    (Array.isArray(articles) ? articles : []).map(article => [String(article?.id), article])
  );
  return normalizeShelfIds(savedIds)
    .map(id => byId.get(id))
    .filter(Boolean);
}

export function readShelfIds(storage = getDefaultStorage()) {
  try {
    const value = storage?.getItem(READING_SHELF_STORAGE_KEY);
    return normalizeShelfIds(value ? JSON.parse(value) : []);
  } catch {
    return [];
  }
}

export function writeShelfIds(ids, storage = getDefaultStorage()) {
  const normalized = normalizeShelfIds(ids);
  try {
    storage?.setItem(READING_SHELF_STORAGE_KEY, JSON.stringify(normalized));
  } catch {
    // Storage can be unavailable in private browsing or during server rendering.
  }
  return normalized;
}
