import fs from "node:fs";

const [, , inputPath, outputPath] = process.argv;

if (!inputPath || !outputPath) {
  console.error("Usage: node convert-mysql-dump.mjs <mysql-dump.sql> <d1-data.sql>");
  process.exit(1);
}

const dump = fs.readFileSync(inputPath, "utf8");
const skippedTables = new Set(["flyway_schema_history"]);

function parseSchemaColumns() {
  const columnsByTable = new Map();
  const tablePattern = /CREATE TABLE `([^`]+)` \(([\s\S]*?)\) ENGINE=/g;
  for (const match of dump.matchAll(tablePattern)) {
    const columns = [];
    for (const line of match[2].split(/\r?\n/)) {
      const column = line.match(/^\s*`([^`]+)`\s+/);
      if (column && !line.includes("GENERATED ALWAYS")) columns.push(column[1]);
    }
    columnsByTable.set(match[1], columns);
  }
  return columnsByTable;
}

function splitTuples(values) {
  const tuples = [];
  let quote = false;
  let depth = 0;
  let start = -1;
  let escaped = false;

  for (let index = 0; index < values.length; index += 1) {
    const character = values[index];
    if (escaped) {
      escaped = false;
      continue;
    }
    if (quote && character === "\\") {
      escaped = true;
      continue;
    }
    if (character === "'") {
      if (quote && values[index + 1] === "'") {
        index += 1;
        continue;
      }
      quote = !quote;
      continue;
    }
    if (quote) continue;
    if (character === "(") {
      if (depth === 0) start = index + 1;
      depth += 1;
    } else if (character === ")") {
      depth -= 1;
      if (depth === 0) tuples.push(values.slice(start, index));
    }
  }
  return tuples;
}

function splitValues(tuple) {
  const values = [];
  let start = 0;
  let quote = false;
  let escaped = false;

  for (let index = 0; index < tuple.length; index += 1) {
    const character = tuple[index];
    if (escaped) {
      escaped = false;
      continue;
    }
    if (quote && character === "\\") {
      escaped = true;
      continue;
    }
    if (character === "'") {
      if (quote && tuple[index + 1] === "'") {
        index += 1;
        continue;
      }
      quote = !quote;
    } else if (!quote && character === ",") {
      values.push(tuple.slice(start, index).trim());
      start = index + 1;
    }
  }
  values.push(tuple.slice(start).trim());
  return values;
}

function decodeMysqlString(value) {
  let decoded = "";
  for (let index = 1; index < value.length - 1; index += 1) {
    const character = value[index];
    if (character !== "\\") {
      decoded += character;
      continue;
    }
    const next = value[++index];
    const replacements = { n: "\n", r: "\r", t: "\t", 0: "\0", b: "\b", Z: "\x1a" };
    decoded += replacements[next] ?? next;
  }
  return decoded;
}

function sqliteLiteral(value) {
  const trimmed = value.trim();
  if (/^null$/i.test(trimmed) || trimmed === "\\N") return "NULL";
  if (/^b'[01]'$/i.test(trimmed)) return trimmed.slice(2, -1);
  if (/^0x[0-9a-f]+$/i.test(trimmed)) return trimmed;
  if (trimmed.startsWith("'") && trimmed.endsWith("'")) {
    return `'${decodeMysqlString(trimmed).replaceAll("'", "''")}'`;
  }
  return trimmed;
}

function parseInsert(line) {
  const match = line.match(/^INSERT INTO `([^`]+)`(?: \(([^)]+)\))? VALUES (.*);$/);
  if (!match) return null;
  const table = match[1];
  const columns = match[2]
    ? match[2].split(",").map(column => column.trim().replaceAll("`", ""))
    : schemaColumns.get(table);
  if (!columns?.length) throw new Error(`No column metadata for ${table}`);
  const rows = splitTuples(match[3]).map(tuple => splitValues(tuple));
  return { table, columns, rows };
}

const schemaColumns = parseSchemaColumns();
const inserts = dump
  .split(/\r?\n/)
  .map(parseInsert)
  .filter(Boolean)
  .filter(insert => !skippedTables.has(insert.table));

const byTable = new Map(inserts.map(insert => [insert.table, insert]));
const importOrder = [
  "tb_user_info",
  "tb_role",
  "tb_storage_provider_config",
  "tb_about",
  "tb_page",
  "tb_menu",
  "tb_resource",
  "tb_outbox_event",
  "tb_website_config",
  "tb_article",
  "tb_content_asset",
  "tb_media_asset",
  "tb_user_auth",
  "tb_user_role",
  "tb_role_menu",
  "tb_role_resource"
];

const articleAssetUpdates = [];
const statements = [
  "-- Generated from a MySQL snapshot; do not commit this data file.",
  "PRAGMA foreign_keys = ON;"
];

for (const table of importOrder) {
  const insert = byTable.get(table);
  if (!insert) continue;
  const contentAssetIndex = table === "tb_article" ? insert.columns.indexOf("content_asset_id") : -1;
  const rows = insert.rows.map(row => {
    if (row.length !== insert.columns.length) {
      throw new Error(`${table}: expected ${insert.columns.length} values, got ${row.length}`);
    }
    if (contentAssetIndex >= 0 && !/^null$/i.test(row[contentAssetIndex])) {
      const idIndex = insert.columns.indexOf("id");
      articleAssetUpdates.push({ articleId: row[idIndex], assetId: row[contentAssetIndex] });
      row = [...row];
      row[contentAssetIndex] = "NULL";
    }
    return `(${row.map(sqliteLiteral).join(", ")})`;
  });
  statements.push(`INSERT INTO ${table} (${insert.columns.join(", ")}) VALUES\n${rows.join(",\n")};`);
}

for (const update of articleAssetUpdates) {
  statements.push(
    `UPDATE tb_article SET content_asset_id = ${sqliteLiteral(update.assetId)} WHERE id = ${sqliteLiteral(update.articleId)};`
  );
}

fs.writeFileSync(outputPath, `${statements.join("\n\n")}\n`, "utf8");
console.log(`Generated ${outputPath} from ${inserts.length} table inserts.`);
console.log(`Deferred article content asset links: ${articleAssetUpdates.length}.`);
