import { createFileRoute, redirect } from "@tanstack/react-router";

import { type RecordRef, traceLevel } from "@/components/admin/practice-reviews/review-layers";
import { carriedScope } from "@/components/admin/practice-reviews/review-scope";
import { reviewPageSearchSchema } from "@/components/admin/practice-reviews/review-search";
import { parseSelectionSearch } from "@/components/admin/practice-reviews/review-selection";
import { parseTraceFocus } from "@/components/admin/practice-reviews/review-trace";
import { detailStackKey } from "@/components/layout/detail-drawer/detail-stack";

/**
 * A review has no page of its own any more: its trace is a sheet over the Reviews tab. An address of
 * the page — a link from before, or one shared — opens a trace with the record the tables track. A
 * link that focused one observation or piece of feedback of the review opens that record's trace, so
 * the trace marks where the reader started; any other link opens the review's. The scope it carried,
 * such as the practice, the dates and the person, is kept.
 */
export const Route = createFileRoute(
	"/_authenticated/w/$workspaceSlug/admin/practices/reviews/review/$reviewId",
)({
	validateSearch: reviewPageSearchSchema,
	beforeLoad: ({ search, params }) => {
		const focus = parseTraceFocus(search.focus);
		const record: RecordRef =
			focus?.kind === "observation" || focus?.kind === "feedback"
				? { kind: focus.kind, id: focus.id }
				: { kind: "review", id: params.reviewId };
		throw redirect({
			to: "/w/$workspaceSlug/admin/practices/reviews/runs",
			params: { workspaceSlug: params.workspaceSlug },
			search: {
				...carriedScope("runs", search),
				...parseSelectionSearch(search),
				detail: [detailStackKey(traceLevel(record))],
			},
			replace: true,
		});
	},
});
