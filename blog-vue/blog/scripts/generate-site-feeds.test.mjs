import test from "node:test";
import assert from "node:assert/strict";

process.env.PUBLIC_SITE_ORIGIN = "https://blog.example";
const { buildAtom, buildRss, validateOrigin } = await import("./generate-site-feeds.mjs");

test("feed builders escape metadata and keep article URLs canonical", () => {
  const article = {
    id: 7,
    articleTitle: "A & B",
    articleIntro: "摘要 <tag>"
  };
  const rss = buildRss([article]);
  const atom = buildAtom([article]);

  assert.match(rss, /A &amp; B/);
  assert.match(rss, /https:\/\/blog\.example\/articles\/7\//);
  assert.doesNotMatch(rss, /摘要 <tag>/);
  assert.match(atom, /<entry>/);
  assert.match(atom, /<id>https:\/\/blog\.example\/articles\/7\//);
});

test("production feed origins reject non-HTTPS and path-bearing values", () => {
  assert.equal(validateOrigin("https://blog.example.com/"), "https://blog.example.com");
  assert.throws(() => validateOrigin("https://blog.example.com/path"));
});
