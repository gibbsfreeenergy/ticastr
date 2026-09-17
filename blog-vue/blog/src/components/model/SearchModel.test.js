import { mount, flushPromises } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";
import SearchModel from "./SearchModel.vue";

function render(search) {
  return mount(SearchModel, {
    global: {
      mocks: {
        $store: { state: { searchFlag: true } },
        $api: { article: { search } },
        $router: { push: vi.fn() }
      },
      stubs: {
        VDialog: { template: "<div><slot /></div>" },
        VCard: { template: "<div><slot /></div>" },
        VIcon: true
      },
      directives: { "safe-html": (el, binding) => { el.textContent = binding.value; } }
    }
  });
}

function page(ids, nextCursor = null) {
  return { data: { items: ids.map(id => ({ id, articleTitle: `文章 ${id}`, snippet: "摘要" })),
    nextCursor, hasNext: Boolean(nextCursor) } };
}

afterEach(() => vi.useRealTimers());

describe("search pagination", () => {
  it("appends the next page and removes the load-more button at the end", async () => {
    vi.useFakeTimers();
    const search = vi.fn().mockResolvedValueOnce(page([1, 2], "next-page"))
      .mockResolvedValueOnce(page([3]));
    const wrapper = render(search);
    await wrapper.find("input").setValue("文章");
    await vi.advanceTimersByTimeAsync(240);
    await flushPromises();
    expect(wrapper.findAll("li")).toHaveLength(2);
    await wrapper.find("button").trigger("click");
    await flushPromises();
    expect(search.mock.calls[1][0].params).toEqual({ keywords: "文章", size: 10, cursor: "next-page" });
    expect(wrapper.findAll("li")).toHaveLength(3);
    expect(wrapper.find("button").exists()).toBe(false);
    wrapper.unmount();
  });

  it("ignores an older pending page after changing the query", async () => {
    vi.useFakeTimers();
    let finishOldPage;
    const search = vi.fn().mockResolvedValueOnce(page([1], "next-page"))
      .mockImplementationOnce(() => new Promise(resolve => { finishOldPage = resolve; }))
      .mockResolvedValueOnce(page([9]));
    const wrapper = render(search);
    await wrapper.find("input").setValue("旧查询");
    await vi.advanceTimersByTimeAsync(240);
    await wrapper.find("button").trigger("click");
    await wrapper.find("input").setValue("新查询");
    await vi.advanceTimersByTimeAsync(240);
    finishOldPage(page([2]));
    await flushPromises();
    expect(wrapper.findAll("li").map(item => item.text())).toEqual(["文章 9摘要"]);
    expect(search.mock.calls[2][0].params.cursor).toBeUndefined();
    wrapper.unmount();
  });

  it("keeps existing results and retries the same cursor after a failed page", async () => {
    vi.useFakeTimers();
    const search = vi.fn().mockResolvedValueOnce(page([1], "next-page"))
      .mockRejectedValueOnce(new Error("offline")).mockResolvedValueOnce(page([2]));
    const wrapper = render(search);
    await wrapper.find("input").setValue("文章");
    await vi.advanceTimersByTimeAsync(240);
    await wrapper.find("button").trigger("click");
    await flushPromises();
    expect(wrapper.findAll("li")).toHaveLength(1);
    expect(wrapper.find('[role="alert"]').text()).toContain("重试");
    await wrapper.find('[role="alert"] button').trigger("click");
    await flushPromises();
    expect(search.mock.calls[2][0].params.cursor).toBe("next-page");
    expect(wrapper.findAll("li")).toHaveLength(2);
    wrapper.unmount();
  });
});
