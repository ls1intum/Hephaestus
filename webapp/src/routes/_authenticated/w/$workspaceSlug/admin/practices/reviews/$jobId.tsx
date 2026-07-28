import { createFileRoute } from "@tanstack/react-router";
import { ReviewRunDetailPage } from "@/components/admin/practice-reviews/ReviewRunDetailPage";
import { runsSearchSchema } from "@/components/admin/practice-reviews/review-search";
import { workspaceAdminHead } from "@/lib/page-title";

export const Route = createFileRoute(
	"/_authenticated/w/$workspaceSlug/admin/practices/reviews/$jobId",
)({
	validateSearch: runsSearchSchema,
	head: workspaceAdminHead("Review details"),
	component: ReviewRunDetailRoute,
});

function ReviewRunDetailRoute() {
	const { workspaceSlug, jobId } = Route.useParams();
	const search = Route.useSearch();
	return <ReviewRunDetailPage workspaceSlug={workspaceSlug} jobId={jobId} search={search} />;
}
