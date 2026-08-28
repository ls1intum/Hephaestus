import { createFileRoute, Outlet } from "@tanstack/react-router";

import { PracticeReviewsLayout } from "@/components/admin/practice-reviews/PracticeReviewsLayout";
import { workspaceAdminHead } from "@/lib/page-title";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/practices/reviews")({
	head: workspaceAdminHead("Practice reviews"),
	component: ReviewsLayoutRoute,
});

function ReviewsLayoutRoute() {
	const { workspaceSlug } = Route.useParams();
	return (
		<PracticeReviewsLayout workspaceSlug={workspaceSlug}>
			<Outlet />
		</PracticeReviewsLayout>
	);
}
