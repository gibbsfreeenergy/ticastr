/**
 * HTTP boundary shared by the public reader and administrator console.
 * Only endpoints used by the retained article/about workflow live here.
 */
import { createContentApi } from "./contentApi";

export function createApi(client) {
  if (!client) throw new TypeError("createApi requires an HTTP client");

  const get = (url, config) => client.get(url, config);
  const post = (url, data, config) => client.post(url, data, config);
  const put = (url, data, config) => client.put(url, data, config);
  const remove = (url, config) => client.delete(url, config);
  const unwrap = request => request.then(response => response.data);
  const resultGet = (url, config) => unwrap(get(url, config));
  const resultPost = (url, data, config) => unwrap(post(url, data, config));
  const resultPut = (url, data, config) => unwrap(put(url, data, config));
  const resultRemove = (url, config) => unwrap(remove(url, config));
  const contentApi = createContentApi(client);

  return {
    get,
    post,
    put,
    delete: remove,
    request: config => client(config),

    auth: {
      login: payload => resultPost("/api/login", payload),
      logout: () => resultPost("/api/logout"),
      updateInfo: payload => resultPut("/api/users/info", payload),
      updateAvatar: payload => resultPost("/api/users/avatar", payload),
      avatarUploadUrl: "/api/users/avatar"
    },

    article: {
      home: config => resultGet("/api/articles", config),
      archives: config => resultGet("/api/articles/archives", config),
      byId: id => resultGet("/api/articles/" + id),
      content: (id, config) => contentApi.public(id, config),
      search: config => resultGet("/api/articles/search", config),
      adminList: config => resultGet("/api/admin/articles", config),
      adminById: id => resultGet("/api/admin/articles/" + id),
      adminContent: (id, config) => contentApi.admin(id, config),
      previewContent: (_id, payload) => Promise.resolve({ data: payload, notPublished: true }),
      saveContent: (id, payload) => resultPut("/api/admin/articles/" + id + "/content", payload),
      contentVersions: (id, config) => resultGet("/api/admin/articles/" + id + "/versions", config),
      restoreContentVersion: (id, version, payload) =>
        resultPost("/api/admin/articles/" + id + "/versions/" + version + "/restore", payload),
      save: payload => resultPost("/api/admin/articles", payload),
      remove: config => resultRemove("/api/admin/articles", config),
      updateDelete: payload => resultPut("/api/admin/articles", payload),
      updateTop: payload => resultPut("/api/admin/articles/top", payload),
      uploadImage: (data, config) => resultPost("/api/admin/articles/images", data, config)
    },

    admin: {
      home: config => resultGet("/api/admin", config),
      menus: config => resultGet("/api/admin/user/menus", config),
      pages: config => resultGet("/api/admin/pages", config),
      savePage: payload => resultPost("/api/admin/pages", payload),
      websiteConfig: config => resultGet("/api/admin/website/config", config),
      updateWebsiteConfig: payload => resultPut("/api/admin/website/config", payload),
      about: config => resultGet("/api/about", config),
      updateAbout: payload => resultPut("/api/admin/about", payload),
      uploadImage: (data, config) => resultPost("/api/admin/articles/images", data, config),
      uploadImageUrl: "/api/admin/articles/images",
      uploadConfigImage: (data, config) => resultPost("/api/admin/config/images", data, config),
      uploadConfigImageUrl: "/api/admin/config/images",
      updateAdminPassword: payload => resultPut("/api/admin/users/password", payload),
      trafficOverview: config => resultGet("/api/admin/traffic/overview", config),
      trafficTimeseries: config => resultGet("/api/admin/traffic/timeseries", config),
      trafficDaily: config => resultGet("/api/admin/traffic/daily", config),
      trafficSources: config => resultGet("/api/admin/traffic/sources", config),
      trafficTargets: config => resultGet("/api/admin/traffic/targets", config),
      trafficGeo: config => resultGet("/api/admin/traffic/geo", config),
      trafficLive: config => resultGet("/api/admin/traffic/live", config),
      trafficAlerts: config => resultGet("/api/admin/traffic/alerts", config),
      trafficBlocklist: config => resultGet("/api/admin/traffic/blocklist", config),
      trafficBlock: payload => resultPost("/api/admin/traffic/block", payload),
      trafficUnblock: payload => resultPost("/api/admin/traffic/unblock", payload),
      trafficLabel: payload => resultPost("/api/admin/traffic/label", payload),
      trafficAckAlerts: payload => resultPost("/api/admin/traffic/alerts/ack", payload),
      trafficAlertRules: config => resultGet("/api/admin/traffic/alert-rules", config),
      saveTrafficAlertRule: payload => resultPost("/api/admin/traffic/alert-rules", payload),
      deleteTrafficAlertRule: id => resultRemove("/api/admin/traffic/alert-rules/" + encodeURIComponent(id)),
      trafficSyncBlocklist: () => resultPost("/api/admin/traffic/blocklist/sync"),
      trafficCollect: () => resultPost("/api/admin/traffic/collect"),
      trafficGeoRefresh: () => resultPost("/api/admin/traffic/geo/refresh"),
      storageConfigs: config => resultGet("/api/admin/storage/configs", config),
      createStorageConfig: payload => resultPost("/api/admin/storage/configs", payload),
      updateStorageConfig: (id, payload) => resultPut("/api/admin/storage/configs/" + id, payload),
      deleteStorageConfig: id => resultRemove("/api/admin/storage/configs/" + id),
      validateStorageConfig: id => resultPost("/api/admin/storage/configs/" + id + "/validate"),
      activateStorageConfig: id => resultPost("/api/admin/storage/configs/" + id + "/activate"),
      refreshStorageUsage: id => resultPost("/api/admin/storage/configs/" + id + "/usage")
    },

    public: {
      home: config => resultGet("/api/", config),
      about: config => resultGet("/api/about", config)
    }
  };
}
