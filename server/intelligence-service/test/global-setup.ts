/**
 * Vitest Global Setup - Testcontainers PostgreSQL + Drizzle Schema
 *
 * Database Selection Strategy (matches application-server pattern):
 * 1. HEPHAESTUS_DB_MODE=local → Use local/external PostgreSQL (cloud environments)
 * 2. Docker available → Start fresh Testcontainer
 * 3. Docker unavailable → Fall back to local PostgreSQL
 *
 * Environment Variables:
 * - HEPHAESTUS_DB_MODE: Set to 'local' to force local database mode
 * - HEPHAESTUS_TEST_DATABASE_URL: Custom connection URL for local mode
 * - HEPHAESTUS_TEST_DB_USER: Custom username (default: test)
 * - HEPHAESTUS_TEST_DB_PASSWORD: Custom password (default: test)
 *
 * IMPORTANT: This globalSetup runs in a separate process from test workers.
 * Data is shared via a temp file that setupFiles reads.
 */

import fs from "node:fs";
import path from "node:path";
import { PostgreSqlContainer, type StartedPostgreSqlContainer } from "@testcontainers/postgresql";
import { drizzle } from "drizzle-orm/node-postgres";
import { Client, Pool } from "pg";

import * as schema from "../src/shared/db/schema";

// ─────────────────────────────────────────────────────────────────────────────
// IPC: Share data between globalSetup and test workers via temp file
// ─────────────────────────────────────────────────────────────────────────────

const TEST_CONFIG_FILE = path.join(__dirname, ".test-config.json");

function writeTestConfig(config: { databaseUrl: string; usingLocalDb: boolean }): void {
	fs.writeFileSync(TEST_CONFIG_FILE, JSON.stringify(config), "utf-8");
}

function cleanupTestConfig(): void {
	try {
		fs.unlinkSync(TEST_CONFIG_FILE);
	} catch {
		// File may not exist, that's fine
	}
}

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────

const ENV_DB_MODE = "HEPHAESTUS_DB_MODE";
const ENV_TEST_DATABASE_URL = "HEPHAESTUS_TEST_DATABASE_URL";
const ENV_TEST_DB_USER = "HEPHAESTUS_TEST_DB_USER";
const ENV_TEST_DB_PASSWORD = "HEPHAESTUS_TEST_DB_PASSWORD";

const DEFAULT_TEST_DATABASE_URL = "postgresql://localhost:5432/hephaestus_test";
const DEFAULT_TEST_USER = "test";
const DEFAULT_TEST_PASSWORD = "test";

// ─────────────────────────────────────────────────────────────────────────────
// State
// ─────────────────────────────────────────────────────────────────────────────

let container: StartedPostgreSqlContainer | null = null;
let pool: Pool | null = null;
let usingLocalDatabase = false;

// ─────────────────────────────────────────────────────────────────────────────
// Docker Detection
// ─────────────────────────────────────────────────────────────────────────────

async function isDockerAvailable(): Promise<boolean> {
	try {
		// Try to import and check Docker availability
		const { execSync } = await import("node:child_process");
		execSync("docker info", { stdio: "ignore", timeout: 5000 });
		return true;
	} catch {
		return false;
	}
}

// ─────────────────────────────────────────────────────────────────────────────
// Schema Push
// ─────────────────────────────────────────────────────────────────────────────

async function pushDrizzleSchema(connectionString: string): Promise<void> {
	const schemaPool = new Pool({ connectionString, max: 1 });
	const db = drizzle(schemaPool, { schema, casing: "snake_case" });

	// Use require() for ESM compatibility with drizzle-kit/api
	// See: https://github.com/drizzle-team/drizzle-orm/issues/2853
	const { pushSchema } = require("drizzle-kit/api") as typeof import("drizzle-kit/api");

	console.log("📦 Pushing Drizzle schema to test database...");

	const result = await pushSchema(schema, db as any);

	if (result.warnings && result.warnings.length > 0) {
		console.log("⚠️  Schema push warnings:", result.warnings);
	}

	await result.apply();
	console.log("✅ Schema applied successfully");

	await schemaPool.end();
}

/**
 * Verify that the schema exists in the database.
 * For local mode, the schema is managed by application-server's Liquibase.
 */
async function verifySchemaExists(connectionString: string): Promise<void> {
	const client = new Client({ connectionString });
	try {
		await client.connect();
		// Check for a core table to verify schema exists
		const result = await client.query(`
			SELECT EXISTS (
				SELECT FROM information_schema.tables 
				WHERE table_schema = 'public' 
				AND table_name = 'chat_thread'
			)
		`);
		if (!result.rows[0]?.exists) {
			throw new Error("Schema not found - ensure application-server migrations have been run");
		}
		console.log("✅ Schema verified in local database");
	} finally {
		await client.end();
	}
}

// ─────────────────────────────────────────────────────────────────────────────
// Local Database Mode
// ─────────────────────────────────────────────────────────────────────────────

function useLocalDatabase(): boolean {
	const dbMode = process.env[ENV_DB_MODE]?.toLowerCase();
	return dbMode === "local";
}

