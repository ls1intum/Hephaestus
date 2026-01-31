/**
 * Update GitLab GraphQL schema via introspection
 *
 * Usage: npm run gitlab:update-schema [-- --url <other-gitlab-url>]
 *
 * Fetches the GitLab GraphQL schema via introspection and converts it to SDL format.
 * Default instance: https://gitlab.lrz.de (public introspection, no auth needed)
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
const DEFAULT_GITLAB_URL = "https://gitlab.lrz.de";

// Validation constants
const MIN_SIZE_BYTES = 500_000; // 500KB minimum
const MAX_SIZE_BYTES = 50_000_000; // 50MB maximum
const REQUEST_TIMEOUT_MS = 60_000; // 60 seconds

// GraphQL schema validation patterns
const HAS_TYPE = /^type\s+\w+/m;
const HAS_INPUT = /^input\s+\w+/m;
const HAS_QUERY = /^type\s+Query\s*\{/m;

interface IntrospectionResponse {
	data?: IntrospectionQuery;
	errors?: Array<{ message: string; locations?: unknown }>;
}

function parseArgs(): { url: string; token?: string } {
	const args = process.argv.slice(2);

	if (args.includes("--help") || args.includes("-h")) {
		console.log(`Update GitLab GraphQL schema via introspection

Usage: npm run gitlab:update-schema [-- --url <gitlab-url>] [-- --token <pat>]

Default: ${DEFAULT_GITLAB_URL} (public introspection)

Options:
  --url <url>    GitLab instance URL (default: ${DEFAULT_GITLAB_URL})
  --token <pat>  Personal Access Token (rarely needed)
`);
		process.exit(0);
	}

	let url = DEFAULT_GITLAB_URL;
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

	return { url, token };
}

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

	if (content.includes("\0")) {
		return { valid: false, reason: "Content contains null bytes (possible binary data)" };
	}

	return { valid: true };
}

async function main(): Promise<void> {
	const { url, token } = parseArgs();

	// Normalize URL
	let graphqlEndpoint = url.replace(/\/+$/, "");
	if (!graphqlEndpoint.endsWith("/api/graphql")) {
		graphqlEndpoint = `${graphqlEndpoint}/api/graphql`;
	}

	console.log("Downloading GitLab GraphQL schema...");
	console.log(`Source: ${graphqlEndpoint}`);

	const headers: Record<string, string> = {
		"Content-Type": "application/json",
	};

	if (token) {
		headers["Authorization"] = `Bearer ${token}`;
	}

	const controller = new AbortController();
	const timeout = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);

	let response: Response;
	try {
		response = await fetch(graphqlEndpoint, {
			method: "POST",
			headers,
			body: JSON.stringify({ query: getIntrospectionQuery() }),
			signal: controller.signal,
		});
	} catch (error) {
		if (error instanceof Error && error.name === "AbortError") {
			console.error(`Request timed out after ${REQUEST_TIMEOUT_MS / 1000} seconds`);
		} else {
			console.error("Failed to connect:", error instanceof Error ? error.message : error);
		}
		process.exit(1);
	} finally {
		clearTimeout(timeout);
	}

	if (!response.ok) {
		console.error(`Failed to fetch schema: ${response.status} ${response.statusText}`);
		process.exit(1);
	}

	const responseText = await response.text();
	let result: IntrospectionResponse;
	try {
		result = JSON.parse(responseText) as IntrospectionResponse;
	} catch {
		console.error("Failed to parse response as JSON");
		console.error(`Response preview: ${responseText.substring(0, 200)}`);
		process.exit(1);
	}

	if (result.errors) {
		console.error("GraphQL errors:", JSON.stringify(result.errors, null, 2));
		process.exit(1);
	}

	if (!result.data?.__schema) {
		console.error("Invalid response: missing __schema");
		process.exit(1);
	}

	console.log("Converting to SDL...");

	let sdlContent: string;
	try {
		const schema = buildClientSchema(result.data);
		sdlContent = printSchema(schema);
	} catch (error) {
		console.error("Failed to convert to SDL:", error);
		process.exit(1);
	}

	console.log("Validating schema...");
	const validation = validateGraphQLSchema(sdlContent);
	if (!validation.valid) {
		console.error(`Validation failed: ${validation.reason}`);
		process.exit(1);
	}

	mkdirSync(SCHEMA_DIR, { recursive: true });

	const tempFile = `${SCHEMA_FILE}.tmp`;
	try {
		writeFileSync(tempFile, sdlContent, "utf-8");
		const stats = statSync(tempFile);
		console.log(`Downloaded ${Math.round(stats.size / 1_048_576)}MB`);
		renameSync(tempFile, SCHEMA_FILE);
		console.log(`Schema updated: ${SCHEMA_FILE}`);
	} catch (error) {
		try { unlinkSync(tempFile); } catch { /* ignore */ }
		throw error;
	}
}

main().catch((error) => {
	console.error("Error:", error);
	process.exit(1);
});
