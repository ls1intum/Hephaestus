import { defineConfig, devices } from "@playwright/test";

import { E2E_BASE_URL, E2E_PORT } from "./e2e/urls.ts";

export default defineConfig({
	testDir: "./e2e",
	testMatch: "**/*.spec.ts",
	fullyParallel: false,
	forbidOnly: !!process.env.CI,
	workers: process.env.CI ? 1 : undefined,
	// Retry for diagnostics, but fail CI on flaky tests.
	retries: process.env.CI ? 1 : 0,
	failOnFlakyTests: !!process.env.CI,
	timeout: 60_000,
	reporter: process.env.CI ? [["github"]] : [["list"]],
	use: {
		baseURL: E2E_BASE_URL,
		trace: "on-first-retry",
		screenshot: "only-on-failure",
	},
	projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
	webServer: {
		command: `pnpm run build && pnpm exec vite preview --host 127.0.0.1 --port ${E2E_PORT}`,
		url: E2E_BASE_URL,
		reuseExistingServer: !process.env.CI,
		timeout: 120_000,
	},
});