function getLocalConnectionString(): string {
	const baseUrl = process.env[ENV_TEST_DATABASE_URL] || DEFAULT_TEST_DATABASE_URL;
	const user = process.env[ENV_TEST_DB_USER] || DEFAULT_TEST_USER;
	const password = process.env[ENV_TEST_DB_PASSWORD] || DEFAULT_TEST_PASSWORD;

	// Parse and rebuild URL with credentials
	const url = new URL(baseUrl);
	url.username = user;
	url.password = password;
	return url.toString();
}

async function verifyLocalDatabase(connectionString: string): Promise<void> {
	const client = new Client({ connectionString });
	try {
		await client.connect();
		await client.query("SELECT 1");
		console.log("✅ Connected to local PostgreSQL instance");
	} catch (error) {
		const message = buildLocalConnectionError(connectionString, error);
		throw new Error(message);
	} finally {
		await client.end();
	}
}

function buildLocalConnectionError(connectionString: string, error: unknown): string {
	const reason =
		process.env[ENV_DB_MODE] === "local"
			? "HEPHAESTUS_DB_MODE=local (explicit)"
			: "Docker unavailable";

	return `
Failed to connect to local PostgreSQL instance.

Connection: ${connectionString.replace(/:[^:@]+@/, ":****@")}
Reason: ${reason}
Error: ${error instanceof Error ? error.message : String(error)}

To resolve this issue:
  1. For cloud environments (Codex, GitHub Copilot Tasks):
     Run 'scripts/codex-setup.sh' to set up local PostgreSQL
     Then run 'scripts/local-postgres.sh start'

  2. For local development:
     Install and start Docker to use Testcontainers automatically

Environment variables:
  ${ENV_DB_MODE}: Set to 'local' to force local database mode
  ${ENV_TEST_DATABASE_URL}: Custom PostgreSQL URL (default: ${DEFAULT_TEST_DATABASE_URL})
  ${ENV_TEST_DB_USER}: Database user (default: ${DEFAULT_TEST_USER})
  ${ENV_TEST_DB_PASSWORD}: Database password
`.trim();
}

async function setupLocalDatabase(): Promise<string> {
	const connectionString = getLocalConnectionString();
	console.log("\n🏠 Using local PostgreSQL instance (no Docker)...");
	await verifyLocalDatabase(connectionString);
	// For local mode, schema is managed by application-server's Liquibase
	// We only verify it exists, we don't push it
	await verifySchemaExists(connectionString);
	usingLocalDatabase = true;
	return connectionString;
}

// ─────────────────────────────────────────────────────────────────────────────
// Docker Container Mode
// ─────────────────────────────────────────────────────────────────────────────

async function setupDockerContainer(): Promise<string> {
	console.log("\n🐘 Starting PostgreSQL Testcontainer...");

	container = await new PostgreSqlContainer("postgres:17")
		.withDatabase("hephaestus_test")
		.withUsername("test")
		.withPassword("test")
		.start();

	const connectionString = container.getConnectionUri();

	console.log(
		`✅ PostgreSQL container started at ${container.getHost()}:${container.getMappedPort(5432)}`,
	);

	await pushDrizzleSchema(connectionString);

	return connectionString;
}

// ─────────────────────────────────────────────────────────────────────────────
// Main Setup/Teardown
// ─────────────────────────────────────────────────────────────────────────────

export async function setup(): Promise<void> {
	let connectionString: string;

	// Strategy 1: Explicit local mode
	if (useLocalDatabase()) {
		connectionString = await setupLocalDatabase();
	}
	// Strategy 2: Try Docker, fall back to local
	else if (await isDockerAvailable()) {
		try {
			connectionString = await setupDockerContainer();
		} catch (dockerError) {
			console.warn(
				"⚠️  Failed to start Testcontainer, falling back to local database:",
				dockerError,
			);
			connectionString = await setupLocalDatabase();
		}
	}
	// Strategy 3: No Docker, use local
	else {
		console.log("⚠️  Docker is not available, using local PostgreSQL instance");
		connectionString = await setupLocalDatabase();
	}

	pool = new Pool({
		connectionString,
		max: 10,
		idleTimeoutMillis: 30_000,
	});

	// Write config to temp file for test workers to read
	writeTestConfig({
		databaseUrl: connectionString,
		usingLocalDb: usingLocalDatabase,
	});

	// Keep these for backwards compatibility with any code that uses them
	globalThis.__TEST_POSTGRES_CONTAINER__ = container ?? undefined;
	globalThis.__TEST_POSTGRES_POOL__ = pool ?? undefined;
	globalThis.__TEST_DATABASE_URL__ = connectionString;
	globalThis.__TEST_USING_LOCAL_DB__ = usingLocalDatabase;
}

export async function teardown(): Promise<void> {
	if (usingLocalDatabase) {
		console.log("\n🏠 Closing local database connection...");
	} else {
		console.log("\n🛑 Stopping PostgreSQL Testcontainer...");
	}

	if (pool) {
		await pool.end();
	}

	if (container && !usingLocalDatabase) {
		await container.stop();
	}

	// Clean up temp config file
	cleanupTestConfig();

	console.log("✅ Cleanup complete");
}

declare global {
	var __TEST_POSTGRES_CONTAINER__: StartedPostgreSqlContainer | undefined;
	var __TEST_POSTGRES_POOL__: Pool | undefined;
	var __TEST_DATABASE_URL__: string | undefined;
	var __TEST_USING_LOCAL_DB__: boolean | undefined;
}
