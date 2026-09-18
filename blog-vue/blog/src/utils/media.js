export function normalizeMediaUrl(value) {
  if (typeof value !== "string") return "";

  const reference = value.trim();
  if (!reference) return "";
  if (typeof window === "undefined") return reference;
  if (reference.startsWith("//")) return `${window.location.protocol}${reference}`;
  if (window.location.protocol === "https:" && /^http:\/\//i.test(reference)) {
    return reference.replace(/^http:\/\//i, "https://");
  }
  return reference;
}
