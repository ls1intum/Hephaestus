import { QueryClient } from "@tanstack/react-query";
import { createMemoryHistory, createRouter } from "@tanstack/react-router";
import { screen, within } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { beforeEach, describe, expect, it } from "vitest";
import { server } from "@/mocks/server";
import { routeTree } from "@/routeTree.gen";
import { ROUTE_RENDER_WAIT, renderRouteAtWithRouter } from "@/test/router-harness";

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
		["Review activity", "/w/acme/admin/practices/reviews"],
		["Review activity", "/w/acme/admin/practices/reviews/11111111-1111-1111-1111-111111111111"],
		["Review activity", "/w/acme/admin/practices/reviews/targets/pull-request/42"],
		["Findings", "/w/acme/admin/practices/reviews/findings"],
		["Findings", "/w/acme/admin/practices/reviews/findings/55555555-5555-5555-5555-555555555555"],
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

	it("redirects the former Runs page to Review activity", async () => {
		const location = await land("/w/acme/admin/practices/runs");

		expect(location.pathname).toBe("/w/acme/admin/practices/reviews");
	});

	it("keeps reviewed-work output under Review activity and carries its scope into Delivery", async () => {
		const emptyPage = {
			content: [],
			page: { number: 0, size: 5, totalElements: 0, totalPages: 0 },
		};
		server.use(
			http.get("*/workspaces/:workspaceSlug/practices/reviews/feedback", () =>
				HttpResponse.json(emptyPage),
			),
			http.get("*/workspaces/:workspaceSlug/practices/reviews/findings", () =>
				HttpResponse.json(emptyPage),
			),
		);
		renderRouteAtWithRouter("/w/acme/admin/practices/reviews/targets/pull-request/42");
		await screen.findByText("No review output found");
		const navigation = screen.getByRole("navigation", { name: "Practice review sections" });
		within(navigation).getByRole("link", { name: "Review activity", current: "page" });

		const deliveryLink = within(navigation).getByRole("link", {
			name: "Delivery",
		}) as HTMLAnchorElement;
		const deliveryUrl = new URL(deliveryLink.href);
		expect(deliveryUrl.pathname).toBe("/w/acme/admin/practices/reviews/delivery");
		expect(deliveryUrl.searchParams.get("artifactType")).toBe("PULL_REQUEST");
		expect(deliveryUrl.searchParams.get("artifactId")).toBe("42");
	});
});
