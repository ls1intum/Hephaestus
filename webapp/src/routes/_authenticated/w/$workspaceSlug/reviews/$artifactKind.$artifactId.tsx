import { useQuery } from "@tanstack/react-query";
import { createFileRoute, notFound } from "@tanstack/react-router";
import { TracePage } from "@/components/practice-trace/TracePage";
import { workspaceMembershipQueryOptions } from "@/integrations/auth/guard";
import { hasMinimumWorkspaceRole } from "@/lib/workspace-roles";

export const Route = createFileRoute(
	"/_authenticated/w/$workspaceSlug/reviews/$artifactKind/$artifactId",
)({
	// `artifactKind` stays the wire id (`scm.pull_request`) rather than a short slug: kinds are an
	// open vocabulary, and a slug map would make a kind this build has never heard of unreachable.
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
	// The same cache entry the sidebar and the admin guard already read, on the same schedule, so
	// asking here costs no request. It decides whether a refusal may offer its fix: this page is
	// open to every member, and most of them would only be bounced back off the admin guard.
	const membership = useQuery(workspaceMembershipQueryOptions(workspaceSlug));
	return (
		<TracePage
			workspaceSlug={workspaceSlug}
			artifactKind={artifactKind}
			artifactId={artifactId}
			canAdminister={hasMinimumWorkspaceRole(membership.data?.role, "ADMIN")}
		/>
	);
}
