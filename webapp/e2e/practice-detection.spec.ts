import assert from "node:assert/strict";
import type { Response } from "@playwright/test";
import { expect, loginAsDevAdmin, test } from "./fixtures";

function isReviewSettingsWrite(response: Response) {
	return (
		response.url().includes("/practices/review-settings") && response.request().method() === "PATCH"
	);
}

/** Requires the seeded `e2e` workspace and a member account to sign in as — see e2e/seed.sql. */
test("dev-login then configure practice review settings (read + mutate over http)", async ({
	page,
}) => {
	await loginAsDevAdmin(page);

	await page.goto("/w/e2e/admin/practices/review?section=when-and-where");
	await expect(page.getByRole("heading", { name: "Review" })).toBeVisible();
	// Card titles are `<div>`s, not headings, and "Model" needs `exact` or it also takes "AI models".
	await expect(page.getByText("Model", { exact: true })).toBeVisible();
	await expect(page.getByText("Review policy", { exact: true })).toBeVisible();

	// The PATCH proves the CSRF double-submit works over plain http.
	const skipDrafts = page.getByRole("switch", { name: /skip drafts/i });
	const before = await skipDrafts.getAttribute("aria-checked");
	assert.ok(before, "The switch must report an aria-checked state before it is clicked.");
	const [response] = await Promise.all([
		page.waitForResponse(isReviewSettingsWrite),
		skipDrafts.click(),
	]);
	expect(response.status()).toBe(200);
	await expect(skipDrafts).not.toHaveAttribute("aria-checked", before);
});
