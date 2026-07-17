import { QueryClient } from "@tanstack/react-query";
import { createMemoryHistory, createRouter } from "@tanstack/react-router";
import { HttpResponse, http } from "msw";
import { describe, expect, it, vi } from "vitest";
import { currentUser } from "@/mocks/fixtures/auth";
import { server } from "@/mocks/server";
import { routeTree } from "@/routeTree.gen";

// `router.load()` lazily imports each matched route's module, so a case can pay the one-off cost of
// transforming a heavy page. Observed several seconds against the 5s default.
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
 * The instance-admin twin of `w/$workspaceSlug/admin/-route.test.ts`: same bypass class (a route
 * mapping to an /admin URL without nesting under the gated layout), same enumerate-and-drive shape.
 * Instance-admin *features* are #1386's; keeping its gate honest is this suite's.
 */
describe("instance-admin route gate", () => {
	it("enumerates the instance-admin routes", () => {
		// A filter that matched nothing would leave every case below vacuously green.
		expect(adminUrls.length).toBeGreaterThanOrEqual(4);
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
