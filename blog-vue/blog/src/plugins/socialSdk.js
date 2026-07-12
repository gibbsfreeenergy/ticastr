function appendScript(id, src, attributes = {}) {
  if (document.getElementById(id)) return;
  const script = document.createElement("script");
  script.id = id;
  script.src = src;
  script.async = true;
  Object.entries(attributes).forEach(([name, value]) => script.setAttribute(name, value));
  document.head.appendChild(script);
}

export function loadSocialSdk(config) {
  if (config.WEIBO_APP_ID) {
    appendScript(
      "weibo-sdk",
      `https://tjs.sjs.sinajs.cn/open/api/js/wb.js?appkey=${encodeURIComponent(config.WEIBO_APP_ID)}`
    );
  }
  if (config.QQ_APP_ID) {
    appendScript("qq-sdk", "https://connect.qq.com/qc_jssdk.js", {
      "data-appid": config.QQ_APP_ID,
      "data-redirecturi": config.QQ_REDIRECT_URI
    });
  }
}
