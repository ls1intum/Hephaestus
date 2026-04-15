import { useMatches, useNavigate } from "@tanstack/react-router";
import { buildWorkspaceSwitchPlan, getWorkspaceRouteMatch } from "@/lib/workspace-switching";
import { useWorkspaceStore } from "@/stores/workspace-store";

export function useWorkspaceSwitcher() {
	const navigate = useNavigate();
	const setSelectedSlug = useWorkspaceStore((state) => state.setSelectedSlug);
	const workspaceRoute = useMatches({
		select: (matches) => getWorkspaceRouteMatch(matches),
	});

	return (workspaceSlug: string) => {
		if (!workspaceRoute) {
			setSelectedSlug(workspaceSlug);
		}

		const target = buildWorkspaceSwitchPlan(workspaceRoute, workspaceSlug);

		if (target.kind === "relative") {
			if (target.preserveSearch) {
				navigate({
					from: target.from as never,
					to: target.to as never,
					params: { workspaceSlug } as never,
					search: true,
					replace: true,
				});
			} else {
				navigate({
					from: target.from as never,
					to: target.to as never,
					params: { workspaceSlug } as never,
					replace: true,
				});
			}
			return;
		}

		navigate({
			to: target.to,
			params: target.params,
			...(target.preserveSearch ? { search: (prev) => prev } : {}),
			replace: true,
		});
	};
}
