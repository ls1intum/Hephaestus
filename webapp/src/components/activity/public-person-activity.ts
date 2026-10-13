import type {
	PublicActivityCounts,
	PublicActivityPersonDetail,
	PublicActivityReviewBreakdown,
} from "@/api/types.gen";
import type { PanelState } from "@/components/common/panel-state";
import { formatUtcDayRange } from "@/lib/dates";

import type { ActivityCategoryChartState } from "./activity-category-chart";
import {
	ACTIVITY_CATEGORY_DEFS,
	type ActivityCategory,
	type ActivityKind,
} from "./activity-kind-defs";
import { weekStarts } from "./activity-tally";

export function publicActivitySpanLabel(from: Date, to: Date): string | undefined {
	return to.getTime() > from.getTime()
		? formatUtcDayRange(from, new Date(to.getTime() - 1))
		: undefined;
}

export type PublicActivityCategory = "pull-requests" | "reviews" | "issues";
/** One source mapping owns which public event counts this presentation supports. */
const PUBLIC_KIND_COUNTS: Partial<
	Record<
		ActivityKind,
		(counts: PublicActivityCounts, breakdown: PublicActivityReviewBreakdown) => number
	>
> = {
	PULL_REQUEST_OPENED: (counts) => counts.pullRequestsOpened,
	PULL_REQUEST_MERGED: (counts) => counts.pullRequestsMerged,
	REVIEW_APPROVED: (_counts, breakdown) => breakdown.approvals,
	REVIEW_CHANGES_REQUESTED: (_counts, breakdown) => breakdown.changeRequests,
	REVIEW_COMMENTED: (_counts, breakdown) => breakdown.commentReviews,
	ISSUE_OPENED: (counts) => counts.issuesOpened,
};

export function publicCategoryKinds(category: ActivityCategory): readonly ActivityKind[] {
	return ACTIVITY_CATEGORY_DEFS[category].kinds.filter(
		(kind) => PUBLIC_KIND_COUNTS[kind] !== undefined,
	);
}

export type PublicPersonActivityState = PanelState<{
	person: PublicActivityPersonDetail;
	stale: boolean;
}> & { seedAllowed?: boolean };

/** The range's distinct-work total and each week's total keep their own grains. */
export function publicCategoryChartState(
	state: PublicPersonActivityState,
	category: PublicActivityCategory,
): ActivityCategoryChartState {
	if (state.status !== "ready") {
		return state;
	}
	const { person } = state;
	const def = ACTIVITY_CATEGORY_DEFS[category];
	const kinds = def.kinds.filter((kind) => person.supportedKinds.includes(kind));
	const actions = (counts: PublicActivityCounts, breakdown: PublicActivityReviewBreakdown) =>
		kinds.flatMap((kind) => {
			const countOf = PUBLIC_KIND_COUNTS[kind];
			return countOf === undefined ? [] : [{ kind, count: countOf(counts, breakdown) }];
		});
	const headline = (counts: PublicActivityCounts) => {
		switch (category) {
			case "pull-requests": {
				return counts.pullRequestsMerged;
			}
			case "reviews": {
				return counts.pullRequestsReviewed;
			}
			case "issues": {
				return counts.issuesOpened;
			}
		}
	};
	const byWeek = new Map(person.weeks.map((week) => [week.start.getTime(), week]));
	const chart = {
		category,
		total: headline(person.counts),
		kinds: actions(person.counts, person.breakdown),
		weeks: weekStarts(person.from, person.to).map((start) => {
			const week = byWeek.get(start.getTime());
			return {
				start,
				count: week === undefined ? 0 : headline(week.counts),
				kinds:
					week === undefined
						? kinds.map((kind) => ({ kind, count: 0 }))
						: actions(week.counts, week.breakdown),
			};
		}),
	};
	return state.stale
		? { status: "ready", chart, stale: true }
		: { status: "ready", chart, stale: false, span: { from: person.from, to: person.to } };
}
