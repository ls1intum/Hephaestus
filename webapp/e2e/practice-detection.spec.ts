import { expect, loginAsDevAdmin, test } from "./fixtures";

/** Requires the seeded `e2e` workspace and a member account to sign in as — see e2e/seed.sql. */
test("dev-login then configure practice review settings (read + mutate over http)", async ({
	page,
}) => {
	await loginAsDevAdmin(page);

	await page.goto("/w/e2e/admin/practices/settings");
	await expect(page.getByRole("heading", { name: "Review settings" })).toBeVisible();
	// Card titles are `<div>`s, not headings, and "Model" needs `exact` or it also takes "AI models".
	await expect(page.getByText("Model", { exact: true })).toBeVisible();
	await expect(page.getByText("Review policy", { exact: true })).toBeVisible();

	// The PATCH proves the CSRF double-submit works over plain http.
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
