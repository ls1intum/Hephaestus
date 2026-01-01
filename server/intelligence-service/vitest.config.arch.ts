import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { defineConfig } from "vitest/config";

const __dirname = dirname(fileURLToPath(import.meta.url));

/**
 * Architecture Test Configuration
 *
 * Runs architecture tests WITHOUT database setup.
 * Pattern: *.arch.test.ts
 */
export default defineConfig({
  test: {
    name: "architecture",
    environment: "node",
    passWithNoTests: false,
    globals: true,
    include: ["test/**/*.arch.test.ts"],
    exclude: ["node_modules", "dist"],
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
      junit: "./test-results/junit-arch.xml",
    },
  },
  resolve: {
    alias: {
      "@": resolve(__dirname, "./src"),
    },
  },
});
