import { createFileRoute, notFound } from "@tanstack/react-router";
import { TracePage } from "@/components/practice-trace/TracePage";

export const Route = createFileRoute(
	"/_authenticated/w/$workspaceSlug/reviews/$artifactKind/$artifactId",
)({
	// `artifactKind` stays the wire id (`scm.pull_request`) rather than a short slug: kinds are an
	// open vocabulary, and a slug map would make a kind this build has never heard of unreachable —
	// on the one page whose promise is that nothing is left out.
	loader: ({ params: { artifactId } }) => {
		const id = Number(artifactId);
		if (!Number.isSafeInteger(id) || id < 1) {
			throw notFound();
		}
		return { artifactId: id };
	},
	component: ReviewActivityDetailRoute,
});

function ReviewActivityDetailRoute() {
	const { workspaceSlug, artifactKind } = Route.useParams();
	const { artifactId } = Route.useLoaderData();
	return (
		<TracePage workspaceSlug={workspaceSlug} artifactKind={artifactKind} artifactId={artifactId} />
	);
}
