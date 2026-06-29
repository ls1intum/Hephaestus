import { expect, loginAsDevAdmin, test } from "./fixtures";

/**
 * Drives the workspace practice-detection admin UI end to end over plain http://localhost:
 * passwordless dev sign-in → workspace config renders → a policy mutation round-trips (which also
 * proves the CSRF double-submit works over http).
 *
 * Requires the seeded `e2e` workspace + a signed-in account that is a member (see e2e/seed.sql).
 */
test("dev-login then configure practice detection (read + mutate over http)", async ({ page }) => {
	await loginAsDevAdmin(page);

	await page.goto("/w/e2e/admin/ai/practice-detection");
	await expect(page.getByRole("heading", { name: "Practice detection" })).toBeVisible();
	// `exact` so the "AI model" binding-card title is not confused with the "AI models" nav link.
	await expect(page.getByText("AI model", { exact: true })).toBeVisible();
	await expect(page.getByText("Review policy")).toBeVisible();

	// Toggle "Skip drafts" — the PATCH proves CSRF double-submit works over plain http.
	const skipDrafts = page.getByRole("switch", { name: /skip drafts/i });
	const before = await skipDrafts.getAttribute("aria-checked");
	const [response] = await Promise.all([
		page.waitForResponse(
			(r) => r.url().includes("/ai-settings/practice-review") && r.request().method() === "PATCH",
		),
		skipDrafts.click(),
	]);
	expect(response.status()).toBe(200);
	await expect(skipDrafts).not.toHaveAttribute("aria-checked", before ?? "");
});
