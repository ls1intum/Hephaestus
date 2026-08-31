import { createFileRoute, Outlet } from "@tanstack/react-router";

import { observationsSearchSchema } from "@/components/admin/practice-reviews/review-search";

export const Route = createFileRoute(
	"/_authenticated/w/$workspaceSlug/admin/practices/reviews/observations",
)({
	validateSearch: observationsSearchSchema,
	component: Outlet,
});
