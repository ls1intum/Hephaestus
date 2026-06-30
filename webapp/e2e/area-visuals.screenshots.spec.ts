import { loginAsDevAdmin, test } from "./fixtures";

/**
 * Captures PR screenshots of the domain-framed "Practices" admin: the Catalog tree (areas → practices
 * in one accordion, drag-reorderable, area icons in their seeded colours), the icon/colour picker, the
 * Review settings and Runs pages, and a practice's detail page (standard + the reserved
 * observations/feedback section). Run against the seeded `e2e` workspace. Output → /tmp/shots/*.png.
 */
const OUT = "/tmp/shots";

test("capture practices admin", async ({ page }) => {
	await page.setViewportSize({ width: 1440, height: 1000 });
	await page.addInitScript(() => {
		try {
			localStorage.setItem("theme", "light");
		} catch {
			// ignore
		}
	});
	await loginAsDevAdmin(page);

	// 1) Catalog — collapsed overview: areas as accordion sections with their seeded colour/icon, plus
	//    the sidebar's expanded "Practices" group.
	await page.goto("/w/e2e/admin/practices");
	await page.waitForLoadState("networkidle");
	await page.waitForTimeout(500);
	await page.screenshot({ path: `${OUT}/catalog-overview.png`, fullPage: true });

	// 2) Expand the first two areas to show their practice rows (flush list, aligned switch column).
	const headers = page.locator('[data-slot="accordion-trigger"]');
	const n = Math.min(2, await headers.count());
	for (let i = 0; i < n; i++) {
		await headers.nth(i).click();
		await page.waitForTimeout(150);
	}
	await page.waitForTimeout(300);
	await page.screenshot({ path: `${OUT}/catalog-expanded.png`, fullPage: true });

	// 3) Area icon/colour picker — open the first area's visual editor (full colour spectrum + searchable
	//    icon grid).
	await page
		.getByRole("button", { name: /Edit icon and colour/ })
		.first()
		.click();
	await page.waitForTimeout(350);
	await page.screenshot({ path: `${OUT}/area-picker.png` });
	await page.keyboard.press("Escape");
	await page.waitForTimeout(150);

	// 4) Practice detail page — the spine (standard + reserved observations/feedback section).
	await page
		.locator('a:has(span.hover\\:underline)')
		.first()
		.click()
		.catch(() => {});
	await page.waitForLoadState("networkidle");
	await page.waitForTimeout(400);
	await page.screenshot({ path: `${OUT}/practice-detail.png`, fullPage: true });

	// 5) Review settings.
	await page.goto("/w/e2e/admin/practices/settings");
	await page.waitForLoadState("networkidle");
	await page.waitForTimeout(500);
	await page.screenshot({ path: `${OUT}/review-settings.png`, fullPage: true });

	// 6) Runs.
	await page.goto("/w/e2e/admin/practices/runs");
	await page.waitForLoadState("networkidle");
	await page.waitForTimeout(500);
	await page.screenshot({ path: `${OUT}/runs.png`, fullPage: true });
});
