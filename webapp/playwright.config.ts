import { defineConfig, devices } from "@playwright/test";

const BASE_URL = process.env.E2E_BASE_URL ?? "http://localhost:4200";

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
		baseURL: BASE_URL,
		trace: "retain-on-failure",
		screenshot: "only-on-failure",
	},
	projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
	webServer: {
		command: "pnpm run dev",
		url: BASE_URL,
		reuseExistingServer: !process.env.CI,
		timeout: 120_000,
	},
});
