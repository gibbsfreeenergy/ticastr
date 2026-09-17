import { mount } from "@vue/test-utils";
import { afterAll, beforeAll, describe, expect, it } from "vitest";

let TopNavBar;
let toolbarStyle;

const blogInfo = {
  websiteConfig: {
    websiteAuthor: "博客作者"
  }
};

function mountTopNav(path = "/") {
  return mount(TopNavBar, {
    global: {
      mocks: {
        $route: { path },
        $store: { state: { avatar: null, blogInfo } }
      },
      stubs: {
        VAppBar: {
          template: '<header v-bind="$attrs" class="v-toolbar v-app-bar v-theme--light"><div class="v-toolbar__content"><slot /></div></header>'
        },
        RouterLink: {
          props: ["to"],
          template: '<a :href="to"><slot /></a>'
        }
      }
    }
  });
}

beforeAll(async () => {
  toolbarStyle = document.createElement("style");
  toolbarStyle.textContent = `
    .v-toolbar__content { overflow: hidden; }
    .v-theme--light.orbit-nav:not(.nav-fixed) a {
      color: #f4f5ff !important;
    }
  `;
  document.head.appendChild(toolbarStyle);
  ({ default: TopNavBar } = await import("./TopNavBar.vue"));
});

afterAll(() => {
  toolbarStyle?.remove();
});

describe("TopNavBar", () => {
  it("keeps the navigation content visible inside the toolbar", () => {
    const wrapper = mountTopNav();
    const toolbarContent = wrapper.find(".v-toolbar__content");

    expect(toolbarContent.exists()).toBe(true);
    expect(getComputedStyle(toolbarContent.element).overflow).toBe("visible");
  });

  it("only exposes the retained reader pages", () => {
    const wrapper = mountTopNav();
    expect(wrapper.findAll(".menu-btn")).toHaveLength(4);
    expect(wrapper.text()).toContain("首页");
    expect(wrapper.text()).toContain("归档");
    expect(wrapper.text()).toContain("关于");
  });
});
