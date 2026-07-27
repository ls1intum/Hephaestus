import { expect, loginAsDevAdmin, test } from "./fixtures";

/**
 * Drives the workspace practices admin UI end to end over plain http://localhost: passwordless dev
 * sign-in → the Review settings page renders → a policy mutation round-trips (which also proves the
 * CSRF double-submit works over http).
 *
 * Requires the seeded `e2e` workspace + a signed-in account that is a member (see e2e/seed.sql).
 */
test("dev-login then configure practice review settings (read + mutate over http)", async ({
	page,
}) => {
	await loginAsDevAdmin(page);

	await page.goto("/w/e2e/admin/practices/settings");
	await expect(page.getByRole("heading", { name: "Review settings" })).toBeVisible();
	// The two card titles. `exact` on "Model" because it is a short word: without it the matcher also
	// takes "Model #12", "AI models" in the sidebar, and any label that merely contains it. Card
	// titles are `<div>`s (`ui/card.tsx`), not headings, so `getByText` is the query that reaches
	// them — `getByRole("heading")` finds neither.
	await expect(page.getByText("Model", { exact: true })).toBeVisible();
	await expect(page.getByText("Review policy", { exact: true })).toBeVisible();

	// Toggle "Skip drafts" — the PATCH proves CSRF double-submit works over plain http.
	const skipDrafts = page.getByRole("switch", { name: /skip drafts/i });
	const before = await skipDrafts.getAttribute("aria-checked");
	const [response] = await Promise.all([
		page.waitForResponse(
			(r) => r.url().includes("/practices/review-settings") && r.request().method() === "PATCH",
		),
		skipDrafts.click(),
	]);
	expect(response.status()).toBe(200);
	await expect(skipDrafts).not.toHaveAttribute("aria-checked", before ?? "");
});
