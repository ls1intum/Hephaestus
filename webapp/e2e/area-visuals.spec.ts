import { expect, loginAsDevAdmin, test } from "./fixtures";

test("practice areas render and their visual editor opens", async ({ page }) => {
	await loginAsDevAdmin(page);
	await page.goto("/w/e2e/admin/practices");

	await expect(page.getByRole("heading", { name: "Practice setup" })).toBeVisible();
	const editVisual = page.getByRole("button", { name: /Edit the icon and color/ }).first();
	await expect(editVisual).toBeVisible();
	await editVisual.click();
	await expect(page.getByRole("dialog", { name: "Icon and color" })).toBeVisible();
});
