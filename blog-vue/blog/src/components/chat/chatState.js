import { createWebSocketClient } from "../../../../shared/realtime/createWebSocketClient";

const PROTOCOL_VERSION = 1;
const HISTORY_LIMIT = 100;

function createId() {
  return globalThis.crypto?.randomUUID?.()
    || "chat-" + Date.now() + "-" + Math.random().toString(36).slice(2);
}

function messageKey(message) {
  const id = message?.id ?? message?.messageId ?? message?.clientMessageId;
  return id == null ? null : String(id);
}

function parseEnvelope(event, onProtocolError) {
  try {
    const envelope = typeof event?.data === "string" ? JSON.parse(event.data) : event?.data;
    if (!envelope || typeof envelope !== "object") throw new Error("invalid-envelope");
    return envelope;
  } catch (error) {
    onProtocolError?.({ event, error, reason: error.message === "invalid-envelope" ? "invalid-envelope" : "invalid-json" });
    return null;
  }
}

export function createChatState({
  websocketFactory = createWebSocketClient,
  clientId,
  onChange = () => {},
  onMessage = () => {},
  onProtocolError
} = {}) {
  const state = {
    status: "offline",
    onlineCount: 0,
    messages: [],
    pendingMessages: [],
    historyLoading: false,
    hasMore: false,
    beforeId: null,
    error: null
  };
  let socket = null;
  let disposed = false;

  function notify() {
    if (!disposed) onChange(state);
  }

  function mergeMessages(incoming, prepend = false) {
    const previousKeys = new Set(state.messages.map(messageKey).filter(key => key !== null));
    const merged = new Map();
    let added = 0;
    (prepend ? incoming.concat(state.messages) : state.messages.concat(incoming))
      .filter(item => item && messageKey(item) !== null)
      .forEach(item => {
        const key = messageKey(item);
        if (!merged.has(key)) {
          merged.set(key, item);
          if (!previousKeys.has(key)) added += 1;
        } else if (item.id != null) {
          merged.set(key, item);
        }
      });
    state.messages = [...merged.values()].sort((left, right) => {
      const leftId = Number(left.id);
      const rightId = Number(right.id);
      if (Number.isFinite(leftId) && Number.isFinite(rightId)) return leftId - rightId;
      return String(left.createTime || "").localeCompare(String(right.createTime || ""));
    });
    return added;
  }

  function appendMessage(message) {
    if (!message || messageKey(message) === null) return;
    const added = mergeMessages([message]);
    const pendingIndex = state.pendingMessages.findIndex(item =>
      item.clientMessageId === message.clientMessageId || item.id === message.id);
    if (pendingIndex >= 0) state.pendingMessages.splice(pendingIndex, 1);
    if (added > 0) onMessage(message);
    notify();
  }

  function markPendingFailed(pending, message = "消息发送失败，请重试") {
    pending.status = "failed";
    pending.error = message;
    state.error = message;
    notify();
  }

  function sendPending(pending) {
    if (!socket) {
      markPendingFailed(pending, "聊天室尚未连接");
      return false;
    }
    try {
      const sent = socket.send({
        type: 3,
        version: PROTOCOL_VERSION,
        eventId: createId(),
        clientMessageId: pending.clientMessageId,
        data: { type: 3, content: pending.content }
      });
      if (!sent) {
        markPendingFailed(pending);
        return false;
      }
      pending.status = "pending";
      pending.error = null;
      state.error = null;
      notify();
      return true;
    } catch {
      markPendingFailed(pending);
      return false;
    }
  }

  function handleEnvelope(envelope) {
    if (envelope.version != null && envelope.version !== PROTOCOL_VERSION) {
      state.error = "聊天室协议版本不兼容";
      notify();
      return;
    }
    const data = envelope.data;
    switch (envelope.type) {
      case 1:
        state.onlineCount = Number(data) || 0;
        notify();
        break;
      case 2: {
        const history = data || {};
        mergeMessages(Array.isArray(history.chatRecordList) ? history.chatRecordList : [], true);
        state.hasMore = Boolean(history.hasMore);
        state.beforeId = history.nextBeforeId || null;
        state.historyLoading = false;
        notify();
        break;
      }
      case 3:
      case 5:
        appendMessage(data);
        break;
      case 4:
        if (data?.id != null) {
          state.messages = state.messages.filter(item => item.id !== data.id);
          notify();
        }
        break;
      case 7: {
        const clientMessageId = envelope.clientMessageId || data?.clientMessageId;
        const messageId = envelope.messageId || data?.messageId;
        const index = state.pendingMessages.findIndex(item => item.clientMessageId === clientMessageId);
        if (index < 0) break;
        const pending = state.pendingMessages[index];
        state.pendingMessages.splice(index, 1);
        mergeMessages([{
          id: messageId,
          clientMessageId,
          content: pending.content,
          type: pending.type,
          owner: true,
          createTime: new Date().toISOString()
        }]);
        notify();
        break;
      }
      case 0:
        markPendingFailed(
          state.pendingMessages.find(item => item.clientMessageId === data?.clientMessageId)
            || { status: "failed" },
          "消息未被服务器接受"
        );
        break;
      default:
        break;
    }
  }

  function handleMessage(event) {
    const envelope = parseEnvelope(event, onProtocolError);
    if (envelope) handleEnvelope(envelope);
  }

  function connect(url, options = {}) {
    if (disposed) return null;
    if (socket) return socket;
    state.status = "connecting";
    state.error = null;
    const current = websocketFactory({
      url,
      ...options,
      onOpen: event => {
        state.status = "online";
        state.error = null;
        notify();
        state.pendingMessages.filter(item => item.status === "pending").forEach(sendPending);
        options.onOpen?.(event);
      },
      onMessage: event => {
        handleMessage(event);
        options.onMessage?.(event);
      },
      onError: event => {
        state.error = "聊天室连接失败，请稍后重试";
        notify();
        options.onError?.(event);
      },
      onReconnectAttempt: details => {
        state.status = "reconnecting";
        notify();
        options.onReconnectAttempt?.(details);
      },
      onReconnectScheduled: details => {
        state.status = "reconnecting";
        notify();
        options.onReconnectScheduled?.(details);
      },
      onProtocolError: details => {
        onProtocolError?.(details);
        options.onProtocolError?.(details);
      },
      onClose: details => {
        if (socket !== current) return;
        state.status = details.intentional ? "offline" : details.reconnecting ? "reconnecting" : "offline";
        notify();
        options.onClose?.(details);
      }
    });
    socket = current;
    current.connect();
    return current;
  }

  function close() {
    disposed = true;
    if (socket) socket.close();
    socket = null;
    state.status = "offline";
    notify();
  }

  function sendText(content) {
    const value = String(content || "").trim();
    if (!value) return false;
    const pending = {
      clientMessageId: createId(),
      content: value,
      type: 3,
      status: "pending",
      error: null
    };
    state.pendingMessages.push(pending);
    notify();
    return sendPending(pending);
  }

  function retry(clientMessageId) {
    const pending = state.pendingMessages.find(item => item.clientMessageId === clientMessageId);
    return pending ? sendPending(pending) : false;
  }

  function requestHistory() {
    if (!socket) return false;
    state.historyLoading = true;
    const sent = socket.send({
      type: 8,
      version: PROTOCOL_VERSION,
      eventId: createId(),
      data: { beforeId: state.beforeId, limit: HISTORY_LIMIT }
    });
    if (!sent) state.historyLoading = false;
    notify();
    return sent;
  }

  function recall(messageId, isVoice = false) {
    if (!socket || messageId == null) return false;
    return socket.send({
      type: 4,
      version: PROTOCOL_VERSION,
      eventId: createId(),
      data: { id: messageId, isVoice }
    });
  }

  return {
    state,
    connect,
    close,
    sendText,
    retry,
    requestHistory,
    recall,
    isOpen: () => Boolean(socket?.isOpen?.()),
    get clientId() {
      return clientId;
    }
  };
}
