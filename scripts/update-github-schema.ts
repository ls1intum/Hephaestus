/**
 * Update GitHub GraphQL schema from official docs
 *
 * Usage: npm run github:update-schema
 *
 * This script fetches the official GitHub GraphQL schema and updates the local copy.
 * It includes validation to ensure the downloaded content is a valid GraphQL schema.
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
// Official GitHub GraphQL schema URL - this is the only trusted source
const SCHEMA_URL = "https://docs.github.com/public/fpt/schema.docs.graphql";

// Validation constants
const MIN_SIZE_BYTES = 1_000_000; // 1MB - GitHub schema is typically ~15MB
const MAX_SIZE_BYTES = 50_000_000; // 50MB - reasonable upper limit to prevent DoS

// GraphQL schema validation patterns
// The GitHub schema always starts with directive definitions
const STARTS_WITH_DOC_COMMENT = /^\s*"""/;
const HAS_DIRECTIVE = /directive\s+@/;
const HAS_TYPE = /^type\s+\w+/m;
const HAS_INPUT = /^input\s+\w+/m;

/**
 * Validates that the content appears to be a valid GraphQL schema.
 * This is a security measure to ensure we don't write arbitrary content to disk.
 */
function validateGraphQLSchema(content: string): { valid: boolean; reason?: string } {
	// Check for minimum content length (quick check before regex)
	if (content.length < MIN_SIZE_BYTES) {
		return { valid: false, reason: `Content too small (${content.length} bytes, minimum ${MIN_SIZE_BYTES})` };
	}

	// Check for maximum content length to prevent DoS
	if (content.length > MAX_SIZE_BYTES) {
		return { valid: false, reason: `Content too large (${content.length} bytes, maximum ${MAX_SIZE_BYTES})` };
	}

	// Verify content starts with expected GraphQL patterns
	if (!STARTS_WITH_DOC_COMMENT.test(content)) {
		return { valid: false, reason: "Content does not start with expected GraphQL documentation comment" };
	}

	// Verify essential GraphQL constructs are present
	const hasDirective = HAS_DIRECTIVE.test(content);
	const hasType = HAS_TYPE.test(content);
	const hasInput = HAS_INPUT.test(content);

	if (!hasDirective) {
		return { valid: false, reason: "Content missing GraphQL directive definitions" };
	}

	if (!hasType) {
		return { valid: false, reason: "Content missing GraphQL type definitions" };
	}

	if (!hasInput) {
		return { valid: false, reason: "Content missing GraphQL input definitions" };
	}

	// Check for known GitHub schema markers
	if (!content.includes("@preview") && !content.includes("@possibleTypes")) {
		return { valid: false, reason: "Content missing expected GitHub-specific directives" };
	}

	// Ensure content is valid UTF-8 text (no binary data or null bytes)
	if (content.includes("\0")) {
		return { valid: false, reason: "Content contains null bytes (possible binary data)" };
	}

	return { valid: true };
}

async function main(): Promise<void> {
	console.log("Downloading GitHub GraphQL schema...");
	console.log(`Source: ${SCHEMA_URL}`);

	const response = await fetch(SCHEMA_URL);

	if (!response.ok) {
		console.error(`Failed to fetch schema: ${response.status} ${response.statusText}`);
		process.exit(1);
	}

	// Validate Content-Type header
	// GitHub returns application/octet-stream for the schema file download
	const contentType = response.headers.get("content-type") || "";
	const acceptableContentTypes = [
		"text/",
		"application/graphql",
		"application/octet-stream", // GitHub's default for file downloads
	];
	if (!acceptableContentTypes.some((type) => contentType.includes(type))) {
		console.error(`Unexpected Content-Type: ${contentType}`);
		console.error(`Expected one of: ${acceptableContentTypes.join(", ")}`);
		process.exit(1);
	}

	const content = await response.text();

	// Validate the content before writing to disk
	console.log("Validating schema content...");
	const validation = validateGraphQLSchema(content);

	if (!validation.valid) {
		console.error(`Schema validation failed: ${validation.reason}`);
		process.exit(1);
	}

	// Write to temp file first for atomic update
	const tempFile = `${SCHEMA_FILE}.tmp`;

	try {
		writeFileSync(tempFile, content, "utf-8");

		const stats = statSync(tempFile);
		console.log(`Downloaded ${Math.round(stats.size / 1_048_576)}MB`);

		// Atomic rename
		renameSync(tempFile, SCHEMA_FILE);

		console.log(`Schema updated successfully: ${SCHEMA_FILE}`);
		console.log("\nTo regenerate types: cd server/application-server && ./mvnw compile -DskipTests");
	} catch (error) {
		// Clean up temp file on error
		try {
			unlinkSync(tempFile);
		} catch {
			// Ignore cleanup errors
		}
		throw error;
	}
}

main().catch((error) => {
	console.error("Error updating schema:", error);
	process.exit(1);
});
