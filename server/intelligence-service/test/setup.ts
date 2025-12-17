/**
 * Vitest Global Setup
 *
 * This file runs before all tests and sets up the test environment.
 * Environment variables are loaded from .env.test via @/env module.
 * Keep it minimal - heavy setup should be in beforeAll() hooks.
 */

import { afterAll, vi } from "vitest";

// Set NODE_ENV to test before importing @/env to ensure .env.test is loaded
process.env.NODE_ENV = "test";

// Import env to trigger dotenv loading from .env.test
// This validates all required environment variables are present
import "@/env";

afterAll(() => {
	// Cleanup
	vi.restoreAllMocks();
});

// Global type augmentation for vitest globals
declare module "vitest" {
	// Extend if needed
}
