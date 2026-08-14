import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { ObservationsListPage } from "@/components/admin/practice-reviews/ObservationsListPage";
import type { ObservationsSearch } from "@/components/admin/practice-reviews/review-search";
import { workspaceAdminHead } from "@/lib/page-title";

export const Route = createFileRoute(
	"/_authenticated/w/$workspaceSlug/admin/practices/reviews/observations/",
)({
	head: workspaceAdminHead("Observations"),
	component: ObservationsListRoute,
});

function ObservationsListRoute() {
	const { workspaceSlug } = Route.useParams();
	const search = Route.useSearch();
	const navigate = useNavigate({ from: Route.fullPath });
	const updateSearch = (patch: Partial<ObservationsSearch>) =>
		navigate({
			search: (previous) => {
				const next = { ...previous, ...patch };
				return { ...next, page: next.page || undefined };
			},
			replace: true,
		});

	return (
		<ObservationsListPage
			workspaceSlug={workspaceSlug}
			search={search}
			onSearchChange={updateSearch}
		/>
	);
}
