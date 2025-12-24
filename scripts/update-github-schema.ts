/**
 * Update GitHub GraphQL schema from official docs
 *
 * This script downloads the latest schema and optionally triggers code generation.
 *
 * Usage:
 *   npx tsx scripts/update-github-schema.ts [--generate]
 */

import { execSync } from "node:child_process";
import { existsSync, statSync, unlinkSync, writeFileSync } from "node:fs";
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
const MIN_SIZE_BYTES = 1_000_000; // Schema should be ~1.4MB

async function downloadSchema(): Promise<void> {
	console.log("📥 Downloading GitHub GraphQL schema...");

	const response = await fetch(SCHEMA_URL);
	if (!response.ok) {
		throw new Error(`Failed to download schema: ${response.status} ${response.statusText}`);
	}

	const content = await response.text();
	const tempFile = `${SCHEMA_FILE}.tmp`;

	writeFileSync(tempFile, content, "utf-8");

	// Verify download was successful (file should be > 1MB)
	const stats = statSync(tempFile);
	if (stats.size < MIN_SIZE_BYTES) {
		unlinkSync(tempFile);
		throw new Error(
			`Downloaded file is too small (${stats.size} bytes). Schema should be ~1.4MB`,
		);
	}

	// Atomic rename
	const { renameSync } = await import("node:fs");
	renameSync(tempFile, SCHEMA_FILE);

	const sizeStr =
		stats.size > 1_048_576
			? `${Math.round(stats.size / 1_048_576)}MB`
			: `${Math.round(stats.size / 1024)}KB`;

	console.log(`✅ Schema downloaded successfully (${sizeStr})`);
}

function generateCode(): void {
	console.log("");
	console.log("🔨 Generating GraphQL client code...");

	const serverDir = resolve(__dirname, "../server/application-server");
	execSync("./mvnw graphql-codegen:generate -q", {
		cwd: serverDir,
		stdio: "inherit",
	});

	console.log("✅ Code generation complete");
	console.log("   Output: target/generated-sources/graphql");
}

async function main(): Promise<void> {
	const args = process.argv.slice(2);
	const shouldGenerate = args.includes("--generate");

	try {
		await downloadSchema();

		if (shouldGenerate) {
			generateCode();
		}

		console.log("");
		console.log(`📁 Schema location: ${SCHEMA_FILE}`);
		console.log("");
		console.log("To regenerate client code, run:");
		console.log("  cd server/application-server && ./mvnw graphql-codegen:generate");
	} catch (error) {
		console.error(`❌ ${error instanceof Error ? error.message : String(error)}`);
		process.exit(1);
	}
}

main();
