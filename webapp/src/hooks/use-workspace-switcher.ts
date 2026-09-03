import { useMatches, useNavigate } from "@tanstack/react-router";
import { toast } from "sonner";

import { getWorkspaceRouteMatch, isPortableWorkspaceRoute } from "@/lib/workspace-switching";

export function useWorkspaceSwitcher() {
	const navigate = useNavigate();
	const portable = useMatches({
		select: (matches) =>
			isPortableWorkspaceRoute(getWorkspaceRouteMatch(matches.map(({ params }) => ({ params })))),
	});

	return (workspace: { displayName: string; workspaceSlug: string }) => {
		const { displayName, workspaceSlug } = workspace;
		if (portable) {
			void navigate({
				to: ".",
				params: (previous) => ({ ...previous, workspaceSlug }),
				search: () => ({}),
				replace: true,
			});
			return;
		}

		void navigate({
			to: "/w/$workspaceSlug",
			params: { workspaceSlug },
			search: () => ({}),
			replace: true,
		});
		toast.info(`Switched to ${displayName}`, {
			description:
				"This page is specific to the previous workspace, so we opened the new workspace's home page.",
		});
	};
}
