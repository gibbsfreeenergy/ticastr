/**
 * API boundary shared by the public blog and the administrator console.
 *
 * The low-level methods remain available temporarily for legacy screens, but
 * new code should use one of the domain methods below.  Domain methods keep
 * endpoint paths and HTTP verbs out of rendered components.
 */
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
    // Compatibility adapter used while legacy screens are migrated.
    get,
    post,
    put,
    delete: remove,
    request: config => client(config),

    auth: {
      login: payload => resultPost("/api/login", payload),
      logout: () => resultPost("/api/logout"),
      register: payload => resultPost("/api/register", payload),
      sendCode: username => resultGet("/api/users/code", { params: { username } }),
      sendEmailCode: payload => resultPost("/api/users/email", payload),
      updatePassword: payload => resultPut("/api/users/password", payload),
      updateInfo: payload => resultPut("/api/users/info", payload),
      updateAvatar: payload => resultPut("/api/users/avatar", payload),
      avatarUploadUrl: "/api/users/avatar",
      oauthQQ: payload => resultPost("/api/users/oauth/qq", payload),
      oauthWeibo: payload => resultPost("/api/users/oauth/weibo", payload)
    },

    article: {
      home: config => resultGet("/api/articles", config),
      archives: config => resultGet("/api/articles/archives", config),
      byId: id => resultGet(`/api/articles/${id}`),
      byPath: path => resultGet(`/api${path}`),
      content: (id, config) => contentApi.public(id, config),
      condition: config => resultGet("/api/articles/condition", config),
      search: config => resultGet("/api/articles/search", config),
      like: id => resultPost(`/api/articles/${id}/like`),
      adminList: config => resultGet("/api/admin/articles", config),
      adminById: id => resultGet(`/api/admin/articles/${id}`),
      adminContent: (id, config) => contentApi.admin(id, config),
      previewContent: (_id, payload) => Promise.resolve({ data: payload, notPublished: true }),
      saveContent: (id, payload) => resultPut(`/api/admin/articles/${id}/content`, payload),
      contentVersions: (id, config) => resultGet(`/api/admin/articles/${id}/versions`, config),
      restoreContentVersion: (id, version, payload) =>
        resultPost(`/api/admin/articles/${id}/versions/${version}/restore`, payload),
      save: payload => resultPost("/api/admin/articles", payload),
      remove: config => resultRemove("/api/admin/articles", config),
      updateDelete: payload => resultPut("/api/admin/articles", payload),
      updateTop: payload => resultPut("/api/admin/articles/top", payload),
      uploadImage: (data, config) => resultPost("/api/admin/articles/images", data, config)
    },

    comment: {
      list: config => resultGet("/api/comments", config),
      replies: (id, config) => resultGet(`/api/comments/${id}/replies`, config),
      create: payload => resultPost("/api/comments", payload),
      like: id => resultPost(`/api/comments/${id}/like`),
      adminList: config => resultGet("/api/admin/comments", config),
      review: payload => resultPut("/api/admin/comments/review", payload),
      remove: config => resultRemove("/api/admin/comments", config)
    },

    talk: {
      list: config => resultGet("/api/talks", config),
      byId: id => resultGet(`/api/talks/${id}`),
      like: id => resultPost(`/api/talks/${id}/like`),
      adminList: config => resultGet("/api/admin/talks", config),
      adminById: id => resultGet(`/api/admin/talks/${id}`),
      save: payload => resultPost("/api/admin/talks", payload),
      remove: config => resultRemove("/api/admin/talks", config),
      uploadImage: (data, config) => resultPost("/api/admin/talks/images", data, config)
    },

    album: {
      list: config => resultGet("/api/photos/albums", config),
      photos: (id, config) => resultGet(`/api/albums/${id}/photos`, config),
      adminList: config => resultGet("/api/admin/photos/albums", config),
      adminInfo: id => resultGet(`/api/admin/photos/albums/${id}/info`),
      adminOptions: config => resultGet("/api/admin/photos/albums/info", config),
      save: payload => resultPost("/api/admin/photos/albums", payload),
      remove: id => resultRemove(`/api/admin/photos/albums/${id}`),
      uploadCover: (data, config) => resultPost("/api/admin/photos/albums/cover", data, config),
      listPhotos: config => resultGet("/api/admin/photos", config),
      savePhotos: payload => resultPost("/api/admin/photos", payload),
      updatePhoto: payload => resultPut("/api/admin/photos", payload),
      movePhotos: payload => resultPut("/api/admin/photos/album", payload),
      updateDelete: payload => resultPut("/api/admin/photos/delete", payload),
      removePhotos: config => resultRemove("/api/admin/photos", config)
    },

    catalog: {
      categories: config => resultGet("/api/categories", config),
      categorySearch: config => resultGet("/api/admin/categories/search", config),
      adminCategories: config => resultGet("/api/admin/categories", config),
      saveCategory: payload => resultPost("/api/admin/categories", payload),
      removeCategories: config => resultRemove("/api/admin/categories", config),
      tags: config => resultGet("/api/tags", config),
      tagSearch: config => resultGet("/api/admin/tags/search", config),
      adminTags: config => resultGet("/api/admin/tags", config),
      saveTag: payload => resultPost("/api/admin/tags", payload),
      removeTags: config => resultRemove("/api/admin/tags", config)
    },

    admin: {
      home: config => resultGet("/api/admin", config),
      menus: config => resultGet("/api/admin/user/menus", config),
      menuList: config => resultGet("/api/admin/menus", config),
      saveMenu: payload => resultPost("/api/admin/menus", payload),
      removeMenu: id => resultRemove(`/api/admin/menus/${id}`),
      resources: config => resultGet("/api/admin/resources", config),
      saveResource: payload => resultPost("/api/admin/resources", payload),
      removeResource: id => resultRemove(`/api/admin/resources/${id}`),
      roleResources: config => resultGet("/api/admin/role/resources", config),
      roleMenus: config => resultGet("/api/admin/role/menus", config),
      roles: config => resultGet("/api/admin/roles", config),
      saveRole: payload => resultPost("/api/admin/role", payload),
      removeRoles: config => resultRemove("/api/admin/roles", config),
      users: config => resultGet("/api/admin/users", config),
      userRoles: config => resultGet("/api/admin/users/role", config),
      updateUserRole: payload => resultPut("/api/admin/users/role", payload),
      updateUserDisable: payload => resultPut("/api/admin/users/disable", payload),
      onlineUsers: config => resultGet("/api/admin/users/online", config),
      removeOnlineUser: id => resultRemove(`/api/admin/users/${id}/online`),
      userAreas: config => resultGet("/api/admin/users/area", config),
      updateAdminPassword: payload => resultPut("/api/admin/users/password", payload),
      messages: config => resultGet("/api/admin/messages", config),
      reviewMessages: payload => resultPut("/api/admin/messages/review", payload),
      removeMessages: config => resultRemove("/api/admin/messages", config),
      operationLogs: config => resultGet("/api/admin/operation/logs", config),
      removeOperationLogs: config => resultRemove("/api/admin/operation/logs", config),
      pages: config => resultGet("/api/admin/pages", config),
      savePage: payload => resultPost("/api/admin/pages", payload),
      removePage: id => resultRemove(`/api/admin/pages/${id}`),
      links: config => resultGet("/api/admin/links", config),
      saveLink: payload => resultPost("/api/admin/links", payload),
      removeLinks: config => resultRemove("/api/admin/links", config),
      websiteConfig: config => resultGet("/api/admin/website/config", config),
      updateWebsiteConfig: payload => resultPut("/api/admin/website/config", payload),
      about: config => resultGet("/api/about", config),
      updateAbout: payload => resultPut("/api/admin/about", payload),
      uploadImage: (data, config) => resultPost("/api/admin/articles/images", data, config),
      uploadImageUrl: "/api/admin/articles/images",
      uploadConfigImage: (data, config) => resultPost("/api/admin/config/images", data, config),
      uploadConfigImageUrl: "/api/admin/config/images",
      uploadAlbumCover: (data, config) => resultPost("/api/admin/photos/albums/cover", data, config),
      uploadAlbumCoverUrl: "/api/admin/photos/albums/cover",
      uploadTalkImage: (data, config) => resultPost("/api/admin/talks/images", data, config),
      uploadTalkImageUrl: "/api/admin/talks/images",
      storageProvider: config => resultGet("/api/admin/storage/provider", config),
      storageProviders: config => resultGet("/api/admin/storage/providers", config),
      validateStorageProvider: provider => resultPost(`/api/admin/storage/providers/${provider}/validate`),
      switchStorageProvider: payload => resultPut("/api/admin/storage/provider", payload),
      outbox: config => resultGet("/api/admin/outbox", config),
      outboxMetrics: config => resultGet("/api/admin/outbox/metrics", config),
      retryOutbox: eventId => resultPost(`/api/admin/outbox/${eventId}/retry`)
    },

    public: {
      home: config => resultGet("/api/", config),
      homeTalks: config => resultGet("/api/home/talks", config),
      about: config => resultGet("/api/about", config),
      links: config => resultGet("/api/links", config),
      messages: config => resultGet("/api/messages", config),
      createMessage: payload => resultPost("/api/messages", payload),
      sendVoice: (data, config) => resultPost("/api/voice", data, config),
      report: config => resultPost("/api/report", null, config)
    }
  };
}
import { createContentApi } from "./contentApi";
