import { screen } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";
import {
	artifactTrace,
	tracedArtifactPage,
	tracedArtifacts,
} from "@/components/practice-trace/story-mock-data";
import { server } from "@/mocks/server";
import { ROUTE_RENDER_WAIT, renderRouteAt } from "@/test/router-harness";

// Mounting the real route pulls in the whole app shell and its lazy modules.
vi.setConfig({ testTimeout: 15_000 });

beforeEach(() => {
	server.use(
		// A plain MEMBER: this surface is deliberately not behind the admin layout's role guard.
		http.get("*/workspaces/:workspaceSlug/members/me", () =>
			HttpResponse.json({ role: "MEMBER", userId: 1, userLogin: "ada", userName: "Ada" }),
		),
		http.get("*/workspaces/:workspaceSlug/practices/trace", () =>
			HttpResponse.json(tracedArtifactPage()),
		),
		http.get("*/workspaces/:workspaceSlug/practices/trace/:artifactKind/:artifactId", () =>
			HttpResponse.json(artifactTrace),
		),
	);
});

describe("review activity routes", () => {
	it("lists recorded work for a member", async () => {
		renderRouteAt("/w/acme/reviews");

		await screen.findByRole("heading", { name: "Review activity" }, ROUTE_RENDER_WAIT);
		await screen.findByRole("link", { name: /Member-facing review activity/ }, ROUTE_RENDER_WAIT);
	});

	/**
	 * The page links are built with `search={(previous) => ({ ...previous, page })}`, so what keeps a
	 * filter alive across a page turn is the router's own search state rather than anything the list
	 * holds. Turning to page 2 of "issues only" must stay issues only.
	 */
	it("keeps the work-type filter when the reader turns the page", async () => {
		server.use(
			http.get("*/workspaces/:workspaceSlug/practices/trace", () =>
				HttpResponse.json({
					content: tracedArtifacts,
					page: { number: 0, size: 20, totalElements: 45, totalPages: 3 },
				}),
			),
		);

		renderRouteAt("/w/acme/reviews?kind=scm.issue");

		const next = await screen.findByRole("link", { name: "Go to next page" }, ROUTE_RENDER_WAIT);
		expect(next.getAttribute("href")).toBe("/w/acme/reviews?kind=scm.issue&page=1");
		// Page 1 is the default, so it is spelled by leaving `page` out rather than by `page=0`.
		expect(screen.getByRole("link", { name: "Go to page 1" }).getAttribute("href")).toBe(
			"/w/acme/reviews?kind=scm.issue",
		);
	});

	it("carries a dotted artifact kind through the URL into the detail view", async () => {
		renderRouteAt("/w/acme/reviews/scm.pull_request/1423");

		// The quiet practices are the point of the page, so one is asserted rather than the loud one.
		await screen.findByText("Discussion hygiene", undefined, ROUTE_RENDER_WAIT);
		expect(screen.getByText("Waiting on a connection")).toBeTruthy();
	});

	it("refuses an artifact id that is not a positive integer", async () => {
		renderRouteAt("/w/acme/reviews/scm.pull_request/not-a-number");

		await screen.findByRole("heading", { name: "Page Not Found" }, ROUTE_RENDER_WAIT);
	});
});
