import { writeFileSync } from "node:fs";
import { resolve } from "node:path";
import YAML from "yaml";
import { openAPIConfig } from "@/lib/configure-open-api";
import app from "../src/app";

async function main() {
	const yaml = YAML.stringify(app.getOpenAPI31Document(openAPIConfig));
	const outPath = resolve(process.cwd(), "openapi.yaml");
	writeFileSync(outPath, yaml, { encoding: "utf-8" });
	console.log(`OpenAPI spec written to ${outPath}`);
}

main().catch((err) => {
	console.error("Failed to export OpenAPI spec:", err);
	process.exit(1);
});
