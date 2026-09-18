import { describe, expect, it } from "vitest";
import { normalizeMediaUrl } from "./media";

describe("media URL normalization", () => {
  it("trims blank references without inventing a local asset", () => {
    expect(normalizeMediaUrl("  /uploads/cover.png  ")).toBe("/uploads/cover.png");
    expect(normalizeMediaUrl("   ")).toBe("");
    expect(normalizeMediaUrl(null)).toBe("");
  });

  it("resolves protocol-relative storage URLs against the current site", () => {
    expect(normalizeMediaUrl("//cdn.example.com/cover.png")).toBe("http://cdn.example.com/cover.png");
  });
});
