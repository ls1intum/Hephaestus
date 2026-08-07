import { screen } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { artifactTrace, tracedArtifactPage } from "@/components/practice-trace/story-mock-data";
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
		expect(
			await screen.findByRole("link", { name: /Member-facing review activity/ }, ROUTE_RENDER_WAIT),
		).toBeTruthy();
	});

	it("carries a dotted artifact kind through the URL into the detail view", async () => {
		renderRouteAt("/w/acme/reviews/scm.pull_request/1423");

		// The quiet practices are the point of the page, so one is asserted rather than the loud one.
		expect(
			await screen.findByText("Discussion hygiene", undefined, ROUTE_RENDER_WAIT),
		).toBeTruthy();
		expect(screen.getByText("Waiting on a connection")).toBeTruthy();
	});

	it("refuses an artifact id that is not a positive integer", async () => {
		renderRouteAt("/w/acme/reviews/scm.pull_request/not-a-number");

		expect(
			await screen.findByRole("heading", { name: "Page Not Found" }, ROUTE_RENDER_WAIT),
		).toBeTruthy();
	});
});
