import { useInfiniteQuery, useQuery } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";

import {
	countPracticeReviewFeedbackByLocationOptions,
	listPracticeReviewFeedbackInfiniteOptions,
} from "@/api/@tanstack/react-query.gen";
import { FeedbackListPage } from "@/components/admin/practice-reviews/FeedbackListPage";
import { practiceReviewsHead } from "@/components/admin/practice-reviews/review-levels";
import {
	type FeedbackSearch,
	feedbackQuery,
	feedbackSearchSchema,
	REVIEW_PAGE_SIZE,
} from "@/components/admin/practice-reviews/review-search";
import { panelState } from "@/components/common/panel-state";
import { useReviewScopeSources } from "@/hooks/use-review-scope-sources";
import { useScopeRecordFilter } from "@/hooks/use-scope-record-filter";
import { useSearchState } from "@/lib/search-params";
import { pagedListState } from "@/runtime/tanstack-query/infinite-list";
import { pagedModelParams } from "@/runtime/tanstack-query/spring-page";

export const Route = createFileRoute(
	"/_authenticated/w/$workspaceSlug/admin/practices/reviews/feedback",
)({
	validateSearch: feedbackSearchSchema,
	head: practiceReviewsHead("Feedback"),
	component: FeedbackListRoute,
});

function FeedbackListRoute() {
	const { workspaceSlug } = Route.useParams();
	const search = Route.useSearch();
	const setSearch = useSearchState();
	const updateSearch = (patch: Partial<FeedbackSearch>) => {
		void setSearch((previous) => ({ ...previous, ...patch }), { replace: true });
	};

	const feedbackQueryResult = useInfiniteQuery({
		...listPracticeReviewFeedbackInfiniteOptions({
			path: { workspaceSlug },
			query: feedbackQuery(search, REVIEW_PAGE_SIZE),
		}),
		...pagedModelParams,
	});
	const { groups, practices, people } = useReviewScopeSources(workspaceSlug);
	// Everything addressed to the recipient filtered to, whatever else is filtered: the summary is
	// about the person, and its counts open the list narrowed to them alone.
	const recipientCountsQuery = useQuery({
		...countPracticeReviewFeedbackByLocationOptions({
			path: { workspaceSlug },
			query: { recipientUserId: search.recipientUserId },
		}),
		enabled: search.recipientUserId !== undefined,
	});

	const record = useScopeRecordFilter(workspaceSlug, search);

	return (
		<FeedbackListPage
			record={record}
			workspaceSlug={workspaceSlug}
			search={search}
			onSearchChange={updateSearch}
			feedback={pagedListState(feedbackQueryResult, (feedback) => feedback.id)}
			groups={groups}
			practices={practices}
			people={people}
			recipientCounts={panelState(recipientCountsQuery, (counts) => ({
				status: "ready" as const,
				counts,
			}))}
		/>
	);
}
