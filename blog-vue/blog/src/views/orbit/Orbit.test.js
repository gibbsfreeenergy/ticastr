import { nextTick } from "vue";
import { mount } from "@vue/test-utils";
import { describe, expect, it, vi } from "vitest";
import Orbit from "./Orbit.vue";

async function settle() {
  await Promise.resolve();
  await nextTick();
  await Promise.resolve();
  await nextTick();
}

function mountOrbit(home) {
  return mount(Orbit, {
    global: {
      mocks: {
        $api: { article: { home } },
        date: value => String(value).slice(0, 10)
      },
      stubs: {
        RouterLink: {
          props: ["to"],
          template: '<a data-testid="router-link" :href="to"><slot /></a>'
        }
      }
    }
  });
}

const response = {
  flag: true,
  data: {
    items: [
      {
        id: 1,
        articleTitle: "Redis 的第二条路",
        categoryName: "系统",
        createTime: "2026-08-30T00:00:00Z",
        tagDTOList: [{ id: 1, tagName: "性能" }]
      },
      {
        id: 2,
        articleTitle: "给夏天写一封信",
        categoryName: "生活",
        createTime: "2026-07-01T00:00:00Z",
        tagDTOList: [{ id: 2, tagName: "随笔" }]
      }
    ]
  }
};

describe("Orbit page", () => {
  it("renders, filters, selects, and links to a real article", async () => {
    const wrapper = mountOrbit(vi.fn().mockResolvedValue(response));
    await settle();

    expect(wrapper.text()).toContain("Redis 的第二条路");
    expect(wrapper.text()).toContain("给夏天写一封信");

    const lifeFilter = wrapper.findAll("button").find(button => button.text() === "生活");
    await lifeFilter.trigger("click");
    expect(wrapper.text()).toContain("给夏天写一封信");
    expect(wrapper.text()).not.toContain("Redis 的第二条路");

    await wrapper.find('[data-article-id="2"]').trigger("click");
    expect(wrapper.find('[data-testid="selected-title"]').text()).toBe("给夏天写一封信");
    expect(wrapper.find('[data-testid="router-link"]').attributes("href")).toBe("/articles/2");
  });

  it("keeps the shell visible and exposes retry after a failed request", async () => {
    const wrapper = mountOrbit(vi.fn().mockRejectedValue(new Error("offline")));
    await settle();

    expect(wrapper.text()).toContain("阅读星图");
    expect(wrapper.text()).toContain("暂时无法读取文章元数据，请稍后重试。");
    expect(wrapper.find('[data-testid="retry-articles"]').exists()).toBe(true);
  });
});
