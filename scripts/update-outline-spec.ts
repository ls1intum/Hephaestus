/**
 * Update the vendored Outline OpenAPI spec from the official repository.
 *
 * Usage: pnpm run outline:update-spec
 *
 * This script fetches Outline's maintained OpenAPI 3 specification and updates the local
 * vendored copy under server/src/main/resources/openapi/outline/spec3.yml. The spec is the
 * machine-readable contract the openapi-generator Maven plugin turns into the Outline client
 * models — vendoring it makes every refresh a deliberate, reviewable diff (mirroring
 * update-github-schema.ts / update-gitlab-schema.ts for the GraphQL vendors).
 *
 * Note: the hand-authored outline-supplement.yaml sitting beside spec3.yml is NOT touched by
 * this script — it carries the four endpoints Outline has not yet documented upstream and is
 * deleted once they land in spec3.yml.
 */

import { renameSync, statSync, unlinkSync, writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const SPEC_DIR = resolve(
	__dirname,
	"../server/src/main/resources/openapi/outline",
);
const SPEC_FILE = join(SPEC_DIR, "spec3.yml");
// Official Outline OpenAPI spec — the only trusted source.
const SPEC_URL =
	"https://raw.githubusercontent.com/outline/openapi/main/spec3.yml";

// Validation constants — the Outline spec is ~250KB.
const MIN_SIZE_BYTES = 50_000;
const MAX_SIZE_BYTES = 10_000_000;

/**
 * Validates that the content appears to be Outline's OpenAPI 3 spec before writing it to disk.
 * A security measure so we never persist arbitrary fetched content.
 */
function validateSpec(content: string): { valid: boolean; reason?: string } {
	if (content.length < MIN_SIZE_BYTES) {
		return {
			valid: false,
			reason: `Content too small (${content.length} bytes, minimum ${MIN_SIZE_BYTES})`,
		};
	}
	if (content.length > MAX_SIZE_BYTES) {
		return {
			valid: false,
			reason: `Content too large (${content.length} bytes, maximum ${MAX_SIZE_BYTES})`,
		};
	}
	if (content.includes("\0")) {
		return {
			valid: false,
			reason: "Content contains null bytes (possible binary data)",
		};
	}
	if (!/^openapi:\s*3\./m.test(content)) {
		return { valid: false, reason: "Content is not an OpenAPI 3 document" };
	}
	if (!/title:\s*Outline API/.test(content)) {
		return {
			valid: false,
			reason: "Content is missing the expected 'Outline API' title",
		};
	}
	if (!/^\s*schemas:/m.test(content) || !/^\s*Document:/m.test(content)) {
		return {
			valid: false,
			reason: "Content is missing expected Outline component schemas",
		};
	}
	return { valid: true };
}

async function main(): Promise<void> {
	console.log("Downloading Outline OpenAPI spec...");
	console.log(`Source: ${SPEC_URL}`);

	const response = await fetch(SPEC_URL);
	if (!response.ok) {
		console.error(
			`Failed to fetch spec: ${response.status} ${response.statusText}`,
		);
		process.exit(1);
	}

	const contentType = response.headers.get("content-type") || "";
	const acceptable = ["text/", "application/octet-stream", "application/yaml"];
	if (!acceptable.some((type) => contentType.includes(type))) {
		console.error(`Unexpected Content-Type: ${contentType}`);
		console.error(`Expected one of: ${acceptable.join(", ")}`);
		process.exit(1);
	}

	const content = await response.text();

	console.log("Validating spec content...");
	const validation = validateSpec(content);
	if (!validation.valid) {
		console.error(`Spec validation failed: ${validation.reason}`);
		process.exit(1);
	}

	const tempFile = `${SPEC_FILE}.tmp`;
	try {
		writeFileSync(tempFile, content, "utf-8");
		const stats = statSync(tempFile);
		console.log(`Downloaded ${Math.round(stats.size / 1024)}KB`);
		renameSync(tempFile, SPEC_FILE);
		console.log(`Spec updated successfully: ${SPEC_FILE}`);
		console.log(
			"\nTo regenerate models: cd server && ./mvnw -o clean compile -P'!quick'",
		);
	} catch (error) {
		try {
			unlinkSync(tempFile);
		} catch {
			// Ignore cleanup errors.
		}
		throw error;
	}
}

main().catch((error) => {
	console.error("Error updating spec:", error);
	process.exit(1);
});
