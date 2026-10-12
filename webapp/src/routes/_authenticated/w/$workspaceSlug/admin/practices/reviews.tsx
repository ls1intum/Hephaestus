import {
	createFileRoute,
	Outlet,
	redirect,
	stripSearchParams,
	useMatchRoute,
	useSearch,
} from "@tanstack/react-router";

import { PracticeReviewsLayout } from "@/components/admin/practice-reviews/PracticeReviewsLayout";
import {
	layeredStack,
	recordLevel,
	type RecordRef,
} from "@/components/admin/practice-reviews/review-layers";
import {
	PRACTICE_REVIEWS_SEARCH_DEFAULTS,
	type PracticeReviewsSearch,
	parsePracticeReviewLevels,
	practiceReviewsHead,
	practiceReviewsSearchSchema,
} from "@/components/admin/practice-reviews/review-levels";
import type { ReviewScopeSearch } from "@/components/admin/practice-reviews/review-scope";
import {
	parseSelectionSearch,
	selectionSearch,
} from "@/components/admin/practice-reviews/review-selection";
import { ReviewSelectionBar } from "@/components/admin/practice-reviews/ReviewSelectionBar";
import { detailStackKey, stackInSearch } from "@/components/layout/detail-drawer/detail-stack";
import { useDetailStack } from "@/components/layout/detail-drawer/use-detail-stack";
import { useReviewSelection } from "@/hooks/use-review-selection";
import { useReviewTabCounts } from "@/hooks/use-review-tab-counts";
import { useSearchState } from "@/lib/search-params";

import { PracticeReviewDrawer } from "./reviews/-PracticeReviewDrawer";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/practices/reviews")({
	validateSearch: practiceReviewsSearchSchema,
	// The range is the overview's, not the lists': a list has its own dates,
	// so the range is not carried to the tabs, and its default stays out of every address.
	search: {
		middlewares: [
			stripSearchParams<PracticeReviewsSearch>(PRACTICE_REVIEWS_SEARCH_DEFAULTS),
			// The queue belongs to the level it was opened as. Left behind under another level, it would
			// turn the next piece of feedback opened from a list into a queue too. An address from when
			// the trace walked its steps names one as `focus`; once a level is open it means nothing. With
			// none open it stays, since a review's old page address turns it into the trace it opens.
			({ search, next }) => {
				const result = next(search);
				const stack = stackInSearch(result.detail);
				const kept: PracticeReviewsSearch & { focus?: unknown } = {
					...result,
					queue: stack.length > 0 ? result.queue : undefined,
				};
				if (stack.length > 0) {
					delete kept.focus;
				}
				return kept;
			},
		],
	},
	// An address from before the layers held deeper stacks: it keeps what it showed in front.
	beforeLoad: ({ search, location }) => {
		const stack = parsePracticeReviewLevels(search.detail);
		const layered = layeredStack(stack);
		if (layered.length === stack.length) {
			return;
		}
		const url = new URL(location.href, "https://hephaestus.invalid");
		url.searchParams.delete("detail");
		for (const entry of layered) {
			url.searchParams.append("detail", detailStackKey(entry));
		}
		throw redirect({ href: `${url.pathname}${url.search}`, replace: true });
	},
	head: practiceReviewsHead("Practice reviews"),
	component: ReviewsLayoutRoute,
});

/** The tabs, the tracked record above them, and the sheets and the trace over them. */
function ReviewsLayoutRoute() {
	const { workspaceSlug } = Route.useParams();
	const { detail, queue } = Route.useSearch();
	const stack = parsePracticeReviewLevels(detail);
	const controls = useDetailStack();
	// The selection is in every tab's own search, which this layout's schema does not declare.
	const selectionScope = useSearch({ strict: false, select: parseSelectionSearch });
	const selected = useReviewSelection(workspaceSlug, selectionScope);
	// The shared scope is in every tab's own search too; each tab's count reads what its link carries.
	const scope: ReviewScopeSearch = useSearch({ strict: false });
	const counts = useReviewTabCounts(workspaceSlug, scope);
	const setSearch = useSearchState();
	const matchRoute = useMatchRoute();
	const onOverview =
		matchRoute({ to: "/w/$workspaceSlug/admin/practices/reviews", params: { workspaceSlug } }) !==
		false;
	const openOverTrace = (record: RecordRef) => {
		const [trace, over] = stack;
		if (trace?.kind !== "trace") {
			return;
		}
		if (over === undefined) {
			void controls.push([recordLevel(record)]);
			return;
		}
		// The one level over the trace is swapped for the next record, not stacked on it: the sheet
		// over a trace is the deepest level.
		void setSearch(
			(previous) => ({
				...previous,
				detail: [detailStackKey(trace), detailStackKey(recordLevel(record))],
			}),
			{ state: true, replace: true },
		);
	};
	return (
		<>
			<PracticeReviewsLayout
				workspaceSlug={workspaceSlug}
				counts={counts}
				selection={
					selected.picked &&
					selected.selection && (
						<ReviewSelectionBar
							kind={selected.selection.kind}
							name={selected.name}
							trace={selected.trace}
							onClear={() => {
								void setSearch((previous) => ({ ...previous, ...selectionSearch(undefined) }), {
									replace: true,
								});
							}}
							note={onOverview ? "Not applied on the overview" : undefined}
						/>
					)
				}
			>
				<Outlet />
			</PracticeReviewsLayout>
			<PracticeReviewDrawer
				workspaceSlug={workspaceSlug}
				stack={stack}
				onClose={controls.close}
				onOpenOverTrace={openOverTrace}
				// With a record opened over it the footer is covered anyway; closing back to it restores it.
				approvalQueue={queue === "approvals"}
			/>
		</>
	);
}
