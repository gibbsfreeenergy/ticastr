import { describe, expect, it, vi } from "vitest";
import { createChatState } from "./chatState";

function createSocketHarness() {
  let handlers;
  const socket = {
    connect: vi.fn(),
    close: vi.fn(),
    send: vi.fn(() => true)
  };
  const factory = vi.fn(options => {
    handlers = options;
    return socket;
  });
  return { factory, socket, handlers: () => handlers };
}

describe("chat state", () => {
  it("merges history and events without duplicate records", () => {
    const harness = createSocketHarness();
    const chat = createChatState({ websocketFactory: harness.factory, clientId: "client" });
    chat.connect("wss://example.test/websocket");
    harness.handlers().onMessage({
      data: JSON.stringify({
        type: 2,
        eventId: "history-1",
        data: { chatRecordList: [{ id: 1, content: "old" }], hasMore: true, nextBeforeId: 1 }
      })
    });
    harness.handlers().onMessage({
      data: JSON.stringify({
        type: 3,
        eventId: "message-1",
        data: { id: 2, content: "new", type: 3 }
      })
    });
    harness.handlers().onMessage({
      data: JSON.stringify({
        type: 3,
        eventId: "message-2",
        data: { id: 2, content: "new", type: 3 }
      })
    });

    expect(chat.state.messages.map(item => item.id)).toEqual([1, 2]);
  });

  it("notifies unread listeners only for a newly appended record", () => {
    const harness = createSocketHarness();
    const onMessage = vi.fn();
    const chat = createChatState({
      websocketFactory: harness.factory,
      clientId: "client",
      onMessage
    });
    chat.connect("wss://example.test/websocket");

    const event = content => ({
      data: JSON.stringify({
        type: 3,
        eventId: `message-${content}`,
        data: { id: 2, content, type: 3 }
      })
    });
    harness.handlers().onMessage(event("new"));
    harness.handlers().onMessage(event("duplicate"));

    expect(onMessage).toHaveBeenCalledTimes(1);
    expect(chat.state.messages).toHaveLength(1);
  });

  it("keeps a failed send retryable and clears it once the ACK arrives", () => {
    const harness = createSocketHarness();
    const chat = createChatState({ websocketFactory: harness.factory, clientId: "client" });
    chat.connect("wss://example.test/websocket");

    expect(chat.sendText("hello")).toBe(true);
    const payload = harness.socket.send.mock.calls[0][0];
    expect(payload.clientMessageId).toBeTruthy();
    expect(chat.state.pendingMessages).toHaveLength(1);

    harness.handlers().onMessage({
      data: JSON.stringify({
        type: 7,
        eventId: "ack-1",
        clientMessageId: payload.clientMessageId,
        messageId: 8,
        data: { clientMessageId: payload.clientMessageId, messageId: 8 }
      })
    });

    expect(chat.state.pendingMessages).toHaveLength(0);
    expect(chat.state.messages).toContainEqual(expect.objectContaining({
      id: 8,
      content: "hello"
    }));
  });

  it("reports malformed protocol frames without throwing", () => {
    const harness = createSocketHarness();
    const onProtocolError = vi.fn();
    const chat = createChatState({
      websocketFactory: harness.factory,
      clientId: "client",
      onProtocolError
    });
    chat.connect("wss://example.test/websocket");

    expect(() => harness.handlers().onMessage({ data: "not-json" })).not.toThrow();
    expect(onProtocolError).toHaveBeenCalledWith(expect.objectContaining({ reason: "invalid-json" }));
  });
});
