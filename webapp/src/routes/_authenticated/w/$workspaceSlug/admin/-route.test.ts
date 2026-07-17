import { QueryClient } from "@tanstack/react-query";
import { createMemoryHistory, createRouter } from "@tanstack/react-router";
import { HttpResponse, http } from "msw";
import { describe, expect, it, vi } from "vitest";
import type { WorkspaceRole } from "@/lib/workspace-roles";
import { server } from "@/mocks/server";
import { routeTree } from "@/routeTree.gen";

const WORKSPACE_HOME = "/w/acme";

// `router.load()` lazily imports each matched route's module, so a case can pay the one-off cost of
// transforming a heavy page (the achievement designer pulls in ReactFlow). Observed ~4s against the
// 5s default, which is a flake waiting to happen on a loaded CI box.
vi.setConfig({ testTimeout: 15_000 });

function newRouter(url?: string) {
	return createRouter({
		routeTree,
		...(url ? { history: createMemoryHistory({ initialEntries: [url] }) } : {}),
		context: {
			// A fresh client per case: a shared cache would let one role's answer satisfy another's guard.
			queryClient: new QueryClient(),
			auth: undefined,
		},
	});
}

/** Every admin URL, read from the generated tree so a new admin route is covered automatically. */
const adminUrls = Object.values(newRouter().routesById)
	.filter((route) => route.fullPath?.startsWith("/w/$workspaceSlug/admin/"))
	.map((route) => route.fullPath.replace("$workspaceSlug", "acme"));

function mockMembership(role: WorkspaceRole | null) {
	server.use(
		http.get("*/workspaces/:workspaceSlug/members/me", () =>
			role
				? HttpResponse.json({ role, userId: 1, userLogin: "ada", userName: "Ada" })
				: // What the server actually sends a non-member: the membership lookup throws
					// IllegalArgumentException, which the global advice renders as 400.
					HttpResponse.json({ status: 400, title: "Bad Request" }, { status: 400 }),
		),
	);
}

async function land(url: string) {
	const router = newRouter(url);
	await router.load();
	return router.state.location.pathname;
}

/**
 * Drives admin URLs through the real router as each role. This is what makes the gate structural
 * rather than a convention: a route file that maps to an /admin URL without nesting under the
 * layout (a sibling of it, a dot-notation path, an `admin_` un-nesting suffix) skips the guard
 * silently, and `achievement-designer.tsx` shipped exactly that way.
 */
describe("workspace-admin route gate", () => {
	it("enumerates the admin routes", () => {
		// A filter that matched nothing would leave every case below vacuously green, and one that
		// matched a subset would quietly shrink the coverage this suite exists for.
		expect(adminUrls.length).toBeGreaterThanOrEqual(14);
	});

	// Per route, because each route's nesting is a separate fact: an un-nested one fails only its
	// own case. The allow path needs no such loop — an ungated route admits an ADMIN just as
	// happily as a gated one, so it would prove nothing per route.
	it.each(adminUrls)("redirects a MEMBER away from %s", async (url) => {
		mockMembership("MEMBER");
		expect(await land(url)).toBe(WORKSPACE_HOME);
	});

	it("admits an ADMIN", async () => {
		mockMembership("ADMIN");
		expect(await land("/w/acme/admin/settings")).toBe("/w/acme/admin/settings");
	});

	it("redirects a non-member", async () => {
		mockMembership(null);
		expect(await land("/w/acme/admin/settings")).toBe(WORKSPACE_HOME);
	});

	it("redirects when the membership cannot be resolved", async () => {
		server.use(http.get("*/workspaces/:workspaceSlug/members/me", () => HttpResponse.error()));
		expect(await land("/w/acme/admin/settings")).toBe(WORKSPACE_HOME);
	});
});
