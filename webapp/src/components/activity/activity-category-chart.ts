import type { ActivityAction } from "@/api/types.gen";
import type { PanelState } from "@/components/common/panel-state";

import type { ActivityOverviewState, DateSpan } from "./activity-buckets";
import { ACTIVITY_CATEGORY_DEFS, type ActivityCategory } from "./activity-kind-defs";

/** A chart carries only the kinds its source counts, including their quiet weeks. */
export interface ActivityCategoryChart {
	category: ActivityCategory;
	total: number;
	kinds: readonly ActivityAction[];
	weeks: readonly {
		start: Date;
		count: number;
		kinds: readonly ActivityAction[];
	}[];
}

export type ActivityCategoryChartState = PanelState<
	{ chart: ActivityCategoryChart } & (
		| { stale: false; span: DateSpan; previous?: { total: number; name: string } }
		| { stale: true }
	)
>;

/** Member reads supply every category kind and their distinct-work headline separately. */
export function categoryChartStateOf(
	state: ActivityOverviewState,
	category: ActivityCategory,
): ActivityCategoryChartState {
	if (state.status !== "ready") {
		return state;
	}
	const def = ACTIVITY_CATEGORY_DEFS[category];
	const { overview } = state;
	const chart = {
		category,
		total: def.headline.count(overview.tally),
		kinds: def.kinds.map((kind) => ({ kind, count: overview.tally[kind] })),
		weeks: overview.weeks.map(({ start, tally }) => ({
			start,
			count: def.headline.count(tally),
			kinds: def.kinds.map((kind) => ({ kind, count: tally[kind] })),
		})),
	};
	return state.stale
		? { status: "ready", chart, stale: true }
		: {
				status: "ready",
				chart,
				stale: false,
				span: state.span,
				previous: state.previous && {
					total: def.headline.count(state.previous.tally),
					name: state.previous.name,
				},
			};
}
