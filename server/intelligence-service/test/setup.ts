/**
 * Vitest Setup File - Test Database Configuration
 *
 * This runs before each test file and configures the test database connection.
 * Uses the globalThis variables set by global-setup.ts.
 *
 * The database is provided by either:
 * - Testcontainers (Docker available)
 * - Local PostgreSQL (cloud environments like Codex, HEPHAESTUS_DB_MODE=local)
 */

import { afterAll, afterEach, vi } from "vitest";

// Set NODE_ENV to test
process.env.NODE_ENV = "test";

// Configure DATABASE_URL from globalThis (set by global-setup.ts)
if (globalThis.__TEST_DATABASE_URL__) {
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
