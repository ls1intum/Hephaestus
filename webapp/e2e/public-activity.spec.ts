import type { Page } from "@playwright/test";

import { expect, test } from "./fixtures";

const WORKSPACE = "/w/e2e-public?range=all";
const PERSON = "E2E River";
const API_REPOSITORY = "e2e-public/api";
const PUBLIC_WORK = "Document the public API (opens in a new tab)";
const PRIVATE_WORK = "Private repository work must stay private (opens in a new tab)";
const HIDDEN_WORK = "Hidden contributor work must stay private (opens in a new tab)";

test.use({ storageState: { cookies: [], origins: [] }, colorScheme: "light" });

async function openPublicWorkspace(page: Page) {
	const read = page.waitForResponse(
		(response) =>
			response.request().method() === "GET" &&
			new URL(response.url()).pathname.endsWith("/public/workspaces/e2e-public/activity"),
	);
	await page.goto(WORKSPACE);
	const response = await read;
	expect(response.status()).toBe(200);
	await expect(page.getByRole("heading", { name: "E2E Public Workspace activity" })).toBeVisible();
	await expect(page.getByRole("table", { name: "People", exact: true })).toBeVisible();
	await expect(page.getByRole("link", { name: PERSON, exact: true })).toBeVisible();
	const notice = page.getByRole("region", { name: "Your privacy" });
	if (await notice.isVisible()) {
		await notice.getByRole("button", { name: /^(?:Decline|Reject all)$/u }).click();
	}
	return response;
}

test("a visitor filters People, follows public work, and returns to the same scope", async ({
	page,
}) => {
	const publicRead = await openPublicWorkspace(page);
	const published = await publicRead.text();
	expect(published).not.toContain("e2e-public-hidden");
	expect(published).not.toContain("e2e-public/private");
	expect(published).not.toContain('"email"');
	await expect(page.getByRole("link", { name: "E2E Hidden", exact: true })).toHaveCount(0);
	await expect(
		page
			.getByRole("row")
			.filter({ has: page.getByRole("link", { name: PERSON, exact: true }) })
			.getByRole("cell", { name: "2", exact: true }),
	).toBeVisible();

	await page.getByRole("combobox", { name: "Repository", exact: true }).click();
	await expect(page.getByRole("option", { name: "e2e-public/private", exact: true })).toHaveCount(
		0,
	);
	await page.getByRole("option", { name: API_REPOSITORY, exact: true }).click();
	await page.keyboard.press("Escape");
	await expect(page).toHaveURL((url) => url.searchParams.get("repo") === API_REPOSITORY);
	const filteredAddress = page.url();

	await page
		.getByRole("table", { name: "People", exact: true })
		.getByRole("link", { name: PERSON, exact: true })
		.click();
	const person = page.getByRole("dialog", { name: PERSON, exact: true });
	await expect(person).toBeVisible();
	await expect(person.getByRole("link", { name: PUBLIC_WORK, exact: true })).toHaveAttribute(
		"href",
		"https://github.com/e2e-public/api/pull/11",
	);
	await expect(
		person.getByRole("link", {
			name: "Update the public web page (opens in a new tab)",
			exact: true,
		}),
	).toHaveCount(0);
	await expect(person.getByRole("link", { name: PRIVATE_WORK, exact: true })).toHaveCount(0);
	await expect(person.getByRole("link", { name: HIDDEN_WORK, exact: true })).toHaveCount(0);
	await expect(person.getByRole("button", { name: /hide|automation/iu })).toHaveCount(0);
	await expect(person.getByText("Comments", { exact: true })).toHaveCount(0);
	const personAddress = page.url();

	await person.getByRole("link", { name: /^Pull requests\b/u }).click();
	const category = page.getByRole("dialog", { name: "Pull requests", exact: true });
	await expect(category).toBeVisible();
	await expect(category.getByRole("heading", { name: "Timeline", exact: true })).toBeVisible();
	await expect(category.getByRole("link", { name: PUBLIC_WORK, exact: true })).toBeVisible();
	await page.goBack();
	await expect(category).toBeHidden();
	await expect(page).toHaveURL(personAddress);
	await expect(person).toBeVisible();
	await person.getByRole("button", { name: "Close", exact: true }).click();
	await expect(person).toBeHidden();
	await expect(page).toHaveURL(filteredAddress);
	await page.reload();
	await expect(
		page.getByRole("combobox", { name: `Repository: ${API_REPOSITORY}`, exact: true }),
	).toBeVisible();
	await expect(
		page
			.getByRole("table", { name: "People", exact: true })
			.getByRole("link", { name: PERSON, exact: true }),
	).toBeVisible();

	// The server must refuse direct links too; absence from the visible table is insufficient.
	const hiddenPerson = new URL(publicRead.url());
	hiddenPerson.pathname += "/people/e2e-public-hidden";
	const hiddenRead = await page.request.get(hiddenPerson.href);
	expect(hiddenRead.status()).toBe(404);
	const privateRepository = new URL(publicRead.url());
	privateRepository.searchParams.set("repo", "e2e-public/private");
	const privateRead = await page.request.get(privateRepository.href);
	expect(privateRead.status()).toBe(404);
	const unpublishedWorkspace = new URL(publicRead.url());
	unpublishedWorkspace.pathname = unpublishedWorkspace.pathname.replace("e2e-public", "e2e");
	const unpublishedRead = await page.request.get(unpublishedWorkspace.href);
	expect(unpublishedRead.status()).toBe(404);

	await page.getByRole("main").getByRole("button", { name: "Sign in", exact: true }).click();
	await expect(page).toHaveURL((url) => url.pathname === "/login");
	expect(new URL(page.url()).searchParams.get("returnTo")).toBe(
		new URL(filteredAddress).pathname + new URL(filteredAddress).search,
	);
});

test.describe("narrow touch", () => {
	test.use({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true });

	test("a public person and category remain reachable and close back to People", async ({
		page,
	}) => {
		await openPublicWorkspace(page);
		const peopleAddress = page.url();
		await page
			.getByRole("table", { name: "People", exact: true })
			.getByRole("link", { name: PERSON, exact: true })
			.tap();
		const person = page.getByRole("dialog", { name: PERSON, exact: true });
		await expect(person).toBeVisible();
		await person.getByRole("link", { name: /^Pull requests\b/u }).tap();
		const category = page.getByRole("dialog", { name: "Pull requests", exact: true });
		await expect(category.getByRole("link", { name: PUBLIC_WORK, exact: true })).toBeVisible();
		expect(
			await page.evaluate(
				() => document.documentElement.scrollWidth - document.documentElement.clientWidth,
			),
		).toBeLessThanOrEqual(0);
		await category.getByRole("button", { name: "Back", exact: true }).tap();
		await expect(category).toBeHidden();
		await expect(person).toBeVisible();
		await person.getByRole("button", { name: "Close", exact: true }).tap();
		await expect(person).toBeHidden();
		await expect(page).toHaveURL(peopleAddress);
		await expect(
			page
				.getByRole("table", { name: "People", exact: true })
				.getByRole("link", { name: PERSON, exact: true }),
		).toBeVisible();
	});
});
