import { nextTick } from "vue";
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
  it("allows discovery and entertainment submenus to extend beyond the toolbar", () => {
    const wrapper = mountTopNav();
    const toolbarContent = wrapper.find(".v-toolbar__content");

    expect(toolbarContent.exists()).toBe(true);
    expect(getComputedStyle(toolbarContent.element).overflow).toBe("visible");
  });

  it("keeps submenu labels readable on the orbit page", async () => {
    const wrapper = mountTopNav("/orbit");
    await nextTick();
    const submenuLinks = wrapper.findAll(".menus-submenu a");

    expect(wrapper.vm.$route.path).toBe("/orbit");
    expect(wrapper.vm.navClass).toContain("orbit-nav");
    expect(wrapper.find(".v-app-bar").attributes("class")).toContain("orbit-nav");
    expect(submenuLinks.length).toBeGreaterThan(0);
    expect(getComputedStyle(submenuLinks[0].element).color).toBe("rgb(76, 73, 72)");
  });
});
