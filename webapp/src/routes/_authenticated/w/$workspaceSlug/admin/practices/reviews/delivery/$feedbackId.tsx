import { createFileRoute } from "@tanstack/react-router";
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
	return (
		<FeedbackDetailPage workspaceSlug={workspaceSlug} feedbackId={feedbackId} search={search} />
	);
}
