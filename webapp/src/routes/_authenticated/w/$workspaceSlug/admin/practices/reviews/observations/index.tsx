import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { FindingsListPage } from "@/components/admin/practice-reviews/FindingsListPage";
import type { FindingsSearch } from "@/components/admin/practice-reviews/review-search";
import { workspaceAdminHead } from "@/lib/page-title";

export const Route = createFileRoute(
	"/_authenticated/w/$workspaceSlug/admin/practices/reviews/findings/",
)({
	head: workspaceAdminHead("Findings"),
	component: FindingsListRoute,
});

function FindingsListRoute() {
	const { workspaceSlug } = Route.useParams();
	const search = Route.useSearch();
	const navigate = useNavigate({ from: Route.fullPath });
	const updateSearch = (patch: Partial<FindingsSearch>) =>
		navigate({
			search: (previous) => {
				const next = { ...previous, ...patch };
				return { ...next, page: next.page || undefined };
			},
			replace: true,
		});

	return (
		<FindingsListPage workspaceSlug={workspaceSlug} search={search} onSearchChange={updateSearch} />
	);
}
