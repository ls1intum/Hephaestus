import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { defineConfig } from "vitest/config";

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

export default defineConfig({
	test: {
		// ═══════════════════════════════════════════════════════════════════════════
		// Test Environment
		// ═══════════════════════════════════════════════════════════════════════════
		environment: "node",
		passWithNoTests: false, // FAIL if no tests - forces test coverage

		// ═══════════════════════════════════════════════════════════════════════════
		// Test Organization
		// ═══════════════════════════════════════════════════════════════════════════
		include: ["test/**/*.test.ts", "src/**/*.test.ts"],
		exclude: ["node_modules", "dist"],

		// ═══════════════════════════════════════════════════════════════════════════
		// Globals for cleaner tests
		// ═══════════════════════════════════════════════════════════════════════════
		globals: true,

		// ═══════════════════════════════════════════════════════════════════════════
		// Coverage Configuration - STRICT ENFORCEMENT
		// ═══════════════════════════════════════════════════════════════════════════
		coverage: {
			enabled: false, // Enable via --coverage flag
			provider: "v8",
			reporter: ["text", "html", "lcov", "json"],
			reportsDirectory: "./coverage",
			include: ["src/**/*.ts"],
			exclude: [
				// Generated files
				"src/shared/db/schema.ts",
				"src/shared/db/relations.ts",
				// Entry points (minimal logic)
				"src/index.ts",
				"src/app.ts",
				// Instrumentation
				"src/instrumentation.ts",
				// Type definitions
				"src/**/*.d.ts",
				// Route index files (just exports)
				"src/**/index.ts",
			],
			// STRICT thresholds - CI will FAIL if not met
			thresholds: {
				statements: 80,
				branches: 80,
				functions: 80,
				lines: 80,
				// Per-file thresholds prevent hiding low coverage
				perFile: true,
			},
			// Clean start every run
			clean: true,
			// Skip files with no executable code
			skipFull: false,
		},

		// ═══════════════════════════════════════════════════════════════════════════
		// Performance & Isolation
		// ═══════════════════════════════════════════════════════════════════════════
		pool: "threads",
		isolate: true,

		// ═══════════════════════════════════════════════════════════════════════════
		// Timeouts - Fail fast, don't hang
		// ═══════════════════════════════════════════════════════════════════════════
		testTimeout: 30000, // Increased for database operations
		hookTimeout: 60000, // Increased for container startup

		// ═══════════════════════════════════════════════════════════════════════════
		// Watch mode disabled for CI
		// ═══════════════════════════════════════════════════════════════════════════
		watch: false,

		// ═══════════════════════════════════════════════════════════════════════════
		// Reporter - verbose for debugging
		// ═══════════════════════════════════════════════════════════════════════════
		reporters: ["verbose"],

		// ═══════════════════════════════════════════════════════════════════════════
		// Setup files
		// ═══════════════════════════════════════════════════════════════════════════
		globalSetup: ["./test/global-setup.ts"],
		setupFiles: ["./test/setup.ts"],

		// ═══════════════════════════════════════════════════════════════════════════
		// Strict mode settings
		// ═══════════════════════════════════════════════════════════════════════════
		allowOnly: false, // Fail if .only is used (prevents accidental commits)
		bail: 0, // Don't stop on first failure - run all tests to see full picture
		retry: 0, // No retries - tests should be deterministic

		// ═══════════════════════════════════════════════════════════════════════════
		// Type checking - catch type errors during tests (AI SDK best practice)
		// NOTE: Disabled for test files as they use loose typing for flexibility.
		// Type errors in src/ are caught by npm run typecheck.
		// ═══════════════════════════════════════════════════════════════════════════
		typecheck: {
			enabled: false, // TODO: Enable after fixing test types
		},
	},
	resolve: {
		alias: {
			"@": resolve(__dirname, "./src"),
		},
	},
});
