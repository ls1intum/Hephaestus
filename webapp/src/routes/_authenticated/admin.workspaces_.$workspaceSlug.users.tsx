import { createFileRoute, redirect } from "@tanstack/react-router";

import { instanceAdminHead } from "@/lib/page-title";
import { workspaceAddressConfig } from "@/runtime/workspace-address";
import { userViewSearchSchema, WorkspaceUsersRoute } from "./-WorkspaceUsersRoute";

export const Route = createFileRoute("/_authenticated/admin/workspaces_/$workspaceSlug/users")({
	head: instanceAdminHead("View as user"),
	validateSearch: userViewSearchSchema,
	beforeLoad: ({ params, search }) => {
		if (workspaceAddressConfig.enabled) {
			throw redirect({ to: "/w/$workspaceSlug/view-as", params, search });
		}
	},
	component: WorkspaceUsers,
});

function WorkspaceUsers() {
	return (
		<WorkspaceUsersRoute
			workspaceSlug={Route.useParams().workspaceSlug}
			search={Route.useSearch()}
		/>
	);
}
