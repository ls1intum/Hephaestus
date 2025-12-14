/**
 * Vitest Global Setup
 *
 * This file runs before all tests and sets up the test environment.
 * Keep it minimal - heavy setup should be in beforeAll() hooks.
 */

import { afterAll, beforeAll, vi } from "vitest";

// Mock environment variables for testing
beforeAll(() => {
	// Set required env vars for tests (prevents validation errors)
	process.env.PORT = "3001";
	process.env.LOG_LEVEL = "silent";
	process.env.DATABASE_URL = "postgresql://test:test@localhost:5432/test";
});

afterAll(() => {
	// Cleanup
	vi.restoreAllMocks();
});

// Global type augmentation for vitest globals
declare module "vitest" {
	// Extend if needed
}
