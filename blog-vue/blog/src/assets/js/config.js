const appOrigin = typeof window === "undefined" ? "" : window.location.origin;

export default {
  QQ_APP_ID: import.meta.env.VITE_QQ_APP_ID || "",
  QQ_REDIRECT_URI: import.meta.env.VITE_QQ_REDIRECT_URI || `${appOrigin}/oauth/login/qq`,
  WEIBO_APP_ID: import.meta.env.VITE_WEIBO_APP_ID || "",
  WEIBO_REDIRECT_URI: import.meta.env.VITE_WEIBO_REDIRECT_URI || `${appOrigin}/oauth/login/weibo`,
  TENCENT_CAPTCHA: import.meta.env.VITE_TENCENT_CAPTCHA_ID || "",
  MUSIC_API_BASE_URL: (import.meta.env.VITE_MUSIC_API_BASE_URL || "").replace(/\/$/, "")
};
