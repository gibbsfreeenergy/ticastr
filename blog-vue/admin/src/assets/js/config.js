export default {
  TENCENT_CAPTCHA: import.meta.env.VITE_TENCENT_CAPTCHA_ID || "",
  UPLOAD_SIZE: Number(import.meta.env.VITE_UPLOAD_SIZE_KB || 200)
};
