import { describe, expect, it } from "vitest";
import { countReadableWords, formatReadingTime, normalizeReadingText } from "./readingStats";

describe("reading stats", () => {
  it("normalizes whitespace before counting readable text", () => {
    expect(normalizeReadingText("  一行\n\n两行  ")).toBe("一行 两行");
  });

  it("counts CJK characters and Latin word groups once", () => {
    expect(countReadableWords("你好，Java 24 Stream API")).toBe(6);
  });

  it("always returns a readable minimum duration", () => {
    expect(formatReadingTime(0)).toBe("1分钟");
    expect(formatReadingTime(901)).toBe("3分钟");
  });
});
