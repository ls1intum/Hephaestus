/**
 * Update GitLab GraphQL schema via introspection
 *
 * Usage: npm run gitlab:update-schema -- --url <gitlab-url> [--token <pat>]
 *
 * This script fetches the GitLab GraphQL schema via introspection and converts it to SDL format.
 * Unlike GitHub (which provides a public schema file), GitLab requires introspection queries.
 *
 * Examples:
 *   npm run gitlab:update-schema -- --url https://gitlab.example.com
 *   npm run gitlab:update-schema -- --url https://gitlab.example.com --token glpat-xxx
 *
 * Authentication can be provided via:
 *   - CLI argument: --token <pat>
 *   - Environment variable: GITLAB_TOKEN
 *
 * Note: GitLab's GraphQL introspection is typically accessible without authentication,
 * but some self-hosted instances may require a Personal Access Token.
 */

import { mkdirSync, statSync, unlinkSync, writeFileSync, renameSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { buildClientSchema, getIntrospectionQuery, printSchema, type IntrospectionQuery } from "graphql";

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const SCHEMA_DIR = resolve(
	__dirname,
	"../server/application-server/src/main/resources/graphql/gitlab",
);
const SCHEMA_FILE = join(SCHEMA_DIR, "schema.gitlab.graphql");

// Validation constants - GitLab schema is typically ~2-5MB
const MIN_SIZE_BYTES = 500_000; // 500KB minimum
const MAX_SIZE_BYTES = 50_000_000; // 50MB maximum
const REQUEST_TIMEOUT_MS = 60_000; // 60 second timeout

// GraphQL schema validation patterns
const HAS_TYPE = /^type\s+\w+/m;
const HAS_INPUT = /^input\s+\w+/m;
const HAS_QUERY = /^type\s+Query\s*\{/m;

interface IntrospectionResponse {
	data?: IntrospectionQuery;
	errors?: Array<{ message: string; locations?: unknown }>;
}

function showHelp(): void {
	console.log(`Update GitLab GraphQL schema via introspection

Usage: npm run gitlab:update-schema -- --url <gitlab-url> [--token <pat>]

Options:
  --url <url>      GitLab instance URL (required)
  --token <pat>    Personal Access Token (optional, or use GITLAB_TOKEN env var)
  --help, -h       Show this help message

Examples:
  npm run gitlab:update-schema -- --url https://gitlab.example.com
  npm run gitlab:update-schema -- --url https://gitlab.example.com --token glpat-xxx
  GITLAB_TOKEN=glpat-xxx npm run gitlab:update-schema -- --url https://gitlab.example.com
`);
}

function parseArgs(): { url: string; token?: string } {
	const args = process.argv.slice(2);

	if (args.includes("--help") || args.includes("-h")) {
		showHelp();
		process.exit(0);
	}

	let url: string | undefined;
	let token: string | undefined = process.env.GITLAB_TOKEN?.trim() || undefined;

	for (let i = 0; i < args.length; i++) {
		const nextArg = args[i + 1];
		if (args[i] === "--url" && nextArg?.trim()) {
			url = nextArg.trim();
			i++;
		} else if (args[i] === "--token" && nextArg?.trim()) {
			token = nextArg.trim();
			i++;
		}
	}

	if (!url) {
		console.error("Error: --url is required\n");
		showHelp();
		process.exit(1);
	}

	return { url, token };
}

/**
 * Validates that the content appears to be a valid GraphQL schema.
 */
function validateGraphQLSchema(content: string): { valid: boolean; reason?: string } {
	if (content.length < MIN_SIZE_BYTES) {
		return { valid: false, reason: `Content too small (${content.length} bytes, minimum ${MIN_SIZE_BYTES})` };
	}

	if (content.length > MAX_SIZE_BYTES) {
		return { valid: false, reason: `Content too large (${content.length} bytes, maximum ${MAX_SIZE_BYTES})` };
	}

	if (!HAS_TYPE.test(content)) {
		return { valid: false, reason: "Content missing GraphQL type definitions" };
	}

	if (!HAS_INPUT.test(content)) {
		return { valid: false, reason: "Content missing GraphQL input definitions" };
	}

	if (!HAS_QUERY.test(content)) {
		return { valid: false, reason: "Content missing Query type definition" };
	}

	// Ensure content is valid UTF-8 text (no binary data or null bytes)
	if (content.includes("\0")) {
		return { valid: false, reason: "Content contains null bytes (possible binary data)" };
	}

	return { valid: true };
}

async function main(): Promise<void> {
	const { url, token } = parseArgs();

	// Normalize URL to ensure it ends with /api/graphql
	let graphqlEndpoint = url.replace(/\/+$/, "");
	if (!graphqlEndpoint.endsWith("/api/graphql")) {
		graphqlEndpoint = `${graphqlEndpoint}/api/graphql`;
	}

	console.log("Downloading GitLab GraphQL schema via introspection...");
	console.log(`Endpoint: ${graphqlEndpoint}`);
	console.log(`Authentication: ${token ? "Using Personal Access Token" : "None (public introspection)"}`);

	if (graphqlEndpoint.startsWith("http://")) {
		console.warn("Warning: Using unencrypted HTTP. Token will be transmitted in plaintext.");
	}

	const headers: Record<string, string> = {
		"Content-Type": "application/json",
	};

	if (token) {
		headers["Authorization"] = `Bearer ${token}`;
	}

	const introspectionQuery = getIntrospectionQuery();

	// Set up request timeout
	const controller = new AbortController();
	const timeout = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);

	let response: Response;
	try {
		response = await fetch(graphqlEndpoint, {
			method: "POST",
			headers,
			body: JSON.stringify({ query: introspectionQuery }),
			signal: controller.signal,
		});
	} catch (error) {
		if (error instanceof Error && error.name === "AbortError") {
			console.error(`Request timed out after ${REQUEST_TIMEOUT_MS / 1000} seconds`);
		} else {
			console.error("Failed to connect to GitLab:", error instanceof Error ? error.message : error);
		}
		process.exit(1);
	} finally {
		clearTimeout(timeout);
	}

	if (!response.ok) {
		console.error(`Failed to fetch schema: ${response.status} ${response.statusText}`);
		const body = await response.text();
		if (body) {
			console.error(`Response body: ${body.substring(0, 500)}`);
		}
		process.exit(1);
	}

	// Parse JSON response with explicit error handling
	const responseText = await response.text();
	let result: IntrospectionResponse;
	try {
		result = JSON.parse(responseText) as IntrospectionResponse;
	} catch {
		console.error("Failed to parse response as JSON. Server may have returned an error page.");
		console.error(`Response preview: ${responseText.substring(0, 500)}`);
		process.exit(1);
	}

	if (result.errors) {
		console.error("GraphQL introspection returned errors:");
		console.error(JSON.stringify(result.errors, null, 2));
		process.exit(1);
	}

	if (!result.data?.__schema) {
		console.error("Invalid introspection response: missing __schema");
		process.exit(1);
	}

	console.log("Converting introspection result to SDL format...");

	// Build schema from introspection result and convert to SDL
	let sdlContent: string;
	try {
		const schema = buildClientSchema(result.data);
		sdlContent = printSchema(schema);
	} catch (error) {
		console.error("Failed to convert introspection result to SDL:", error);
		process.exit(1);
	}

	// Validate the SDL content
	console.log("Validating schema content...");
	const validation = validateGraphQLSchema(sdlContent);

	if (!validation.valid) {
		console.error(`Schema validation failed: ${validation.reason}`);
		process.exit(1);
	}

	// Ensure schema directory exists
	mkdirSync(SCHEMA_DIR, { recursive: true });

	// Write to temp file first for atomic update
	const tempFile = `${SCHEMA_FILE}.tmp`;

	try {
		writeFileSync(tempFile, sdlContent, "utf-8");

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
