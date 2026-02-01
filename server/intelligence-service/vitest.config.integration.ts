import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { defineConfig } from "vitest/config";

const __dirname = dirname(fileURLToPath(import.meta.url));

/**
 * Integration Test Configuration
 *
 * Runs integration tests WITH database setup (Testcontainers).
 * Pattern: *.integration.test.ts
 */
export default defineConfig({
  test: {
    name: "integration",
    environment: "node",
    passWithNoTests: false,
    globals: true,
    include: ["test/**/*.integration.test.ts"],
    exclude: ["node_modules", "dist"],
    globalSetup: ["./test/global-setup.ts"],
    setupFiles: ["./test/setup.ts"],
    pool: "threads",
    isolate: true,
    testTimeout: 60000,
    hookTimeout: 120000,
    watch: false,
    reporters: ["verbose", "junit"],
    allowOnly: false,
    bail: 0,
    retry: 0,
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
