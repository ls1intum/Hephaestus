import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { defineConfig } from "vitest/config";

const __dirname = dirname(fileURLToPath(import.meta.url));

/**
 * Unit Test Configuration
 *
 * Runs pure unit tests WITHOUT database setup.
 * Includes all *.test.ts except integration/ and architecture/ directories.
 */
export default defineConfig({
  test: {
    name: "unit",
    environment: "node",
    passWithNoTests: false,
    globals: true,
    include: ["test/**/*.test.ts"],
    exclude: [
      "node_modules",
      "dist",
      "test/integration/**",
      "test/architecture/**",
      "test/**/*.integration.test.ts",
    ],
    globalSetup: [],
    setupFiles: [],
    pool: "threads",
    isolate: true,
    testTimeout: 30000,
    hookTimeout: 60000,
    watch: false,
    reporters: ["verbose", "junit"],
    allowOnly: false,
    bail: 0,
    retry: 0,
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
