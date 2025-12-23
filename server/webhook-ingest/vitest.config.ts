import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { defineConfig } from "vitest/config";

const __dirname = dirname(fileURLToPath(import.meta.url));

export default defineConfig({
	test: {
		environment: "node",
		passWithNoTests: false,
		include: ["test/**/*.test.ts", "src/**/*.test.ts"],
		exclude: ["node_modules", "dist"],
		globals: true,

		// Coverage
		coverage: {
			enabled: false,
			provider: "v8",
			reporter: ["text", "html", "lcov", "json"],
			reportsDirectory: "./coverage",
			include: ["src/**/*.ts"],
			exclude: [
				"src/index.ts",
				"src/app.ts",
				"src/**/*.d.ts",
			],
			thresholds: {
				statements: 80,
				branches: 80,
				functions: 80,
				lines: 80,
				perFile: true,
			},
			clean: true,
			skipFull: false,
		},

		// Execution
		pool: "threads",
		isolate: true,
		testTimeout: 30000,
		hookTimeout: 60000,
		watch: false,
		reporters: ["verbose"],

		// Strict mode
		allowOnly: false,
		bail: 0,
		retry: 0,
	},
	resolve: {
		alias: {
			"@": resolve(__dirname, "./src"),
		},
	},
});
