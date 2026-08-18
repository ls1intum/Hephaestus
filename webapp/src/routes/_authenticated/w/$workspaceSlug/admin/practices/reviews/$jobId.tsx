import { useQuery } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { listPracticesOptions } from "@/api/@tanstack/react-query.gen";
import { ReviewRunDetailPage } from "@/components/admin/practice-reviews/ReviewRunDetailPage";
import { runsSearchSchema } from "@/components/admin/practice-reviews/review-search";
import { useReviewRunController } from "@/hooks/use-review-run-controller";
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
	// The three queries, their polling and the two actions live in the hook; what arrives here is
	// already the answer.
	const run = useReviewRunController(workspaceSlug, jobId);
	// Each observation row names its practice by slug, and the hover card on that name needs the
	// practice itself. One list for the page, shared by query key with every other screen that asks
	// for it.
	const practicesQuery = useQuery({ ...listPracticesOptions({ path: { workspaceSlug } }) });
	return (
		<ReviewRunDetailPage
			workspaceSlug={workspaceSlug}
			jobId={jobId}
			search={search}
			{...run}
			practices={practicesQuery.data}
		/>
	);
}
