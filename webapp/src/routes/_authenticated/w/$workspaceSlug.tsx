import { createFileRoute, notFound, Outlet } from "@tanstack/react-router";
import { getWorkspace } from "@/api/sdk.gen";
import type { Workspace } from "@/api/types.gen";
import { WorkspaceForbidden } from "@/components/workspace/WorkspaceForbidden";
import { WorkspaceNotFound } from "@/components/workspace/WorkspaceNotFound";
import { WorkspaceContext } from "@/hooks/use-workspace";

/**
 * Error class for 403 Forbidden responses from the workspace API.
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

		// Handle error responses
		if (result.error || !result.response.ok) {
			const status = result.response.status;
			if (status === 404) {
				throw notFound();
			}
			if (status === 403) {
				throw new WorkspaceForbiddenError(workspaceSlug);
			}
			throw new Error(`Failed to load workspace: ${status}`);
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
	errorComponent: ({ error }) => {
		if (error instanceof WorkspaceForbiddenError) {
			return <WorkspaceForbidden slug={error.slug} />;
		}
		// Re-throw other errors to bubble up to parent error boundaries
		throw error;
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
