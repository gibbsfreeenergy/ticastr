import { canReconnect, nextReconnectDelay } from "./reconnectPolicy";

/**
 * Lifecycle-safe WebSocket adapter shared by the public app.
 * Reconnects are bounded by policy and stale socket callbacks are ignored.
 */
export function createWebSocketClient({
  url,
  heartbeatMs = 30000,
  heartbeatPayload = { type: 6, data: "ping" },
  reconnect = true,
  maxReconnectAttempts = 8,
  baseReconnectDelayMs = 1000,
  maxReconnectDelayMs = 30000,
  jitterRatio = 0.2,
  eventDedupTtlMs = 5 * 60 * 1000,
  WebSocketImpl = globalThis.WebSocket,
  onOpen,
  onMessage,
  onError,
  onClose,
  onReconnectAttempt,
  onReconnectScheduled,
  onProtocolError
}) {
  if (!url) throw new TypeError("createWebSocketClient requires a url");
  if (typeof WebSocketImpl !== "function") {
    throw new TypeError("createWebSocketClient requires WebSocket support");
  }

  let socket = null;
  let heartbeatTimer = null;
  let reconnectTimer = null;
  let closedByCaller = false;
  let reconnectAttempt = 0;
  let connectionGeneration = 0;
  const seenEventIds = new Map();

  const clearHeartbeat = () => {
    if (heartbeatTimer) {
      clearInterval(heartbeatTimer);
      heartbeatTimer = null;
    }
  };

  const clearReconnect = () => {
    if (reconnectTimer) {
      clearTimeout(reconnectTimer);
      reconnectTimer = null;
    }
  };

  const send = payload => {
    if (!socket || socket.readyState !== WebSocketImpl.OPEN) {
      return false;
    }
    socket.send(typeof payload === "string" ? payload : JSON.stringify(payload));
    return true;
  };

  const scheduleReconnect = generation => {
    if (closedByCaller || !reconnect || generation !== connectionGeneration
      || !canReconnect(reconnectAttempt, maxReconnectAttempts)) {
      return;
    }
    clearReconnect();
    const attempt = reconnectAttempt;
    const delay = nextReconnectDelay(attempt, {
      baseDelayMs: baseReconnectDelayMs,
      maxDelayMs: maxReconnectDelayMs,
      jitterRatio
    });
    reconnectAttempt += 1;
    const details = { attempt: attempt + 1, delay };
    onReconnectAttempt?.(details);
    onReconnectScheduled?.(details);
    reconnectTimer = setTimeout(() => {
      reconnectTimer = null;
      connect();
    }, delay);
  };

  const connect = () => {
    closedByCaller = false;
    clearReconnect();
    clearHeartbeat();
    if (socket && (socket.readyState === WebSocketImpl.OPEN || socket.readyState === WebSocketImpl.CONNECTING)) {
      return socket;
    }
    const generation = ++connectionGeneration;
    const nextSocket = new WebSocketImpl(url);
    socket = nextSocket;
    nextSocket.onopen = event => {
      if (generation !== connectionGeneration || socket !== nextSocket) return;
      reconnectAttempt = 0;
      heartbeatTimer = setInterval(() => {
        if (!send(heartbeatPayload)) clearHeartbeat();
      }, heartbeatMs);
      onOpen?.(event);
    };
    nextSocket.onmessage = event => {
      if (generation !== connectionGeneration || socket !== nextSocket) return;
      let envelope;
      try {
        envelope = typeof event?.data === "string" ? JSON.parse(event.data) : event?.data;
      } catch (exception) {
        onProtocolError?.({ event, error: exception, reason: "invalid-json" });
        return;
      }
      if (!envelope || typeof envelope !== "object") {
        onProtocolError?.({ event, reason: "invalid-envelope" });
        return;
      }
      const eventId = typeof envelope.eventId === "string" ? envelope.eventId : "";
      if (eventId) {
        const now = Date.now();
        for (const [knownId, seenAt] of seenEventIds) {
          if (now - seenAt > eventDedupTtlMs) seenEventIds.delete(knownId);
          else break;
        }
        if (seenEventIds.has(eventId)) return;
        seenEventIds.set(eventId, now);
        while (seenEventIds.size > 500) seenEventIds.delete(seenEventIds.keys().next().value);
      }
      onMessage?.(event);
    };
    nextSocket.onerror = event => {
      if (generation === connectionGeneration && socket === nextSocket) onError?.(event);
    };
    nextSocket.onclose = event => {
      if (generation !== connectionGeneration || socket !== nextSocket) return;
      clearHeartbeat();
      socket = null;
      const reconnecting = !closedByCaller && reconnect
        && canReconnect(reconnectAttempt, maxReconnectAttempts);
      onClose?.({ event, intentional: closedByCaller, reconnecting });
      scheduleReconnect(generation);
    };
    return nextSocket;
  };

  const close = (code = 1000, reason = "component-unmounted") => {
    closedByCaller = true;
    clearHeartbeat();
    clearReconnect();
    connectionGeneration += 1;
    const current = socket;
    socket = null;
    if (current && (current.readyState === WebSocketImpl.OPEN || current.readyState === WebSocketImpl.CONNECTING)) {
      current.close(code, reason);
    }
  };

  return {
    connect,
    send,
    close,
    isOpen: () => Boolean(socket && socket.readyState === WebSocketImpl.OPEN),
    isConnecting: () => Boolean(socket && socket.readyState === WebSocketImpl.CONNECTING),
    getReconnectAttempt: () => reconnectAttempt,
    get reconnectAttempt() {
      return reconnectAttempt;
    },
    get rawSocket() {
      return socket;
    }
  };
}
