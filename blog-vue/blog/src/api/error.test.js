import { describe, expect, it } from "vitest";
import { normalizeHttpError } from "../../../shared/api/error";

describe("normalized HTTP errors", () => {
  it("keeps backend internals out of user-facing messages", () => {
    const result = normalizeHttpError({
      response: {
        status: 503,
        data: { message: "jdbc:mysql://secret-host/internal stack" },
        headers: { "x-request-id": "req-1" }
      }
    });

    expect(result).toMatchObject({ kind: "server", retryable: true, traceId: "req-1" });
    expect(result.message).not.toContain("secret-host");
  });

  it("exposes Retry-After without exposing response payloads", () => {
    expect(normalizeHttpError({ response: { status: 429, headers: { "retry-after": "12" } } }))
      .toMatchObject({ kind: "rate-limited", retryAfterSeconds: 12, retryable: true });
  });
});
