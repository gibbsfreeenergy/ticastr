function decodeCookieValue(value) {
  try {
    return decodeURIComponent(value);
  } catch {
    return value;
  }
}

export function getCookieValue(name) {
  if (typeof document === "undefined" || !name) return "";

  const encodedName = encodeURIComponent(name);
  const cookie = document.cookie
    .split(";")
    .map(item => item.trim())
    .find(item => item.startsWith(`${encodedName}=`));

  return cookie ? decodeCookieValue(cookie.slice(encodedName.length + 1)) : "";
}

export function getCsrfHeaders() {
  const token = getCookieValue("XSRF-TOKEN");

  return {
    "X-Requested-With": "XMLHttpRequest",
    ...(token ? { "X-XSRF-TOKEN": token } : {})
  };
}
