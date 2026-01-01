import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { defineConfig } from "vitest/config";

const __dirname = dirname(fileURLToPath(import.meta.url));

/**
 * Architecture Test Configuration
 *
 * Runs architecture tests WITHOUT database setup.
 * These tests enforce architectural rules and coding standards.
 *
 * Usage: npx vitest run --config vitest.config.arch.ts
 */
export default defineConfig({
  test: {
    name: "architecture",
    environment: "node",
    passWithNoTests: false,
    globals: true,

    // Architecture tests only
    include: ["test/**/*.arch.test.ts"],
    exclude: ["node_modules", "dist"],

    // NO database setup for architecture tests
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
      junit: "./test-results/junit-arch.xml",
    },
  },
  resolve: {
    alias: {
      "@": resolve(__dirname, "./src"),
    },
  },
});
