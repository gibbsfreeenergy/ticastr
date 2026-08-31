import fs from "node:fs/promises";
import path from "node:path";
import process from "node:process";

const distDirectory = path.resolve(process.env.DIST_DIR || "dist");
const allowedScriptOrigins = new Set([
  "https://ssl.captcha.qq.com",
  "https://connect.qq.com",
  "https://tjs.sjs.sinajs.cn"
]);

async function htmlFiles(directory) {
  const entries = await fs.readdir(directory, { withFileTypes: true, recursive: true });
  return entries
    .filter(entry => entry.isFile() && entry.name.toLowerCase().endsWith(".html"))
    .map(entry => path.join(entry.parentPath || entry.path || directory, entry.name));
}

function externalScriptOrigins(html) {
  const origins = [];
  for (const match of html.matchAll(/<script\b[^>]*\bsrc\s*=\s*["']([^"']+)["'][^>]*>/gi)) {
    const source = match[1].trim();
    if (!/^https?:\/\//i.test(source)) continue;
    try {
      origins.push(new URL(source).origin);
    } catch {
      origins.push(source);
    }
  }
  return origins;
}

async function verifyStaticContract() {
  let files;
  try {
    files = await htmlFiles(distDirectory);
  } catch (error) {
    if (error.code === "ENOENT") throw new Error("Static contract check requires a built dist directory");
    throw error;
  }
  const violations = [];
  for (const file of files) {
    const html = await fs.readFile(file, "utf8");
    externalScriptOrigins(html).forEach(origin => {
      if (!allowedScriptOrigins.has(origin)) {
        violations.push(`${path.relative(distDirectory, file)} loads unapproved script origin ${origin}`);
      }
    });
    if (/<(?:script|img|iframe)\b[^>]*\son[a-z]+\s*=/i.test(html)) {
      violations.push(`${path.relative(distDirectory, file)} contains an inline event handler`);
    }
  }
  if (violations.length) throw new Error(violations.join("\n"));
  console.log(`Static contract passed for ${files.length} HTML file(s).`);
}

verifyStaticContract().catch(error => {
  console.error(error.message);
  process.exitCode = 1;
});
