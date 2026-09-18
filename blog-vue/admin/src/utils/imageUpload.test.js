import { describe, expect, it, vi } from "vitest";

const { Compressor } = vi.hoisted(() => ({
  Compressor: vi.fn()
}));

vi.mock("compressorjs", () => ({ default: Compressor }));

import { compressImageForUpload } from "./imageUpload";

describe("compressImageForUpload", () => {
  it("keeps files that are already within the target size", async () => {
    const file = new File([new Uint8Array(100)], "avatar.jpg", { type: "image/jpeg" });

    const result = await compressImageForUpload(file, 1);

    expect(result).toBe(file);
    expect(Compressor).not.toHaveBeenCalled();
  });

  it("returns a smaller File while preserving the upload metadata", async () => {
    const file = new File([new Uint8Array(3 * 1024)], "avatar.jpg", {
      type: "image/jpeg",
      lastModified: 123
    });
    const compressed = new Blob([new Uint8Array(512)], { type: "image/jpeg" });
    Compressor.mockImplementationOnce(function(received, options) {
      expect(received).toBe(file);
      expect(options.maxWidth).toBe(512);
      expect(options.maxHeight).toBe(512);
      expect(options.quality).toBe(0.8);
      options.success(compressed);
    });

    const result = await compressImageForUpload(file, 1);

    expect(result).not.toBe(file);
    expect(result).toBeInstanceOf(File);
    expect(result.name).toBe("avatar.jpg");
    expect(result.type).toBe("image/jpeg");
    expect(result.size).toBe(512);
    expect(result.lastModified).toBe(123);
    expect(Compressor).toHaveBeenCalledTimes(1);
  });
});
