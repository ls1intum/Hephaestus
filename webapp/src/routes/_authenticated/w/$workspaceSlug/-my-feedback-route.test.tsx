import { screen } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { reflectionFeedback } from "@/components/reflection/story-mock-data";
import { server } from "@/mocks/server";
import { ROUTE_RENDER_WAIT, renderRouteAt } from "@/test/router-harness";

// Mounting the real route pulls in the whole app shell and its lazy modules.
vi.setConfig({ testTimeout: 15_000 });

const requests: Request[] = [];

beforeEach(() => {
	requests.length = 0;
	server.use(
		// A plain MEMBER: this page is deliberately not behind the admin layout's role guard.
		http.get("*/workspaces/:workspaceSlug/members/me", () =>
			HttpResponse.json({ role: "MEMBER", userId: 1, userLogin: "ada", userName: "Ada" }),
		),
		http.get("*/workspaces/:workspaceSlug/practices/feedback/reflection", ({ request }) => {
			requests.push(request);
			return HttpResponse.json(reflectionFeedback);
		}),
	);
});

describe("the my-feedback route", () => {
	it("shows the signed-in developer their own patterns", async () => {
		renderRouteAt("/w/acme/my-feedback");

		await screen.findByRole("heading", { name: "My feedback" }, ROUTE_RENDER_WAIT);
		await screen.findByRole(
			"heading",
			{ name: "Tests are arriving one commit late" },
			ROUTE_RENDER_WAIT,
		);
		screen.getByText("Seen on 3 pieces of your work");
	});

	/**
	 * The endpoint answers for whoever is calling and has no parameter to ask about anybody else,
	 * which is the property that makes it safe to write private text onto. A request that carried a
	 * user would mean the client had grown one — asserted here, because a story cannot see the wire.
	 */
	it("asks about nobody", async () => {
		renderRouteAt("/w/acme/my-feedback");

		await screen.findByRole("heading", { name: "My feedback" }, ROUTE_RENDER_WAIT);
		expect(requests).toHaveLength(1);
		const url = new URL(requests[0].url);
		expect(url.pathname).toBe("/workspaces/acme/practices/feedback/reflection");
		expect(url.search).toBe("");
	});

	it("says nothing is prepared rather than showing a blank page", async () => {
		server.use(
			http.get("*/workspaces/:workspaceSlug/practices/feedback/reflection", () =>
				HttpResponse.json([]),
			),
		);

		renderRouteAt("/w/acme/my-feedback");

		await screen.findByText("No feedback prepared for you yet", undefined, ROUTE_RENDER_WAIT);
	});
});
