import { QueryClient } from "@tanstack/react-query";
import { createMemoryHistory, createRouter } from "@tanstack/react-router";
import { HttpResponse, http } from "msw";
import { describe, expect, it, vi } from "vitest";
import { currentUser } from "@/mocks/fixtures/auth";
import { server } from "@/mocks/server";
import { routeTree } from "@/routeTree.gen";

// `router.load()` lazily imports each matched route's module, so a case pays its transform cost.
vi.setConfig({ testTimeout: 15_000 });

function newRouter(url?: string) {
	return createRouter({
		routeTree,
		...(url ? { history: createMemoryHistory({ initialEntries: [url] }) } : {}),
		// A fresh client per case: a shared cache would let one role's answer satisfy another's guard.
		context: { queryClient: new QueryClient(), auth: undefined },
	});
}

/** Every instance-admin URL, read from the generated tree so a new one is covered automatically. */
const adminUrls = Object.values(newRouter().routesById)
	.filter((route) => route.fullPath?.startsWith("/admin/"))
	.map((route) => route.fullPath);

function mockAppRole(appRole: "APP_ADMIN" | "APP_USER") {
	server.use(http.get("*/user", () => HttpResponse.json({ ...currentUser, appRole })));
}

async function land(url: string) {
	const router = newRouter(url);
	await router.load();
	return router.state.location.pathname;
}

/**
 * The bypass this guards against: a route mapping to an /admin URL without nesting under the gated
 * layout. Nothing about the file it lives in would say so, so the URLs are driven for real.
 */
describe("instance-admin route gate", () => {
	it("enumerates the instance-admin routes rather than trusting a hand-written list", () => {
		// A filter that matched nothing leaves every case below vacuously green. Raise the floor when
		// routes are added; never lower it.
		expect(adminUrls.length).toBeGreaterThanOrEqual(6);
		expect(adminUrls).toContain("/admin/users");
		expect(adminUrls).toContain("/admin/usage");
	});

	it.each(adminUrls)("redirects a non-admin away from %s", async (url) => {
		mockAppRole("APP_USER");
		expect(await land(url)).toBe("/");
	});

	it("admits an APP_ADMIN", async () => {
		mockAppRole("APP_ADMIN");
		expect(await land("/admin/users")).toBe("/admin/users");
	});
});

/**
 * Both consoles deliberately reuse page names, so the tab title is the only thing separating two
 * open admin tabs.
 */
describe("admin tab titles", () => {
	async function titleOf(url: string) {
		mockAppRole("APP_ADMIN");
		// The per-workspace console gates on membership, not on the app role.
		server.use(
			http.get("*/workspaces/:workspaceSlug/members/me", () =>
				HttpResponse.json({ role: "ADMIN", userId: 1, userLogin: "ada", userName: "Ada" }),
			),
		);
		const router = newRouter(url);
		await router.load();
		// The deepest match's title wins, so read the last one the matched routes contributed.
		const titles = router.state.matches
			.flatMap((match) => match.meta ?? [])
			.map((tag) => tag?.title)
			.filter((title): title is string => typeof title === "string");
		return titles.at(-1);
	}

	it.each([
		["/admin/usage", "AI usage · Instance admin · Hephaestus"],
		["/w/hephaestus/admin/usage", "AI usage · Admin · Hephaestus"],
	])("distinguishes %s in the tab title", async (url, title) => {
		expect(await titleOf(url)).toBe(title);
	});
});
