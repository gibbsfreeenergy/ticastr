import Compressor from "compressorjs";

const AVATAR_MAX_EDGE = 512;
const AVATAR_QUALITY = 0.8;
const PNG_TO_JPEG_THRESHOLD = 512 * 1024;

function withUploadMetadata(result, original) {
  const type = result.type || original.type || "image/jpeg";
  const extension = type === "image/jpeg" ? ".jpg" : type === "image/png" ? ".png" : "";
  const name = extension && original.name?.includes(".")
    ? original.name.replace(/\.[^.]+$/, extension)
    : original.name;

  if (result instanceof File && result.name === name) return result;
  return new File([result], name, { type, lastModified: original.lastModified });
}

export function compressImageForUpload(file, targetSizeKb) {
  if (!file || !targetSizeKb || file.size / 1024 <= targetSizeKb) return file;

  return new Promise((resolve, reject) => {
    new Compressor(file, {
      maxWidth: AVATAR_MAX_EDGE,
      maxHeight: AVATAR_MAX_EDGE,
      quality: AVATAR_QUALITY,
      checkOrientation: true,
      convertTypes: ["image/png"],
      convertSize: PNG_TO_JPEG_THRESHOLD,
      success(result) {
        const compressed = withUploadMetadata(result, file);
        resolve(compressed.size < file.size ? compressed : file);
      },
      error: reject
    });
  });
}
