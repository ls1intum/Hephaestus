import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { defineConfig } from "vitest/config";

const __dirname = dirname(fileURLToPath(import.meta.url));

/**
 * Unit Test Configuration
 *
 * Runs pure unit tests WITHOUT database setup.
 * Uses glob patterns: includes all *.test.ts, excludes integration/arch tests.
 *
 * Usage: npx vitest run --config vitest.config.unit.ts
 */
export default defineConfig({
  test: {
    name: "unit",
    environment: "node",
    passWithNoTests: false,
    globals: true,

    // Glob pattern: all *.test.ts except integration and architecture tests
    include: ["test/**/*.test.ts"],
    exclude: [
      "node_modules",
      "dist",
      "test/**/*.integration.test.ts",
      "test/**/*.arch.test.ts",
    ],

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
