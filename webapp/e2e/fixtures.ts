import { test as base, expect, type Page } from "@playwright/test";

/** Backend origin the SPA talks to (cross-origin same-site; CORS allows http://localhost:4200). */
const SERVER_URL = process.env.E2E_SERVER_URL ?? "http://localhost:38080";

/**
 * E2E fixture. The dev /env-config.js stub does `window.__ENV__ = {}` (loaded before the app bundle);
 * we replace it so the SPA targets the backend and reads the non-`__Host-` XSRF cookie name — matching
 * a backend booted with `hephaestus.auth.cookie-secure=false`, so the whole flow works over plain
 * http://localhost.
 */
export const test = base.extend({
	context: async ({ context }, use) => {
		await context.route("**/env-config.js", (route) =>
			route.fulfill({
				contentType: "application/javascript",
				body: `window.__ENV__ = ${JSON.stringify({
					APPLICATION_SERVER_URL: SERVER_URL,
					APPLICATION_CLIENT_URL: process.env.E2E_BASE_URL ?? "http://localhost:4200",
					XSRF_COOKIE_NAME: "XSRF-TOKEN",
					TANSTACK_DEVTOOLS_ENABLED: "false",
					SENTRY_DSN: "",
				})};`,
			}),
		);
		await use(context);
	},
});

export { expect };

/** Sign in through the real Dev sign-in UI (passwordless; requires the backend's dev-login flag on). */
export async function loginAsDevAdmin(page: Page, username = "e2e"): Promise<void> {
	await page.goto("/login");
	const consent = page.getByRole("button", { name: /^(decline|allow)$/i }).first();
	if (await consent.count()) await consent.click().catch(() => undefined);
	await page.getByPlaceholder("username").fill(username);
	await page.getByRole("button", { name: /continue as dev admin/i }).click();
	await page.waitForURL((url) => !url.pathname.startsWith("/login"));
}
