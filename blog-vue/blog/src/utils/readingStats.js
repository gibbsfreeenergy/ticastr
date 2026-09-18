const CJK_PATTERN = /[\u3040-\u30ff\u3400-\u9fff\uf900-\ufaff]/g;
const LATIN_WORD_PATTERN = /[A-Za-z0-9]+(?:['’\u2011-][A-Za-z0-9]+)*/g;

export function normalizeReadingText(source) {
  return String(source || "").replace(/\s+/g, " ").trim();
}

export function countReadableWords(source) {
  const text = normalizeReadingText(source);
  if (!text) return 0;

  const cjkCharacters = text.match(CJK_PATTERN) || [];
  const latinWords = text.match(LATIN_WORD_PATTERN) || [];
  return cjkCharacters.length + latinWords.length;
}

export function formatReadingTime(wordCount, wordsPerMinute = 450) {
  const count = Number(wordCount) || 0;
  return Math.max(1, Math.ceil(count / wordsPerMinute)) + "分钟";
}
