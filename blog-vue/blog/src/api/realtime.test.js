import { describe, expect, it, vi } from "vitest";
import { createWebSocketClient } from "../../../shared/realtime/createWebSocketClient";

class FakeWebSocket {
  static CONNECTING = 0;
  static OPEN = 1;
  static CLOSED = 3;
  constructor() {
    this.readyState = FakeWebSocket.CONNECTING;
    FakeWebSocket.instances.push(this);
  }
  open() {
    this.readyState = FakeWebSocket.OPEN;
    this.onopen?.({});
  }
  close() {
    this.readyState = FakeWebSocket.CLOSED;
    this.onclose?.({ code: 1000 });
  }
  send() {}
}
FakeWebSocket.instances = [];

describe("shared websocket lifecycle", () => {
  it("deduplicates envelope event IDs and reports malformed messages", () => {
    const onMessage = vi.fn();
    const onProtocolError = vi.fn();
    const client = createWebSocketClient({
      url: "ws://example.test",
      WebSocketImpl: FakeWebSocket,
      onMessage,
      onProtocolError,
      reconnect: false
    });
    client.connect();
    const socket = FakeWebSocket.instances[0];
    socket.open();
    socket.onmessage?.({ data: JSON.stringify({ eventId: "same", type: "CHAT_MESSAGE", data: {} }) });
    socket.onmessage?.({ data: JSON.stringify({ eventId: "same", type: "CHAT_MESSAGE", data: {} }) });
    socket.onmessage?.({ data: "not-json" });

    expect(onMessage).toHaveBeenCalledTimes(1);
    expect(onProtocolError).toHaveBeenCalledTimes(1);
    expect(client.isOpen()).toBe(true);
    client.close();
    expect(client.isConnecting()).toBe(false);
  });
});
