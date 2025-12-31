import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { defineConfig } from "vitest/config";

const __dirname = dirname(fileURLToPath(import.meta.url));

/**
 * Unit Test Configuration
 *
 * Runs pure unit tests WITHOUT database setup:
 * - Excludes *.integration.test.ts files
 * - Excludes tests that use database fixtures (createTestFixtures, etc.)
 * - No globalSetup/setupFiles (no Testcontainers)
 *
 * Usage: npx vitest run --config vitest.config.unit.ts
 */
export default defineConfig({
  test: {
    name: "unit",
    environment: "node",
    passWithNoTests: false,
    globals: true,

    // Pure unit tests only - no database dependencies
    include: [
      "test/chat/transformer.test.ts",
      "test/chat/edge-cases.test.ts",
      "test/chat/chat-shared.test.ts",
      "test/utils/error.test.ts",
      "test/shared/ai/error-handler.test.ts",
      "test/tools/definitions.test.ts",
      "test/tools/merger.test.ts",
      "test/tools/context.test.ts",
      "test/tools/constants.test.ts",
      "test/streaming/*.test.ts",
      "test/prompts/*.test.ts",
      "test/detector/detector.test.ts",
      "test/mentor/tools/*.test.ts",
    ],
    exclude: ["node_modules", "dist"],

    // NO database setup for pure unit tests
    globalSetup: [],
    setupFiles: [],

    // Execution
    pool: "threads",
    isolate: true,
    testTimeout: 30000,
    hookTimeout: 60000,
    watch: false,
    reporters: ["verbose", "junit"],

    // Strict mode
    allowOnly: false,
    bail: 0,
    retry: 0,

    // CI integration - JUnit reporter for GitHub Actions test summaries
    outputFile: {
      junit: "./test-results/junit-unit.xml",
    },
  },
  resolve: {
    alias: {
      "@": resolve(__dirname, "./src"),
    },
  },
});
