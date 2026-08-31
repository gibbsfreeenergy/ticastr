import { normalizeHttpError } from "../../../../shared/api/error";

const DEFAULT_DEBOUNCE_MS = 2000;

function businessError(message = "文章操作失败") {
  return {
    kind: "request",
    status: 400,
    message,
    retryable: false,
    retryAfterSeconds: null,
    traceId: null
  };
}

function normalizeEditorError(error) {
  if (error?.kind && typeof error.message === "string") return error;
  if (error?.flag === false) return businessError(error.message);
  return normalizeHttpError(error);
}

function responseData(response) {
  return response?.data?.data ?? response?.data ?? null;
}

function responseMarkdown(response) {
  if (typeof response?.data === "string") return response.data;
  if (typeof response?.data?.data === "string") return response.data.data;
  return "";
}

/**
 * Framework-neutral state machine for the admin Markdown editor.
 *
 * The page remains Options API, while timers, optimistic-version handling,
 * request coalescing, and disposal live here so they can be tested without
 * mounting Element Plus.
 */
export function createArticleEditorState({
  api,
  articleId = null,
  version = null,
  debounceMs = DEFAULT_DEBOUNCE_MS,
  onChange = () => {},
  now = () => new Date()
} = {}) {
  if (!api?.article) throw new TypeError("createArticleEditorState requires api.article");

  const state = {
    articleId,
    status: articleId == null ? "ready" : "loading",
    metadata: null,
    markdown: "",
    version,
    lastSavedAt: null,
    dirty: false,
    saveAgain: false,
    saveError: null,
    publishError: null,
    conflict: null
  };

  let disposed = false;
  let timer = null;
  let inFlight = null;
  let loadGeneration = 0;

  function notify() {
    if (!disposed) onChange(state);
  }

  function clearTimer() {
    if (timer !== null) {
      clearTimeout(timer);
      timer = null;
    }
  }

  function scheduleSave() {
    clearTimer();
    if (disposed || state.articleId == null || inFlight) return;
    timer = setTimeout(() => {
      timer = null;
      void saveNow().catch(() => {});
    }, debounceMs);
  }

  function setArticleId(id) {
    state.articleId = id == null ? null : id;
    notify();
  }

  function setMetadata(metadata) {
    state.metadata = metadata || null;
    const metadataVersion = metadata?.contentVersion;
    if (metadataVersion != null && state.version == null) state.version = metadataVersion;
    notify();
  }

  function setMarkdown(markdown) {
    state.markdown = String(markdown ?? "");
    state.dirty = true;
    state.saveError = null;
    state.publishError = null;
    state.conflict = null;
    state.status = "dirty";
    if (inFlight) state.saveAgain = true;
    else scheduleSave();
    notify();
  }

  async function loadContent() {
    if (disposed || state.articleId == null) return state;
    const contentResponse = await api.article.adminContent(state.articleId);
    if (disposed) return state;
    state.markdown = responseMarkdown(contentResponse);
    state.version = contentResponse?.version
      ?? responseData(contentResponse)?.version
      ?? state.version;
    state.status = "ready";
    state.dirty = false;
    state.saveAgain = false;
    state.saveError = null;
    state.conflict = null;
    notify();
    return state;
  }

  async function load(id = state.articleId) {
    const generation = ++loadGeneration;
    clearTimer();
    state.articleId = id == null ? null : id;
    state.status = state.articleId == null ? "ready" : "loading";
    state.metadata = null;
    state.markdown = "";
    state.version = null;
    state.dirty = false;
    state.saveError = null;
    state.conflict = null;
    notify();
    if (state.articleId == null) return state;

    try {
      const metadataResponse = await api.article.adminById(state.articleId);
      if (disposed || generation !== loadGeneration) return state;
      if (metadataResponse?.flag === false || !metadataResponse?.data) {
        throw businessError(metadataResponse?.message || "文章不存在");
      }
      setMetadata(metadataResponse.data);
      state.version = metadataResponse.data.contentVersion ?? state.version;
      if (disposed || generation !== loadGeneration) return state;
      return loadContent();
    } catch (error) {
      const normalized = normalizeEditorError(error);
      if (!disposed && generation === loadGeneration) {
        state.status = normalized.kind === "conflict" ? "conflict" : "error";
        state.saveError = normalized;
        notify();
      }
      throw normalized;
    }
  }

  function saveNow({ force = false } = {}) {
    if (disposed || state.articleId == null) return Promise.resolve(null);
    if (inFlight) {
      state.saveAgain = true;
      return inFlight;
    }
    if (!force && !state.dirty) return Promise.resolve(null);

    clearTimer();
    const markdownAtStart = state.markdown;
    const expectedVersion = state.version;
    state.status = "saving";
    state.saveError = null;
    state.conflict = null;
    notify();

    inFlight = Promise.resolve(api.article.saveContent(state.articleId, {
      content: markdownAtStart,
      expectedVersion
    }))
      .then(response => {
        if (response?.flag === false) throw businessError(response.message);
        if (disposed) return response;
        const saved = responseData(response);
        state.version = saved?.version ?? state.version;
        state.lastSavedAt = now();
        if (state.markdown === markdownAtStart) state.dirty = false;
        state.saveError = null;
        state.conflict = null;
        state.status = state.dirty ? "dirty" : "ready";
        notify();
        return response;
      })
      .catch(error => {
        const normalized = normalizeEditorError(error);
        if (!disposed) {
          state.saveError = normalized;
          state.conflict = normalized.kind === "conflict"
            ? { ...normalized, server: error?.response?.data?.data ?? null }
            : null;
          state.status = normalized.kind === "conflict" ? "conflict" : "error";
          notify();
        }
        throw normalized;
      })
      .finally(() => {
        const shouldSaveAgain = !disposed && state.saveAgain;
        inFlight = null;
        state.saveAgain = false;
        if (shouldSaveAgain) void saveNow({ force: true }).catch(() => {});
      });
    return inFlight;
  }

  function discardLocalDraft() {
    if (!state.conflict?.server) return false;
    const server = state.conflict.server;
    state.markdown = server.content ?? state.markdown;
    state.version = server.version ?? state.version;
    state.dirty = false;
    state.conflict = null;
    state.saveError = null;
    state.status = "ready";
    notify();
    return true;
  }

  function dispose() {
    disposed = true;
    clearTimer();
    loadGeneration += 1;
  }

  return {
    state,
    load,
    loadContent,
    setArticleId,
    setMetadata,
    setMarkdown,
    saveNow,
    discardLocalDraft,
    dispose,
    flush: () => saveNow({ force: true }),
    get disposed() {
      return disposed;
    }
  };
}
