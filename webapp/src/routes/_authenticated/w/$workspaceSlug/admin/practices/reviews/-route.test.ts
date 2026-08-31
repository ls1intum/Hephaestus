import { QueryClient } from "@tanstack/react-query";
import { createMemoryHistory, createRouter } from "@tanstack/react-router";
import { screen, within } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { server } from "@/mocks/server";
import { routeTree } from "@/routeTree.gen";
import { ROUTE_RENDER_WAIT, renderRouteAtWithRouter } from "@/test/router-harness";

// Mounting the real route pulls in the whole admin layout and its lazy modules; the timeout is a
// deadlock backstop, not a budget these renders were meant to fit inside.
vi.setConfig({ testTimeout: 15_000 });

function routerAt(url: string) {
	return createRouter({
		routeTree,
		history: createMemoryHistory({ initialEntries: [url] }),
		context: {
			queryClient: new QueryClient({
				defaultOptions: { queries: { retry: false } },
			}),
			auth: undefined,
		},
	});
}

async function land(url: string) {
	const router = routerAt(url);
	await router.load();
	return router.state.location;
}

beforeEach(() => {
	server.use(
		http.get("*/workspaces/:workspaceSlug/members/me", () =>
			HttpResponse.json({ role: "ADMIN", userId: 1, userLogin: "ada", userName: "Ada" }),
		),
	);
});

describe("practice review routes", () => {
	it.each([
		["Reviews", "/w/acme/admin/practices/reviews"],
		["Reviews", "/w/acme/admin/practices/reviews/11111111-1111-1111-1111-111111111111"],
		["Observations", "/w/acme/admin/practices/reviews/observations"],
		[
			"Observations",
			"/w/acme/admin/practices/reviews/observations/55555555-5555-5555-5555-555555555555",
		],
		["Delivery", "/w/acme/admin/practices/reviews/delivery"],
		["Delivery", "/w/acme/admin/practices/reviews/delivery/33333333-3333-3333-3333-333333333333"],
	])("marks only %s as current on %s", async (expectedCurrent, url) => {
		renderRouteAtWithRouter(url);

		const navigation = await screen.findByRole(
			"navigation",
			{
				name: "Practice review sections",
			},
			ROUTE_RENDER_WAIT,
		);
		const currentLinks = within(navigation).getAllByRole("link", { current: "page" });
		expect(currentLinks).toHaveLength(1);
		within(navigation).getByRole("link", { name: expectedCurrent, current: "page" });
	});

	it("redirects the former Runs page to Reviews", async () => {
		const location = await land("/w/acme/admin/practices/runs");

		expect(location.pathname).toBe("/w/acme/admin/practices/reviews");
	});

	// The URL said "findings" for a concept the product calls an observation, and a bookmark or a
	// link in a chat thread is the one copy of it nobody can be asked to update.
	it.each([
		[
			"/w/acme/admin/practices/reviews/findings?severity=MAJOR",
			"/w/acme/admin/practices/reviews/observations",
		],
		[
			"/w/acme/admin/practices/reviews/findings/55555555-5555-5555-5555-555555555555",
			"/w/acme/admin/practices/reviews/observations/55555555-5555-5555-5555-555555555555",
		],
	])("redirects the former Findings URL %s", async (from, expected) => {
		const location = await land(from);

		expect(location.pathname).toBe(expected);
	});

	it("carries a filter through the Findings redirect", async () => {
		const location = await land("/w/acme/admin/practices/reviews/findings?severity=MAJOR");

		// The multi-value params serialise as a JSON array, so this asserts the value survived rather
		// than the encoding: the point is that a filtered bookmark stays filtered across the rename.
		expect(decodeURIComponent(location.searchStr)).toContain('severity=["MAJOR"]');
	});

	it("treats reviewed work as a neutral view and carries its scope into Delivery", async () => {
		const emptyPage = {
			content: [],
			page: { number: 0, size: 5, totalElements: 0, totalPages: 0 },
		};
		server.use(
			http.get("*/workspaces/:workspaceSlug/practices/reviews/feedback", () =>
				HttpResponse.json(emptyPage),
			),
			http.get("*/workspaces/:workspaceSlug/practices/reviews/observations", () =>
				HttpResponse.json(emptyPage),
			),
		);
		renderRouteAtWithRouter("/w/acme/admin/practices/reviews/targets/pull-request/42");
		await screen.findByText("Nothing has been reviewed on this work");
		const navigation = screen.getByRole("navigation", { name: "Practice review sections" });
		expect(within(navigation).queryByRole("link", { current: "page" })).toBeNull();

		const deliveryLink = within(navigation).getByRole<HTMLAnchorElement>("link", {
			name: "Delivery",
		});
		const deliveryUrl = new URL(deliveryLink.href);
		expect(deliveryUrl.pathname).toBe("/w/acme/admin/practices/reviews/delivery");
		expect(deliveryUrl.searchParams.get("artifactKind")).toBe("scm.pull_request");
		expect(deliveryUrl.searchParams.get("artifactId")).toBe("42");
	});
});
