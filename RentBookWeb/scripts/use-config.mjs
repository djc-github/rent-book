import { copyFileSync, existsSync, mkdirSync, readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const env = process.argv[2] || "local";
const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const source = resolve(root, `config/app-config.${env}.json`);
const target = resolve(root, "public/config/app-config.json");

if (!existsSync(source)) {
  const allowed = ["local", "test", "prod"].join(", ");
  throw new Error(`Unknown frontend env "${env}". Use one of: ${allowed}`);
}

JSON.parse(readFileSync(source, "utf8"));
mkdirSync(dirname(target), { recursive: true });
copyFileSync(source, target);
console.log(`Frontend config: ${env} -> public/config/app-config.json`);
