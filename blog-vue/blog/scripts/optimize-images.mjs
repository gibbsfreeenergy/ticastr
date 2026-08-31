import fs from "node:fs/promises";
import path from "node:path";
import process from "node:process";
import { fileURLToPath } from "node:url";
import sharp from "sharp";

const sourceRoot = path.resolve(process.env.PUBLIC_IMAGE_ROOT || "public/images");
const distDirectory = path.resolve(process.env.DIST_DIR || "dist");
const outputRoot = path.resolve(process.env.DIST_IMAGE_ROOT || "dist/assets/image-variants");
const widths = [320, 640, 960, 1440];
const rasterExtensions = new Set([".png", ".jpg", ".jpeg"]);

async function filesUnder(directory) {
  try {
    const entries = await fs.readdir(directory, { withFileTypes: true, recursive: true });
    return entries.filter(entry => entry.isFile()).map(entry => path.join(entry.parentPath || entry.path || directory, entry.name));
  } catch (error) {
    if (error.code === "ENOENT") return [];
    throw error;
  }
}

function relativeName(file) {
  return path.relative(sourceRoot, file).replaceAll(path.sep, "/");
}

async function verifySvg(file) {
  const source = await fs.readFile(file, "utf8");
  if (/<script|on[a-z]+\s*=|javascript:/i.test(source)) {
    throw new Error(`Unsafe SVG asset: ${relativeName(file)}`);
  }
}

export async function optimizeImages() {
  const files = await filesUnder(sourceRoot);
  await fs.mkdir(outputRoot, { recursive: true });
  for (const file of files) {
    const extension = path.extname(file).toLowerCase();
    if (extension === ".svg") {
      await verifySvg(file);
      continue;
    }
    if (!rasterExtensions.has(extension)) continue;
    const relative = relativeName(file);
    const stem = relative.slice(0, -extension.length);
    for (const width of widths) {
      const image = sharp(file).resize({ width, withoutEnlargement: true });
      await Promise.all([
        image.clone().webp({ quality: 82 }).toFile(path.join(outputRoot, `${stem}-${width}.webp`)),
        image.clone().avif({ quality: 68 }).toFile(path.join(outputRoot, `${stem}-${width}.avif`))
      ]);
    }
    // Vite copied the original public raster into dist; remove it so an
    // unbounded upload cannot silently become part of the public bundle.
    await fs.rm(path.join(distDirectory, "images", relative), { force: true });
  }
  return files.length;
}

if (process.argv[1] && path.resolve(process.argv[1]) === path.resolve(fileURLToPath(import.meta.url))) {
  optimizeImages().catch(error => {
    console.error(error.message);
    process.exitCode = 1;
  });
}
