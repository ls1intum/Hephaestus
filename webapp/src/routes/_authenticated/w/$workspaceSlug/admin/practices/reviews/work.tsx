import { createFileRoute, redirect } from "@tanstack/react-router";

import {
	parsePracticeReviewLevels,
	parseWorkLevel,
} from "@/components/admin/practice-reviews/review-levels";
import { carriedScope } from "@/components/admin/practice-reviews/review-scope";
import { workSearchSchema } from "@/components/admin/practice-reviews/review-search";
import { NO_REVIEW_STARTED } from "@/components/practice-vocabulary/review-status-defs";

/**
 * The Work tab is gone. What it alone showed was the work no review started on, with why: that is
 * now the Reviews tab's Status "No review started". An address of the tab goes there, keeping the scope and
 * the selection, and a record it opened stays open. The work kind it filtered by has no facet
 * there, so it is dropped. An address that opens one piece of work, as the browser extension's
 * "review details" link does, lands on that work's own reviews instead, under its sheet.
 */
export const Route = createFileRoute(
	"/_authenticated/w/$workspaceSlug/admin/practices/reviews/work",
)({
	validateSearch: workSearchSchema,
	beforeLoad: ({ search, params }) => {
		const opened = parsePracticeReviewLevels(search.detail).find((level) => level.kind === "work");
		const work = opened === undefined ? undefined : parseWorkLevel(opened.id);
		if (work !== undefined) {
			throw redirect({
				to: "/w/$workspaceSlug/admin/practices/reviews/runs",
				params: { workspaceSlug: params.workspaceSlug },
				search: {
					...carriedScope("runs", search),
					artifactKind: work.artifactKind,
					artifactId: work.artifactId,
					detail: search.detail,
				},
				replace: true,
			});
		}
		throw redirect({
			to: "/w/$workspaceSlug/admin/practices/reviews/runs",
			params: { workspaceSlug: params.workspaceSlug },
			search: {
				...carriedScope("runs", search),
				status: [NO_REVIEW_STARTED],
				detail: search.detail,
			},
			replace: true,
		});
	},
});
