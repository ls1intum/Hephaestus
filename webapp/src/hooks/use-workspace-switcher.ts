import { useMatches, useNavigate } from "@tanstack/react-router";

import { getWorkspaceRouteMatch, isPortableWorkspaceRoute } from "@/lib/workspace-switching";

export function useWorkspaceSwitcher() {
	const navigate = useNavigate();
	const portable = useMatches({
		select: (matches) =>
			isPortableWorkspaceRoute(
				getWorkspaceRouteMatch(matches.map(({ routeId, params }) => ({ routeId, params }))),
			),
	});

	return (workspaceSlug: string) => {
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
	};
}
