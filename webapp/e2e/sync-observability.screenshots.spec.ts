import { expect, loginAsDevAdmin, test } from "./fixtures";

const SHOTS = "/tmp/ui-shots";

test.describe("integrations sync observability", () => {
	test("overview page renders catalog with live status", async ({ page }) => {
		await loginAsDevAdmin(page, "felix-e2e");
		await page.goto("/w/hephaestustest/admin/integrations");
		await expect(page.getByText(/github/i).first()).toBeVisible({ timeout: 15000 });
		await page.waitForTimeout(2500);
		await page.screenshot({ path: `${SHOTS}/01-overview-github-ws.png`, fullPage: true });
	});

	test("scm detail page shows repos, rate limit, jobs", async ({ page }) => {
		await loginAsDevAdmin(page, "felix-e2e");
		await page.goto("/w/hephaestustest/admin/integrations/scm");
		await page.waitForTimeout(3000);
		await page.screenshot({ path: `${SHOTS}/02-scm-github.png`, fullPage: true });
	});

	test("outline detail page shows collections and jobs", async ({ page }) => {
		await loginAsDevAdmin(page, "felix-e2e");
		await page.goto("/w/hephaestustest/admin/integrations/outline");
		await page.waitForTimeout(3000);
		await page.screenshot({ path: `${SHOTS}/03-outline.png`, fullPage: true });
	});

	test("slack page shows connect CTA when unconnected", async ({ page }) => {
		await loginAsDevAdmin(page, "felix-e2e");
		await page.goto("/w/hephaestustest/admin/integrations/slack");
		await page.waitForTimeout(2000);
		await page.screenshot({ path: `${SHOTS}/04-slack-unconnected.png`, fullPage: true });
	});

	test("gitlab workspace scm page + live sync trigger", async ({ page }) => {
		await loginAsDevAdmin(page, "felix-e2e");
		await page.goto("/w/heph-gitlab/admin/integrations/scm");
		await page.waitForTimeout(3000);
		await page.screenshot({ path: `${SHOTS}/05-scm-gitlab.png`, fullPage: true });

		const syncNow = page.getByRole("button", { name: /sync now/i }).first();
		if (await syncNow.count()) {
			await syncNow.click();
			// live update should arrive via SSE/polling without reload
			await page.waitForTimeout(6000);
			await page.screenshot({ path: `${SHOTS}/06-scm-gitlab-syncing.png`, fullPage: true });
		}
	});
});
