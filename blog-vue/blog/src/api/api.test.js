import { describe, expect, it, vi } from "vitest";
import { createApi } from "../../../shared/api/createApi";

function createClient() {
  const calls = [];
  const response = { data: { flag: true, code: 200, message: "ok", data: { id: 7 } } };
  const client = {
    get: vi.fn((url, config) => {
      calls.push({ method: "get", url, config });
      return Promise.resolve(response);
    }),
    post: vi.fn((url, data, config) => {
      calls.push({ method: "post", url, data, config });
      return Promise.resolve(response);
    }),
    put: vi.fn((url, data, config) => {
      calls.push({ method: "put", url, data, config });
      return Promise.resolve(response);
    }),
    delete: vi.fn((url, config) => {
      calls.push({ method: "delete", url, config });
      return Promise.resolve(response);
    })
  };
  return { client, calls };
}

describe("shared API boundary", () => {
  it("returns the business envelope instead of an Axios response", async () => {
    const { client } = createClient();
    const api = createApi(client);

    await expect(api.article.adminById(7)).resolves.toEqual({
      flag: true,
      code: 200,
      message: "ok",
      data: { id: 7 }
    });
  });

  it("keeps endpoint and transport details in domain methods", async () => {
    const { client, calls } = createClient();
    const api = createApi(client);

    await api.public.home();
    await api.article.archives({ params: { size: 20 } });
    await api.article.search({ params: { keywords: "vue" } });

    expect(calls).toEqual([
      { method: "get", url: "/api/", config: undefined },
      { method: "get", url: "/api/articles/archives", config: { params: { size: 20 } } },
      { method: "get", url: "/api/articles/search", config: { params: { keywords: "vue" } } }
    ]);
  });
});
