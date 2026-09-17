import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { flushPromises, shallowMount } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";
import StorageConfigPanel from "./StorageConfigPanel.vue";

const storageConfigPanelSource = readFileSync(resolve(process.cwd(), "src/views/setting/StorageConfigPanel.vue"), "utf8");

const localConfig = {
  id: 7,
  name: "本地上传",
  provider: "local",
  active: true,
  configured: true,
  credentialsConfigured: false,
  localRoot: "C:/storage/uploads",
  publicUrl: "https://assets.example.test",
  validation: { status: "SUCCESS", success: true, validatedAt: "2026-08-31T08:20:00", message: "验证成功" },
  usage: { status: "SUCCESS", objectCount: 128, bytes: 7340032, latestModified: "2026-08-31T08:20:00", checkedAt: "2026-08-31T08:21:00" }
};

function createAdminApi(overrides = {}) {
  return {
    storageConfigs: vi.fn().mockResolvedValue({ data: { activeConfigId: 7, configs: [localConfig] } }),
    createStorageConfig: vi.fn().mockResolvedValue({ data: { id: 8 } }),
    updateStorageConfig: vi.fn().mockResolvedValue({ data: { id: 7 } }),
    deleteStorageConfig: vi.fn().mockResolvedValue({}),
    validateStorageConfig: vi.fn().mockResolvedValue({ data: { status: "SUCCESS", success: true, message: "验证成功" } }),
    activateStorageConfig: vi.fn().mockResolvedValue({ data: { id: 8, active: true } }),
    refreshStorageUsage: vi.fn().mockResolvedValue({ data: localConfig.usage }),
    ...overrides
  };
}

function mountPanel(admin = createAdminApi()) {
  return shallowMount(StorageConfigPanel, {
    global: {
      config: {
        globalProperties: {
          $api: { admin },
          $message: { success: vi.fn(), error: vi.fn(), warning: vi.fn() }
        }
      }
    }
  });
}

