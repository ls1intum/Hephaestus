/**
 * Update GitHub GraphQL schema from official docs
 *
 * Usage: npm run github:update-schema
 */

import { statSync, unlinkSync, writeFileSync, renameSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const SCHEMA_DIR = resolve(
	__dirname,
	"../server/application-server/src/main/resources/graphql/github",
);
const SCHEMA_FILE = join(SCHEMA_DIR, "schema.github.graphql");
const SCHEMA_URL = "https://docs.github.com/public/fpt/schema.docs.graphql";
const MIN_SIZE_BYTES = 1_000_000;

async function main(): Promise<void> {
	console.log("📥 Downloading GitHub GraphQL schema...");

	const response = await fetch(SCHEMA_URL);
	if (!response.ok) {
		console.error(`❌ Failed: ${response.status} ${response.statusText}`);
		process.exit(1);
	}

	const content = await response.text();
	const tempFile = `${SCHEMA_FILE}.tmp`;
	writeFileSync(tempFile, content, "utf-8");

	const stats = statSync(tempFile);
	if (stats.size < MIN_SIZE_BYTES) {
		unlinkSync(tempFile);
		console.error(`❌ File too small (${stats.size} bytes)`);
		process.exit(1);
	}

	renameSync(tempFile, SCHEMA_FILE);
	console.log(`✅ Schema updated (${Math.round(stats.size / 1_048_576)}MB)`);
	console.log(`   ${SCHEMA_FILE}`);
	console.log(`\nTo regenerate: cd server/application-server && ./mvnw compile -DskipTests`);
}

main();
