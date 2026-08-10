import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { describe, expect, it, vi } from "vitest";
import { buildAutonomyFixture } from "@/components/admin/practices/review-autonomy/story-mock-data";
import { server } from "@/mocks/server";
import { ROUTE_RENDER_WAIT, renderRouteAt } from "@/test/router-harness";

vi.setConfig({ testTimeout: 20_000 });

const fixture = buildAutonomyFixture({
	workspaceDefault: "OBSERVE",
	areas: [
		{
			slug: "hygiene",
			name: "Hygiene",
			practices: [{ name: "States the motivation" }, { name: "Links the issue", override: "OFF" }],
		},
	],
});

function stubWorkspace(extra: Parameters<typeof server.use>) {
	server.use(
		http.get("*/workspaces/:workspaceSlug/members/me", () =>
			HttpResponse.json({ role: "ADMIN", userId: 1, userLogin: "ada", userName: "Ada" }),
		),
		http.get("*/workspaces/:workspaceSlug/practices/review-settings", () =>
			HttpResponse.json(fixture.settings),
		),
		http.get("*/workspaces/:workspaceSlug/practices/review-tiers", () =>
			HttpResponse.json(fixture.rollup),
		),
		http.get("*/workspaces/:workspaceSlug/practices", () => HttpResponse.json(fixture.practices)),
		...extra,
	);
}

describe("review autonomy route", () => {
	it("answers 'what is this workspace doing' from the rollup rather than from the rows", async () => {
		stubWorkspace([]);

		renderRouteAt("/w/acme/admin/practices/autonomy");

		await screen.findByRole("heading", { name: "Review autonomy" }, ROUTE_RENDER_WAIT);
		await screen.findByRole("button", { name: /Hygiene/ }, ROUTE_RENDER_WAIT);

		// The counts are the server's; nothing here adds up practice rows, and the areas are shut.
		// Twice over, on purpose: once for the workspace in the strip that stays on screen, once for the
		// only area, which happens to hold every practice.
		expect(screen.getAllByText("2 practices: 1 off and 1 observe.")).toHaveLength(2);
		expect(screen.queryByText("States the motivation")).toBeNull();
	});

	/**
	 * The one wire detail a screen can get wrong silently. The generated request types the tier as
	 * optional rather than nullable, and the server reads an absent field as "hold no tier here and
	 * inherit" — so clearing an override means omitting the key, not sending `null`.
	 */
	it("clears an override by omitting the tier, not by sending null", async () => {
		const bodies: Array<Record<string, unknown>> = [];
		stubWorkspace([
			http.patch(
				"*/workspaces/:workspaceSlug/practices/:practiceSlug/review-tier",
				async ({ request }) => {
					bodies.push((await request.json()) as Record<string, unknown>);
					return HttpResponse.json({
						...fixture.practices[1],
						reviewTier: { effective: "OBSERVE", source: "AREA", inherited: true },
					});
				},
			),
		]);

		renderRouteAt("/w/acme/admin/practices/autonomy");

		const area = await screen.findByRole("button", { name: /Hygiene/ }, ROUTE_RENDER_WAIT);
		await userEvent.click(area);
		const row = (await screen.findByText("Links the issue")).closest("li");
		if (!(row instanceof HTMLElement)) throw new Error("Practice row not rendered");

		// `hidden: true`: jsdom runs no layout, so Base UI leaves the opened accordion panel carrying
		// the attribute it strips once the panel has a height. The browser-run story covers the state a
		// real user meets; this test is here for the request body.
		await userEvent.click(
			within(row).getByRole("button", {
				name: "Use the default for Links the issue",
				hidden: true,
			}),
		);

		await waitFor(() => expect(bodies).toHaveLength(1));
		expect(bodies[0]).toEqual({});
		expect("reviewTier" in bodies[0]).toBe(false);
	});
});
