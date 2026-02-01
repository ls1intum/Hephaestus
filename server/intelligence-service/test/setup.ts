/**
 * Vitest Setup File - Test Database Configuration
 *
 * This runs before each test file and configures the test database connection.
 * Reads config from temp file written by global-setup.ts (since globalSetup
 * runs in a separate process from test workers).
 *
 * The database is provided by either:
 * - Testcontainers (Docker available)
 * - Local PostgreSQL (cloud environments like Codex, HEPHAESTUS_DB_MODE=local)
 */

import fs from "node:fs";
import path from "node:path";
import { afterAll, afterEach, vi } from "vitest";

// Set NODE_ENV to test
process.env.NODE_ENV = "test";

// ─────────────────────────────────────────────────────────────────────────────
// Read test config from temp file (written by global-setup.ts)
// ─────────────────────────────────────────────────────────────────────────────

const TEST_CONFIG_FILE = path.join(__dirname, ".test-config.json");

interface TestConfig {
	databaseUrl: string;
	usingLocalDb: boolean;
}

function readTestConfig(): TestConfig | null {
	try {
		const content = fs.readFileSync(TEST_CONFIG_FILE, "utf-8");
		return JSON.parse(content) as TestConfig;
	} catch {
		return null;
	}
}

// Configure DATABASE_URL from temp file or globalThis (fallback)
const testConfig = readTestConfig();
if (testConfig?.databaseUrl) {
	process.env.DATABASE_URL = testConfig.databaseUrl;
	globalThis.__TEST_DATABASE_URL__ = testConfig.databaseUrl;
	globalThis.__TEST_USING_LOCAL_DB__ = testConfig.usingLocalDb;
} else if (globalThis.__TEST_DATABASE_URL__) {
	// Fallback to globalThis (for same-process execution)
	process.env.DATABASE_URL = globalThis.__TEST_DATABASE_URL__;
}

afterEach(() => {
	vi.restoreAllMocks();
});

afterAll(() => {
	vi.resetAllMocks();
});

declare global {
	var __TEST_DATABASE_URL__: string | undefined;
	var __TEST_USING_LOCAL_DB__: boolean | undefined;
}
