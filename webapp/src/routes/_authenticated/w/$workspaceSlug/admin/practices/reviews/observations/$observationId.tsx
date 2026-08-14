import { createFileRoute } from "@tanstack/react-router";
import { FindingDetailPage } from "@/components/admin/practice-reviews/FindingDetailPage";
import { workspaceAdminHead } from "@/lib/page-title";

export const Route = createFileRoute(
	"/_authenticated/w/$workspaceSlug/admin/practices/reviews/findings/$findingId",
)({
	head: workspaceAdminHead("Finding details"),
	component: FindingDetailRoute,
});

function FindingDetailRoute() {
	const { workspaceSlug, findingId } = Route.useParams();
	const search = Route.useSearch();
	return <FindingDetailPage workspaceSlug={workspaceSlug} findingId={findingId} search={search} />;
}
