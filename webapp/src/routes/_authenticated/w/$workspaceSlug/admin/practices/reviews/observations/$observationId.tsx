import { createFileRoute } from "@tanstack/react-router";
import { ObservationDetailPage } from "@/components/admin/practice-reviews/ObservationDetailPage";
import { workspaceAdminHead } from "@/lib/page-title";

export const Route = createFileRoute(
	"/_authenticated/w/$workspaceSlug/admin/practices/reviews/observations/$observationId",
)({
	head: workspaceAdminHead("Observation"),
	component: ObservationDetailRoute,
});

function ObservationDetailRoute() {
	const { workspaceSlug, observationId } = Route.useParams();
	const search = Route.useSearch();
	return (
		<ObservationDetailPage
			workspaceSlug={workspaceSlug}
			observationId={observationId}
			search={search}
		/>
	);
}
