import { createFileRoute, Outlet } from "@tanstack/react-router";
import { feedbackSearchSchema } from "@/components/admin/practice-reviews/review-search";

export const Route = createFileRoute(
	"/_authenticated/w/$workspaceSlug/admin/practices/reviews/delivery",
)({
	validateSearch: feedbackSearchSchema,
	component: Outlet,
});
