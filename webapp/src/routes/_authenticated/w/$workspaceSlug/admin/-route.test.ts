import { QueryClient } from "@tanstack/react-query";
import { createMemoryHistory, createRouter } from "@tanstack/react-router";
import { HttpResponse, http } from "msw";
import { beforeEach, describe, expect, it, onTestFinished, vi } from "vitest";

import { isRecord } from "@/lib/is-record";
import { ROUTER_SEARCH } from "@/lib/router-search";
import { hasText } from "@/lib/text";
import type { WorkspaceRole } from "@/lib/workspace-roles";
import { currentUser } from "@/mocks/fixtures/auth";
import { ownMembership, workspaceListItem } from "@/mocks/fixtures/workspaces";
import { server } from "@/mocks/server";
import { routeTree } from "@/routeTree.gen";
import { clearUserView, USER_VIEW_STORAGE_KEY } from "@/runtime/user-view/session";

// The workspace reviews practices, so its home is the Practice profile.
const WORKSPACE_HOME = "/w/acme/practice-profile";

// `router.load()` lazily imports each matched route's module, so a case pays its transform cost.
vi.setConfig({ testTimeout: 15_000 });

function newRouter(url?: string) {
	return createRouter({
		...ROUTER_SEARCH,
		routeTree,
		...(hasText(url) ? { history: createMemoryHistory({ initialEntries: [url] }) } : {}),
		context: {
			// A fresh client per case: a shared cache would let one role's answer satisfy another's guard.
			queryClient: new QueryClient(),
			auth: undefined,
		},
	});
}

// `routesById` is keyed by generated route id, and its value type does not survive `Object.values`,
// so `Object.values` widens to `any` and `fullPath` is narrowed on the way out rather than asserted.
const routePaths = Object.values(newRouter().routesById)
	.map((route) => (isRecord(route) ? route.fullPath : undefined))
	.filter((fullPath): fullPath is string => typeof fullPath === "string");
const adminUrls = routePaths
	.filter((fullPath) => fullPath.startsWith("/w/$workspaceSlug/admin/"))
	.map((fullPath) => fullPath.replace("$workspaceSlug", "acme"));

function mockMembership(role: WorkspaceRole | null) {
	server.use(
		http.get("*/workspaces/:workspaceSlug/members/me", () => {
			if (role) {
				return HttpResponse.json(ownMembership(role));
			}
			// The server answers a non-member with 400, not 403.
			return HttpResponse.json({ status: 400, title: "Bad Request" }, { status: 400 });
		}),
	);
}

async function land(url: string) {
	const router = newRouter(url);
	await router.load();
	return router.state.location.pathname;
}

/** Guards the bypass a route file cannot show: an /admin URL that does not nest under the gate. */
describe("workspace-admin route gate", () => {
	beforeEach(() => {
		server.use(
			http.get("*/user", () => HttpResponse.json({ ...currentUser, appRole: "APP_USER" })),
			http.get("*/workspaces", () =>
				HttpResponse.json([workspaceListItem("acme", { practicesEnabled: true })]),
			),
		);
	});

	it("enumerates the admin routes rather than trusting a hand-written list", () => {
		// A filter that matched nothing would leave every case below vacuously green.
		expect(adminUrls.length).toBeGreaterThanOrEqual(19);
		expect(adminUrls).toContain("/w/acme/admin/settings");
	});

	it.each([
		"/w/$workspaceSlug/achievements",
		"/w/$workspaceSlug/user/$username/achievements",
		"/w/$workspaceSlug/admin/achievements",
		"/w/$workspaceSlug/admin/achievement-designer",
		"/w/$workspaceSlug/reviews",
		"/w/$workspaceSlug/reviews/",
		"/w/$workspaceSlug/reviews/$artifactKind/$artifactId",
	])("does not register the retired route %s", (path) => {
		expect(routePaths).not.toContain(path);
	});

	it.each(adminUrls)("redirects a MEMBER away from %s", async (url) => {
		mockMembership("MEMBER");
		await expect(land(url)).resolves.toBe(WORKSPACE_HOME);
	});

	it("admits an instance administrator to a verified nonmember workspace's admin page", async () => {
		server.use(
			http.get("*/user", () => HttpResponse.json(currentUser)),
			http.get("*/workspaces/tutors", () =>
				HttpResponse.json({ workspaceSlug: "tutors", displayName: "Tutors" }),
			),
		);
		mockMembership(null);
		await expect(land("/w/tutors/admin/settings")).resolves.toBe("/w/tutors/admin/settings");
	});

	it.each([403, 404, 503])(
		"surfaces target verification failure %s without entering administration",
		async (status) => {
			server.use(
				http.get("*/user", () => HttpResponse.json(currentUser)),
				http.get("*/workspaces/tutors", () => HttpResponse.json({ status }, { status })),
			);
			mockMembership(null);
			const router = newRouter("/w/tutors/admin/settings");
			await router.load();
			expect(
				router.state.matches.find((match) => match.routeId === "/_authenticated/w/$workspaceSlug")
					?.status,
			).toBe("error");
		},
	);

	it("keeps an ordinary nonmember out of an unlisted workspace's administration", async () => {
		let targetReads = 0;
		server.use(
			http.get("*/workspaces/tutors", () => {
				targetReads += 1;
				return HttpResponse.json({ workspaceSlug: "tutors" });
			}),
		);
		mockMembership(null);
		await expect(land("/w/tutors/admin/settings")).resolves.toBe(WORKSPACE_HOME);
		expect(targetReads).toBe(0);
	});

	it("does not elevate an instance administrator's user view", async () => {
		server.use(http.get("*/user", () => HttpResponse.json(currentUser)));
		sessionStorage.setItem(
			USER_VIEW_STORAGE_KEY,
			JSON.stringify({
				operatorAccountId: currentUser.id,
				workspaceSlug: "acme",
				workspaceName: "Acme",
				accountId: "7",
				providerLogin: "ada",
				name: "Ada",
				unclaimed: false,
				reason: "Support request",
			}),
		);
		onTestFinished(clearUserView);
		await expect(land("/w/tutors/admin/settings")).resolves.toBe(WORKSPACE_HOME);
	});

	it("does not extend instance administration to ordinary nonmember workspace pages", async () => {
		server.use(http.get("*/user", () => HttpResponse.json(currentUser)));
		await expect(land("/w/tutors/mentor/thread-1")).resolves.toBe(WORKSPACE_HOME);
	});

	it("admits an ADMIN", async () => {
		mockMembership("ADMIN");
		await expect(land("/w/acme/admin/settings")).resolves.toBe("/w/acme/admin/settings");
	});

	// Member onboarding is the one admin page that needs OWNER: the route's own `beforeLoad` sends
	// an ADMIN on to settings, which the layout gate above it cannot express.
	it("sends an ADMIN from member onboarding to settings", async () => {
		mockMembership("ADMIN");
		await expect(land("/w/acme/admin/onboarding")).resolves.toBe("/w/acme/admin/settings");
	});

	it("admits an OWNER to member onboarding", async () => {
		mockMembership("OWNER");
		await expect(land("/w/acme/admin/onboarding")).resolves.toBe("/w/acme/admin/onboarding");
	});

	it("redirects a non-member", async () => {
		mockMembership(null);
		await expect(land("/w/acme/admin/settings")).resolves.toBe(WORKSPACE_HOME);
	});

	it("redirects when the membership cannot be resolved", async () => {
		server.use(http.get("*/workspaces/:workspaceSlug/members/me", () => HttpResponse.error()));
		await expect(land("/w/acme/admin/settings")).resolves.toBe(WORKSPACE_HOME);
	});
});
