import { createFileRoute, notFound } from "@tanstack/react-router";
import { reviewArtifactTypeFromSlug } from "@/components/admin/practice-reviews/ReviewArtifact";
import { ReviewTargetPage } from "@/components/admin/practice-reviews/ReviewTargetPage";
import { workspaceAdminHead } from "@/lib/page-title";

export const Route = createFileRoute(
	"/_authenticated/w/$workspaceSlug/admin/practices/reviews/targets/$artifactType/$artifactId",
)({
	head: workspaceAdminHead("Reviewed work output"),
	loader: ({ params: { artifactType, artifactId } }) => {
		const type = reviewArtifactTypeFromSlug(artifactType);
		const id = Number(artifactId);
		if (!type || !Number.isSafeInteger(id) || id < 1) {
			throw notFound();
		}
		return { artifactType: type, artifactId: id };
	},
	component: ReviewTargetRoute,
});

function ReviewTargetRoute() {
	const { workspaceSlug } = Route.useParams();
	const { artifactType, artifactId } = Route.useLoaderData();
	return (
		<ReviewTargetPage
			workspaceSlug={workspaceSlug}
			artifactType={artifactType}
			artifactId={artifactId}
		/>
	);
}
