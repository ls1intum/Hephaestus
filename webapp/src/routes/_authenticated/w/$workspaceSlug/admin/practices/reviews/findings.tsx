import { createFileRoute, Outlet } from "@tanstack/react-router";
import { findingsSearchSchema } from "@/components/admin/practice-reviews/review-search";

export const Route = createFileRoute(
	"/_authenticated/w/$workspaceSlug/admin/practices/reviews/findings",
)({
	validateSearch: findingsSearchSchema,
	component: Outlet,
});
