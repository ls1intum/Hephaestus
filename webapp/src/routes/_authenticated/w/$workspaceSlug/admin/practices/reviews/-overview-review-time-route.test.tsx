import { screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { ROUTE_RENDER_WAIT, renderRouteAtWithRouter } from "@/test/router-harness";

import { OVERVIEW, serveAttention } from "./-overview-attention-server";

vi.setConfig({ testTimeout: 30_000 });

describe("the overview's review time", () => {
	it("says what the merge cost in comments on the review time card", async () => {
		serveAttention();
		renderRouteAtWithRouter(OVERVIEW);

		const lost = await screen.findByText(/lost to merge$/u, undefined, ROUTE_RENDER_WAIT);
		expect(lost.textContent).toBe("21 lost to merge");
		// The slowest tenth is gone; the median is split into the trace's five stages instead, with
		// no unsplit run, since every review recorded when its execution started.
		expect(screen.queryByText("Slowest 1 in 10")).toBeNull();
		expect(
			screen.getByRole("list", { name: "Where the time went" }).querySelectorAll("li"),
		).toHaveLength(5);
	});
});
