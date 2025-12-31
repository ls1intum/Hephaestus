import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { defineConfig } from "vitest/config";

const __dirname = dirname(fileURLToPath(import.meta.url));

/**
 * Integration Test Configuration
 *
 * Runs integration tests WITH database setup (Testcontainers):
 * - Includes *.integration.test.ts files
 * - Includes tests that use database fixtures (createTestFixtures, etc.)
 * - Uses globalSetup for Testcontainers PostgreSQL
 *
 * Usage: npx vitest run --config vitest.config.integration.ts
 */
export default defineConfig({
  test: {
    name: "integration",
    environment: "node",
    passWithNoTests: false,
    globals: true,

    // Integration tests - require database
    include: [
      // Explicit integration tests
      "test/integration/*.integration.test.ts",
      "test/detector/detector.integration.test.ts",
      // Tests that use database fixtures (createTestFixtures, etc.)
      "test/vote/vote.test.ts",
      "test/tools/tools.test.ts",
      "test/tools/registry.test.ts",
      "test/security/authorization.test.ts",
      "test/documents/*.test.ts",
    ],
    exclude: ["node_modules", "dist"],

    // Database setup with Testcontainers
    globalSetup: ["./test/global-setup.ts"],
    setupFiles: ["./test/setup.ts"],

    // Execution - integration tests need more time
    pool: "threads",
    isolate: true,
    testTimeout: 60000,
    hookTimeout: 120000,
    watch: false,
    reporters: ["verbose", "junit"],

    // Strict mode
    allowOnly: false,
    bail: 0,
    retry: 0,

    // CI integration - JUnit reporter for GitHub Actions test summaries
    outputFile: {
      junit: "./test-results/junit-integration.xml",
    },
  },
  resolve: {
    alias: {
      "@": resolve(__dirname, "./src"),
    },
  },
});
