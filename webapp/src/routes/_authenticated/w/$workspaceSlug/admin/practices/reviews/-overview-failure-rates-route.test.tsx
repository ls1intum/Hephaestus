import { screen, within } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { describe, expect, it, vi } from "vitest";

import { reviewRangeStart } from "@/components/admin/practice-reviews/review-range";
import { server } from "@/mocks/server";
import { ROUTE_RENDER_WAIT, renderRouteAtWithRouter } from "@/test/router-harness";

import { OVERVIEW, rangeOverview, serveAttention } from "./-overview-attention-server";

vi.setConfig({ testTimeout: 30_000 });

/** `total` reviews of the range whose results could not be processed or delivered. */
function serveUnprocessed(total: number) {
	server.use(
		http.get("*/workspaces/:workspaceSlug/practices/reviews", ({ request }) => {
			if (!new URL(request.url).searchParams.getAll("resultProcessing").includes("FAILED")) {
				return;
			}
			return HttpResponse.json({
				content: [],
				page: { size: 1, number: 0, totalElements: total, totalPages: total },
			});
		}),
	);
}

/**
 * The overview's failure rates follow the range on screen, each where it happened: failed reviews
 * on the Reviews tile, failed deliveries and results not processed under Delivery. No fixed week is
 * read any more, so a week with many more failures changes none of them.
 */
describe("the overview's failure rates", () => {
	/** The week alone fails far more: were any figure to read it, it would show. */
	const busyWeek = {
		...rangeOverview,
		reviews: { ...rangeOverview.reviews, failed: 40 },
		feedback: { ...rangeOverview.feedback, failed: 9 },
	};

	it("counts the failed reviews on the Reviews tile over the range, plain at any share", async () => {
		const { overviewReads } = serveAttention(busyWeek);
		renderRouteAtWithRouter(`${OVERVIEW}?range=30d`);

		// 5 of the range's 85 finished reviews failed or timed out: above 5 %, and still plain, since
		// only what is broken and fixable now is coloured, and that is red.
		const link = await screen.findByRole(
			"link",
			{ name: "6 % failed or timed out, 5 of 85 finished reviews" },
			ROUTE_RENDER_WAIT,
		);
		const item = link.closest<HTMLElement>("[data-tone]");
		expect(item?.dataset.tone).toBe("secondary");
		expect(item?.querySelector(".text-warning, .text-destructive")).toBeNull();
		expect(item?.textContent).toBe("6 % failed or timed out");
		// Needs you keeps the approvals only.
		const needsYou = screen.getByRole("region", { name: "Needs you" });
		expect(within(needsYou).queryByText(/failed/u)).toBeNull();
		// No read of a fixed week.
		const weekStart = reviewRangeStart(
			// oxlint-disable-next-line no-restricted-properties -- the route would read its week against the wall clock.
			Date.now(),
			"7d",
		).getTime();
		expect(overviewReads.some((from) => Date.parse(from) === weekStart)).toBe(false);
	});

	it("counts the failed deliveries under Delivery over the range on screen", async () => {
		serveAttention(busyWeek);
		serveUnprocessed(4);
		renderRouteAtWithRouter(`${OVERVIEW}?range=30d`);

		const delivery = await screen.findByRole("list", { name: "Delivery" }, ROUTE_RENDER_WAIT);
		await within(delivery).findByRole("link", { name: "2 of 62 sent" }, ROUTE_RENDER_WAIT);
		const failures = within(delivery).getByRole("group", { name: "Failures" });
		expect(failures.textContent).toMatch(/^Feedback failed to deliver · 2 of 62 sent3 %/u);
		// Only a completed review has results to process, so the reviews' figure is out of the 80
		// that completed, not the 85 that finished: the 5 that failed or timed out are the Reviews
		// tile's, and no review counts in both.
		await within(failures).findByText(
			/^Reviews not processed or delivered/u,
			{},
			ROUTE_RENDER_WAIT,
		);
		await within(failures).findByRole("link", { name: /of 80 completed$/u }, ROUTE_RENDER_WAIT);
		expect(within(delivery).queryByRole("button", { name: /^Why last/u })).toBeNull();
	});
});
