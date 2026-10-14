import { createFileRoute, redirect } from "@tanstack/react-router";

import { type RecordRef, traceLevel } from "@/components/admin/practice-reviews/review-layers";
import { carriedScope } from "@/components/admin/practice-reviews/review-scope";
import { reviewPageSearchSchema } from "@/components/admin/practice-reviews/review-search";
import { parseSelectionSearch } from "@/components/admin/practice-reviews/review-selection";
import { parseTraceFocus } from "@/components/admin/practice-reviews/review-trace";
import { detailStackKey } from "@/components/layout/detail-drawer/detail-stack";

// Shared review URLs open the trace over the Reviews tab.
export const Route = createFileRoute(
	"/_authenticated/w/$workspaceSlug/admin/practices/reviews/review/$reviewId",
)({
	validateSearch: reviewPageSearchSchema,
	beforeLoad: ({ search, params }) => {
		const focus = parseTraceFocus(search.focus);
		const record: RecordRef =
			focus?.kind === "result" || focus?.kind === "feedback"
				? { kind: focus.kind, id: focus.id }
				: { kind: "review", id: params.reviewId };
		throw redirect({
			to: "/w/$workspaceSlug/admin/practices/reviews/runs",
			params: { workspaceSlug: params.workspaceSlug },
			search: {
				...carriedScope(search),
				...parseSelectionSearch(search),
				detail: [detailStackKey(traceLevel(record))],
			},
			replace: true,
		});
	},
});
