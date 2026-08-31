import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url));
const distDirectory = path.resolve(scriptDirectory, "../dist");
const indexPath = path.join(distDirectory, "index.html");
const entryJsBudget = Number(process.env.ADMIN_ENTRY_JS_BUDGET_KB || 640) * 1024;
const entryCssBudget = Number(process.env.ADMIN_ENTRY_CSS_BUDGET_KB || 384) * 1024;
const chunkBudget = Number(process.env.ADMIN_CHUNK_BUDGET_KB || 500) * 1024;

if (!fs.existsSync(indexPath)) {
  console.error("Bundle budget check failed: build dist/index.html first.");
  process.exit(1);
}

const html = fs.readFileSync(indexPath, "utf8");
const initialAssets = [...html.matchAll(/(?:src|href)=\"(\/assets\/[^\"]+\.(?:js|css))\"/g)]
  .map(match => match[1].replace(/^\//, ""));
const initialBytes = initialAssets.reduce((total, asset) => total + fs.statSync(path.join(distDirectory, asset)).size, 0);
const initialJsBytes = initialAssets
  .filter(asset => asset.endsWith(".js"))
  .reduce((total, asset) => total + fs.statSync(path.join(distDirectory, asset)).size, 0);
const initialCssBytes = initialAssets
  .filter(asset => asset.endsWith(".css"))
  .reduce((total, asset) => total + fs.statSync(path.join(distDirectory, asset)).size, 0);
const chunks = fs.readdirSync(path.join(distDirectory, "assets"))
  .filter(file => file.endsWith(".js"))
  .map(file => ({ file, bytes: fs.statSync(path.join(distDirectory, "assets", file)).size }))
  .sort((left, right) => right.bytes - left.bytes);

const failures = [];
if (initialJsBytes > entryJsBudget) failures.push(`initial JavaScript ${(initialJsBytes / 1024).toFixed(1)} KB exceed ${entryJsBudget / 1024} KB`);
if (initialCssBytes > entryCssBudget) failures.push(`initial CSS ${(initialCssBytes / 1024).toFixed(1)} KB exceed ${entryCssBudget / 1024} KB`);
for (const chunk of chunks) {
  if (chunk.bytes > chunkBudget) {
    failures.push(`${chunk.file} ${(chunk.bytes / 1024).toFixed(1)} KB exceed ${chunkBudget / 1024} KB`);
  }
}

console.log(`Initial assets: ${(initialBytes / 1024).toFixed(1)} KB (${(initialJsBytes / 1024).toFixed(1)} KB JS, ${(initialCssBytes / 1024).toFixed(1)} KB CSS)`);
console.log(`Entry budgets: ${entryJsBudget / 1024} KB JS / ${entryCssBudget / 1024} KB CSS`);
console.log(`Largest JavaScript chunks: ${chunks.slice(0, 5).map(chunk => `${chunk.file} ${(chunk.bytes / 1024).toFixed(1)} KB`).join(", ")}`);

if (failures.length > 0) {
  console.error("Bundle budget check failed:");
  failures.forEach(failure => console.error(`- ${failure}`));
  process.exit(1);
}
