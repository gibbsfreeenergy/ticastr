import { describe, expect, it } from "vitest";
import {
  getShelfArticles,
  normalizeShelfIds,
  readShelfIds,
  toggleShelfId,
  writeShelfIds
} from "./readingShelf";

describe("readingShelf", () => {
  it("normalizes ids, removes duplicates, and toggles without mutating input", () => {
    const ids = [1, " 2 ", 1, "", null];

    expect(normalizeShelfIds(ids)).toEqual(["1", "2"]);
    expect(toggleShelfId(ids, 2)).toEqual(["1"]);
    expect(toggleShelfId(ids, 3)).toEqual(["1", "2", "3"]);
    expect(ids).toEqual([1, " 2 ", 1, "", null]);
  });

  it("keeps saved article order and ignores stale ids", () => {
    const articles = [{ id: 2, title: "second" }, { id: 1, title: "first" }];

    expect(getShelfArticles(articles, [1, 99, 2])).toEqual([
      { id: 1, title: "first" },
      { id: 2, title: "second" }
    ]);
  });

  it("round-trips storage and falls back safely for malformed values", () => {
    const values = new Map();
    const storage = {
      getItem(key) {
        return values.get(key) ?? null;
      },
      setItem(key, value) {
        values.set(key, value);
      }
    };

    expect(readShelfIds(storage)).toEqual([]);
    writeShelfIds([4, "4", 5], storage);
    expect(readShelfIds(storage)).toEqual(["4", "5"]);
    values.set("ticastr:reading-shelf", "not-json");
    expect(readShelfIds(storage)).toEqual([]);
  });
});