describe("StorageConfigPanel", () => {
  beforeEach(() => {
    vi.stubGlobal("confirm", vi.fn(() => true));
  });

  it("keeps the dialog at its desktop width while constraining it on narrow viewports", async () => {
    const wrapper = mountPanel();
    await flushPromises();
    const dialog = wrapper.find(".storage-config-dialog");
    const dialogStyle = storageConfigPanelSource.match(/:deep\(\.storage-config-dialog\)\s*\{([^}]*)\}/)?.[1] || "";

    expect(dialog.exists()).toBe(true);
    expect(dialog.classes()).toContain("storage-config-dialog");
    expect(dialog.attributes("width")).toBe("560px");
    expect(dialogStyle).toContain("max-width: calc(100vw - 32px);");
  });

  it("shows only local fields for local and cloud fields for cloud providers", async () => {
    const wrapper = mountPanel();
    await flushPromises();

    wrapper.vm.openCreate();
    await wrapper.vm.$nextTick();
    expect(wrapper.find('[data-test="local-root"]').exists()).toBe(true);
    expect(wrapper.find('[data-test="endpoint"]').exists()).toBe(false);

    wrapper.vm.form.provider = "oss";
    await wrapper.vm.$nextTick();
    expect(wrapper.find('[data-test="local-root"]').exists()).toBe(false);
    expect(wrapper.find('[data-test="endpoint"]').exists()).toBe(true);
    expect(wrapper.find('[data-test="access-key-id"]').exists()).toBe(true);
    expect(wrapper.find('[data-test="access-key-secret"]').exists()).toBe(true);
  });

  it("keeps edit credentials blank and submits only the supported request shape", async () => {
    const admin = createAdminApi();
    const wrapper = mountPanel(admin);
    await flushPromises();
    const unsafeResponse = {
      ...localConfig,
      provider: "oss",
      endpoint: "https://storage.example.test",
      region: "cn-shanghai",
      bucket: "blog-assets",
      accessKeyId: "response-key-must-not-escape",
      accessKeySecret: "response-secret-must-not-escape",
      accessKeySecretCiphertext: "ciphertext-must-not-escape",
      providerCredential: "unknown-credential-must-not-escape"
    };

    wrapper.vm.openEdit(unsafeResponse);
    await wrapper.vm.$nextTick();
    expect(wrapper.vm.form.accessKeyId).toBe("");
    expect(wrapper.vm.form.accessKeySecret).toBe("");
    expect(JSON.stringify(wrapper.vm.form)).not.toContain("must-not-escape");
    expect(wrapper.html()).not.toContain("must-not-escape");

    await wrapper.vm.save();

    expect(admin.updateStorageConfig).toHaveBeenCalledWith(7, {
      name: "本地上传",
      provider: "oss",
      endpoint: "https://storage.example.test",
      region: "cn-shanghai",
      bucket: "blog-assets",
      localRoot: "C:/storage/uploads",
      publicUrl: "https://assets.example.test",
      accessKeyId: "",
      accessKeySecret: ""
    });
  });

  it("uses the shared create, update, and delete methods with ordered payloads", async () => {
    const admin = createAdminApi();
    const wrapper = mountPanel(admin);
    await flushPromises();
    wrapper.vm.openCreate();
    Object.assign(wrapper.vm.form, {
      name: "新本地目录",
      provider: "local",
      localRoot: "D:/uploads",
      publicUrl: "https://cdn.example.test"
    });

    await wrapper.vm.save();
    expect(admin.createStorageConfig).toHaveBeenCalledWith({
      name: "新本地目录",
      provider: "local",
      endpoint: "",
      region: "",
      bucket: "",
      localRoot: "D:/uploads",
      publicUrl: "https://cdn.example.test",
      accessKeyId: "",
      accessKeySecret: ""
    });

    await wrapper.vm.removeConfig({ ...localConfig, active: false });
    expect(confirm).toHaveBeenCalled();
    expect(admin.deleteStorageConfig).toHaveBeenCalledWith(7);
    expect(admin.storageConfigs.mock.calls.length).toBeGreaterThanOrEqual(3);
  });

  it("validates, activates, refreshes usage, reloads, and disables active deletion", async () => {
    const admin = createAdminApi();
    const wrapper = mountPanel(admin);
    await flushPromises();

    expect(wrapper.find('[data-test="delete-storage-config-7"]').attributes("disabled")).toBeDefined();
    await wrapper.vm.validateConfig(localConfig);
    await wrapper.vm.activateConfig({ ...localConfig, id: 8, active: false });
    await wrapper.vm.refreshUsage(localConfig);

    expect(admin.validateStorageConfig).toHaveBeenCalledWith(7);
    expect(admin.activateStorageConfig).toHaveBeenCalledWith(8);
    expect(admin.refreshStorageUsage).toHaveBeenCalledWith(7);
    expect(admin.storageConfigs.mock.calls.length).toBeGreaterThanOrEqual(4);
  });

  it("renders last-good usage after a failed refresh and keeps errors safely generic", async () => {
    const failedUsage = {
      status: "FAILED",
      objectCount: 128,
      bytes: 7340032,
      latestModified: "2026-08-31T08:20:00",
      checkedAt: "2026-08-31T08:25:00",
      error: "使用量刷新失败",
      accessKeySecretCiphertext: "never-render-this-ciphertext"
    };
    const admin = createAdminApi({
      storageConfigs: vi.fn().mockResolvedValue({
        data: { activeConfigId: 7, configs: [{ ...localConfig, usage: failedUsage, unknownCredential: "never-render-this-credential" }] }
      }),
      refreshStorageUsage: vi.fn().mockResolvedValue({ data: failedUsage })
    });
    const wrapper = mountPanel(admin);
    await flushPromises();

    expect(wrapper.text()).toContain("128 个对象");
    expect(wrapper.text()).toContain("7 MB");
    expect(wrapper.find(".storage-config-failure").text()).toBe("使用量刷新失败；已保留上次成功统计。");
    expect(wrapper.find(".storage-config-failure").text().match(/使用量刷新失败/g)).toHaveLength(1);
    expect(wrapper.html()).not.toContain("never-render-this");
    expect(JSON.stringify(wrapper.vm.configs)).not.toContain("never-render-this");

    admin.storageConfigs.mockRejectedValueOnce(new Error("accessKeySecret=never-show-this"));
    await wrapper.vm.load();
    expect(wrapper.vm.error).toBe("存储配置暂时无法读取，请稍后重试");
    expect(wrapper.text()).not.toContain("never-show-this");
    expect(wrapper.vm.configs).toHaveLength(1);
  });

  it("allowlists defined status details and drops opaque values without sensitive keywords", async () => {
    const admin = createAdminApi({
      storageConfigs: vi.fn().mockResolvedValue({
        data: {
          activeConfigId: 7,
          configs: [{
            ...localConfig,
            validation: {
              status: "SUCCESS",
              success: true,
              validatedAt: "2026-08-31T08:20:00",
              message: " 验证成功 "
            },
            usage: {
              status: "FAILED",
              objectCount: 128,
              bytes: 7340032,
              latestModified: "2026-08-31T08:20:00",
              checkedAt: "2026-08-31T08:25:00",
              error: " 使用量刷新失败 "
            }
          }, {
            ...localConfig,
            id: 8,
            validation: { status: "FAILED", message: "配置验证失败" },
            usage: { status: "FAILED", error: "J8m4Qs7wX2pL" }
          }, {
            ...localConfig,
            id: 9,
            validation: { status: "FAILED", message: "配置字段不完整" },
            usage: { status: "FAILED", error: "opaque-identifier-8d43f9" }
          }, {
            ...localConfig,
            id: 10,
            validation: { status: "FAILED", message: "random-value-62b9" },
            usage: { status: "FAILED", error: "C2n7Vb9Kp4" }
          }]
        }
      })
    });
    const wrapper = mountPanel(admin);
    await flushPromises();

    expect(wrapper.vm.configs[0].validation.message).toBe("验证成功");
    expect(wrapper.vm.configs[0].usage.error).toBe("使用量刷新失败");
    expect(wrapper.vm.configs[1].validation.message).toBe("配置验证失败");
    expect(wrapper.vm.configs[2].validation.message).toBe("配置字段不完整");
    expect(wrapper.text()).toContain("验证成功");
    expect(wrapper.text()).toContain("使用量刷新失败");
    expect(wrapper.text()).toContain("配置验证失败");
    expect(wrapper.text()).toContain("配置字段不完整");
    expect(wrapper.vm.configs[1].usage.error).toBe("");
    expect(wrapper.vm.configs[2].usage.error).toBe("");
    expect(wrapper.vm.configs[3].validation.message).toBe("");
    expect(wrapper.vm.configs[3].usage.error).toBe("");
    expect(wrapper.html()).not.toContain("J8m4Qs7wX2pL");
    expect(wrapper.html()).not.toContain("opaque-identifier-8d43f9");
    expect(wrapper.html()).not.toContain("random-value-62b9");
    expect(wrapper.html()).not.toContain("C2n7Vb9Kp4");
    expect(JSON.stringify(wrapper.vm.configs)).not.toContain("J8m4Qs7wX2pL");
    expect(JSON.stringify(wrapper.vm.configs)).not.toContain("opaque-identifier-8d43f9");
    expect(JSON.stringify(wrapper.vm.configs)).not.toContain("random-value-62b9");
    expect(JSON.stringify(wrapper.vm.configs)).not.toContain("C2n7Vb9Kp4");
  });

  it("keeps an initial load error distinct from the empty state and preserves existing configs on refresh failure", async () => {
    const admin = createAdminApi({ storageConfigs: vi.fn().mockRejectedValue(new Error("network unavailable")) });
    const wrapper = mountPanel(admin);
    await flushPromises();

    expect(wrapper.vm.error).toBe("存储配置暂时无法读取，请稍后重试");
    expect(wrapper.vm.configs).toEqual([]);
    expect(wrapper.text()).not.toContain("暂无存储配置。");

    wrapper.vm.configs = [{ ...localConfig }];
    await wrapper.vm.load();
    expect(wrapper.text()).toContain("本地上传");
    expect(wrapper.text()).not.toContain("暂无存储配置。");
  });
});
