import { createFileRoute, notFound, Outlet } from "@tanstack/react-router";
import { getWorkspace } from "@/api/sdk.gen";
import type { Workspace } from "@/api/types.gen";
import { WorkspaceError } from "@/components/workspace/WorkspaceError";
import { WorkspaceForbidden } from "@/components/workspace/WorkspaceForbidden";
import { WorkspaceNotFound } from "@/components/workspace/WorkspaceNotFound";
import { WorkspaceContext } from "@/hooks/use-workspace";

/**
 * Error class for 403 Forbidden responses from the workspace API.
 * Thrown when the user lacks permission to access a workspace.
 */
class WorkspaceForbiddenError extends Error {
	constructor(public slug: string) {
		super(`Access denied to workspace: ${slug}`);
		this.name = "WorkspaceForbiddenError";
	}
}

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug")({
	loader: async ({ params }): Promise<Workspace> => {
		const { workspaceSlug } = params;

		const result = await getWorkspace({
			path: { workspaceSlug },
			throwOnError: false,
		});

		// Handle error responses with specific error types for different HTTP statuses
		if (result.error || !result.response.ok) {
			const status = result.response.status;
			if (status === 404) {
				throw notFound();
			}
			if (status === 403) {
				throw new WorkspaceForbiddenError(workspaceSlug);
			}
			// Throw a descriptive error for other status codes
			throw new Error(`Failed to load workspace: HTTP ${status}`);
		}

		// At this point we know result.data exists because response.ok is true
		if (!result.data) {
			throw new Error("Unexpected empty workspace response");
		}

		return result.data;
	},
	notFoundComponent: () => {
		const { workspaceSlug } = Route.useParams();
		return <WorkspaceNotFound slug={workspaceSlug} />;
	},
	errorComponent: ({ error, reset }) => {
		// Handle specific error types with dedicated components
		if (error instanceof WorkspaceForbiddenError) {
			return <WorkspaceForbidden slug={error.slug} />;
		}
		// Handle generic errors with retry capability
		return <WorkspaceError error={error} reset={reset} />;
	},
	component: WorkspaceLayout,
});

function WorkspaceLayout() {
	const workspace = Route.useLoaderData();

	return (
		<WorkspaceContext value={workspace}>
			<Outlet />
		</WorkspaceContext>
	);
}
