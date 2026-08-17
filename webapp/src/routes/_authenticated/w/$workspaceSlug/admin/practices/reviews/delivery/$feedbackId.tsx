import { useQuery } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import {
	getPracticeReviewFeedbackOptions,
	listPracticesOptions,
} from "@/api/@tanstack/react-query.gen";
import { FeedbackDetailPage } from "@/components/admin/practice-reviews/FeedbackDetailPage";
import { workspaceAdminHead } from "@/lib/page-title";

export const Route = createFileRoute(
	"/_authenticated/w/$workspaceSlug/admin/practices/reviews/delivery/$feedbackId",
)({
	head: workspaceAdminHead("Feedback details"),
	component: FeedbackDetailRoute,
});

function FeedbackDetailRoute() {
	const { workspaceSlug, feedbackId } = Route.useParams();
	const search = Route.useSearch();

	const feedbackQueryResult = useQuery({
		...getPracticeReviewFeedbackOptions({ path: { workspaceSlug, feedbackId } }),
	});
	const practicesQuery = useQuery({ ...listPracticesOptions({ path: { workspaceSlug } }) });

	return (
		<FeedbackDetailPage
			workspaceSlug={workspaceSlug}
			search={search}
			feedback={feedbackQueryResult.data}
			isLoading={feedbackQueryResult.isLoading}
			error={feedbackQueryResult.isError ? feedbackQueryResult.error : undefined}
			onRetry={() => feedbackQueryResult.refetch()}
			practices={practicesQuery.data}
		/>
	);
}
