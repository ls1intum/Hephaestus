import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { ReviewRunsPage } from "@/components/admin/practice-reviews/ReviewRunsPage";
import {
	type RunsSearch,
	runsSearchSchema,
} from "@/components/admin/practice-reviews/review-search";
import { workspaceAdminHead } from "@/lib/page-title";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/practices/reviews/")({
	validateSearch: runsSearchSchema,
	head: workspaceAdminHead("Practice reviews"),
	component: ReviewRunsRoute,
});

function ReviewRunsRoute() {
	const { workspaceSlug } = Route.useParams();
	const search = Route.useSearch();
	const navigate = useNavigate({ from: Route.fullPath });
	const updateSearch = (patch: Partial<RunsSearch>) =>
		navigate({ search: (previous) => ({ ...previous, ...patch }), replace: true });
	return (
		<ReviewRunsPage workspaceSlug={workspaceSlug} search={search} onSearchChange={updateSearch} />
	);
}
