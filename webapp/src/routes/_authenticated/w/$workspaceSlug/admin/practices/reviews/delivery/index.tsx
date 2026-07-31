import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { FeedbackListPage } from "@/components/admin/practice-reviews/FeedbackListPage";
import type { FeedbackSearch } from "@/components/admin/practice-reviews/review-search";
import { workspaceAdminHead } from "@/lib/page-title";

export const Route = createFileRoute(
	"/_authenticated/w/$workspaceSlug/admin/practices/reviews/delivery/",
)({
	head: workspaceAdminHead("Feedback delivery"),
	component: FeedbackListRoute,
});

function FeedbackListRoute() {
	const { workspaceSlug } = Route.useParams();
	const search = Route.useSearch();
	const navigate = useNavigate({ from: Route.fullPath });
	const updateSearch = (patch: Partial<FeedbackSearch>) =>
		navigate({
			search: (previous) => {
				const next = { ...previous, ...patch };
				return { ...next, page: next.page || undefined };
			},
			replace: true,
		});

	return (
		<FeedbackListPage workspaceSlug={workspaceSlug} search={search} onSearchChange={updateSearch} />
	);
}
