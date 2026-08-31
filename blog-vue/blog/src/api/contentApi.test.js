import { describe, expect, it, vi } from "vitest";
import { createContentApi } from "../../../shared/api/contentApi";

describe("content API cache contract", () => {
  it("sends validators and reuses the body on 304", async () => {
    const client = { get: vi.fn() };
    client.get
      .mockResolvedValueOnce({
        status: 200,
        data: "# first",
        headers: { etag: '"asset-1"', "last-modified": "Wed, 01 Jan 2025 00:00:00 GMT" }
      })
      .mockResolvedValueOnce({
        status: 304,
        data: "",
        headers: { etag: '"asset-1"' }
      });
    const api = createContentApi(client);

    await expect(api.public(7)).resolves.toMatchObject({ data: "# first", notModified: false });
    await expect(api.public(7)).resolves.toMatchObject({ data: "# first", notModified: true });
    expect(client.get.mock.calls[1][1].headers["If-None-Match"]).toBe('"asset-1"');
  });

  it("does not accept an empty 304 cache hit", async () => {
    const client = { get: vi.fn() };
    client.get
      .mockResolvedValueOnce({ status: 304, data: "", headers: {} })
      .mockResolvedValueOnce({ status: 200, data: "# recovered", headers: {} });
    const api = createContentApi(client);

    await expect(api.public(8)).resolves.toMatchObject({ data: "# recovered" });
    expect(client.get).toHaveBeenCalledTimes(2);
  });
});
