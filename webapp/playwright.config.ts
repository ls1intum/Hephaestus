import { defineConfig, devices } from "@playwright/test";

// End-to-end tests that drive the real SPA against a running backend, authenticating via the
// passwordless dev-login (no OAuth IdP). See e2e/README.md for the one-time backend setup.
//
// Everything runs over plain http://localhost — a browser "secure context" where Chromium honours
// plain Secure cookies — so the backend must boot with `hephaestus.auth.cookie-secure=false` (which
// also drops the __Host- cookie prefixes the browser rejects over http). No proxy, no TLS.
const BASE_URL = process.env.E2E_BASE_URL ?? "http://localhost:4200";

export default defineConfig({
	testDir: "./e2e",
	testMatch: "**/*.spec.ts",
	fullyParallel: false,
	forbidOnly: !!process.env.CI,
	retries: process.env.CI ? 1 : 0,
	timeout: 60_000,
	reporter: [["list"]],
	use: {
		baseURL: BASE_URL,
		trace: "retain-on-failure",
		screenshot: "only-on-failure",
	},
	projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
	// Vite dev server. The backend (and its seed data) must already be running — see e2e/README.md.
	webServer: {
		command: "pnpm run dev",
		url: BASE_URL,
		reuseExistingServer: true,
		timeout: 120_000,
	},
});
