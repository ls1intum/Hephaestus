import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import type { ProfileReview } from "@/api/types.gen";
import { formatDay, formatShortDay } from "@/lib/dates";
import {
	detailResult,
	detailReview,
	listedResult,
} from "@/stories/practice-detail-story-mock-data";

import { ReviewTimeline } from "./ReviewTimeline";

describe("ReviewTimeline", () => {
	it("names the day once, on the review's own card, and on none of its rows", () => {
		const denseReview: ProfileReview = {
			...detailReview,
			results: [1, 2, 3].map((n) => ({
				...listedResult(detailResult),
				id: `00000000-0000-0000-0000-00000000020${n}`,
				summary: `Result ${n}`,
			})),
		};
		render(<ReviewTimeline reviews={[denseReview]} />);

		expect(screen.getAllByText(formatShortDay(detailReview.review.createdAt))).toHaveLength(1);
		expect(screen.queryByText(formatDay(detailResult.recordedAt))).toBeNull();
	});
});
