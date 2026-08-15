import { createFileRoute } from "@tanstack/react-router";
import { ReflectionPage } from "@/components/reflection/ReflectionPage";
import { useReflectionFeedback } from "@/hooks/use-reflection-feedback";

/**
 * The developer's own reflection surface.
 *
 * <p>A sibling of `/achievements` rather than a child of `/user/$username`, and the URL is the
 * reason: the public profile is addressed by whose it is, while this page has no such address. The
 * endpoint behind it takes no user parameter and answers only for the caller, so a URL carrying a
 * username would invite a reader to edit it and imply an answer that does not exist.
 *
 * <p>Two names meet here on purpose. The code is named for the feedback lane — `REFLECTION`, the
 * process level, the same word the schema, the endpoint and the components use. The URL and every
 * word a developer reads say "my feedback", because that is what the page is to them; "reflection"
 * is the model's word for the lane, not a label anybody would recognise in a sidebar.
 */
export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/my-feedback")({
	component: MyFeedbackRoute,
});

function MyFeedbackRoute() {
	const { workspaceSlug } = Route.useParams();
	// Deliberately fetched in the component and not in a loader: the server treats the read as the
	// delivery, and a loader would record the feedback as received while the reader is still on the
	// page they navigated from.
	const reflection = useReflectionFeedback(workspaceSlug);

	return (
		<ReflectionPage
			workspaceSlug={workspaceSlug}
			feedback={reflection.feedback}
			isLoading={reflection.isLoading}
			error={reflection.error}
			onRetry={reflection.refetch}
		/>
	);
}
