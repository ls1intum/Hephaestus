import { screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { reviewRangeStart } from "@/components/admin/practice-reviews/review-range";
import { ObserverStub } from "@/test/observers";
import { ROUTE_RENDER_WAIT, renderRouteAtWithRouter } from "@/test/router-harness";

import { OVERVIEW, rangeOverview, serveAttention } from "./-overview-attention-server";

vi.setConfig({ testTimeout: 30_000 });

// jsdom has no IntersectionObserver, and the lists load their next page as their end scrolls into view.
beforeEach(() => {
	vi.stubGlobal("IntersectionObserver", ObserverStub);
});

/**
 * The overview's failure rates follow the range on screen, each where it happened: failed reviews
 * on the Reviews tile, failed deliveries under Delivery. No fixed week is
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

		// 5 of the range's 85 finished reviews failed: above 5 %, and still plain, since only what is
		// broken and fixable now is coloured, and that is red.
		const link = await screen.findByRole(
			"link",
			{ name: "6 % failed, 5 of 85 finished reviews" },
			ROUTE_RENDER_WAIT,
		);
		const item = link.closest<HTMLElement>("[data-tone]");
		expect(item?.dataset.tone).toBe("secondary");
		expect(item?.querySelector(".text-warning, .text-destructive")).toBeNull();
		expect(item?.textContent).toBe("6 % failed");
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
		renderRouteAtWithRouter(`${OVERVIEW}?range=30d`);

		const delivery = await screen.findByRole("list", { name: "Delivery" }, ROUTE_RENDER_WAIT);
		await within(delivery).findByRole("link", { name: "2 of 62 sent" }, ROUTE_RENDER_WAIT);
		const failures = within(delivery).getByRole("group", { name: "Failures" });
		expect(failures.textContent).toMatch(/^Feedback failed to deliver · 2 of 62 sent3 %/u);
		expect(within(delivery).queryByRole("button", { name: /^Why last/u })).toBeNull();
	});
});
