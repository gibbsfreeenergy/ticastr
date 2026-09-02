import { flushPromises, shallowMount } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";
import StorageConfigPanel from "./StorageConfigPanel.vue";

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
      endpoint: "https://oss.example.test",
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
      endpoint: "https://oss.example.test",
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
    expect(wrapper.text()).toContain("使用量刷新失败");
    expect(wrapper.html()).not.toContain("never-render-this");
    expect(JSON.stringify(wrapper.vm.configs)).not.toContain("never-render-this");

    admin.storageConfigs.mockRejectedValueOnce(new Error("accessKeySecret=never-show-this"));
    await wrapper.vm.load();
    expect(wrapper.vm.error).toBe("存储配置暂时无法读取，请稍后重试");
    expect(wrapper.text()).not.toContain("never-show-this");
    expect(wrapper.vm.configs).toHaveLength(1);
  });

  it("preserves safe validation and usage details while rejecting credential and exception details", async () => {
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
              message: "连接验证完成"
            },
            usage: {
              status: "FAILED",
              objectCount: 128,
              bytes: 7340032,
              latestModified: "2026-08-31T08:20:00",
              checkedAt: "2026-08-31T08:25:00",
              error: "存储服务暂不可用"
            }
          }, {
            ...localConfig,
            id: 8,
            validation: { status: "FAILED", message: "accessKeySecret=must-not-render" },
            usage: { status: "FAILED", error: "RuntimeException: must-not-render" }
          }]
        }
      })
    });
    const wrapper = mountPanel(admin);
    await flushPromises();

    expect(wrapper.vm.configs[0].validation.message).toBe("连接验证完成");
    expect(wrapper.vm.configs[0].usage.error).toBe("存储服务暂不可用");
    expect(wrapper.text()).toContain("连接验证完成");
    expect(wrapper.text()).toContain("存储服务暂不可用");
    expect(wrapper.vm.configs[1].validation.message).toBe("");
    expect(wrapper.vm.configs[1].usage.error).toBe("");
    expect(wrapper.html()).not.toContain("must-not-render");
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
