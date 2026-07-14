import { createFileRoute, Outlet } from "@tanstack/react-router";
import { Spinner } from "@/components/ui/spinner";
import { NoWorkspace } from "@/components/workspace/NoWorkspace";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";
import { useSyncEvents } from "@/hooks/useSyncEvents";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/_admin/integrations")({
	component: IntegrationsLayout,
});

function IntegrationsLayout() {
	const { workspaceSlug, isLoading } = useActiveWorkspaceSlug();

	// One SSE subscription for the whole section — every child page's queries get invalidated
	// through the generated query keys as hints arrive. No-ops until `workspaceSlug` resolves.
	useSyncEvents(workspaceSlug);

	if (!workspaceSlug && !isLoading) {
		return <NoWorkspace />;
	}
	if (!workspaceSlug) {
		return (
			<div className="flex h-64 items-center justify-center">
				<Spinner className="h-8 w-8" />
			</div>
		);
	}

	// Sub-navigation (Overview / SCM / Slack / Outline) lives in the sidebar's collapsible
	// "Integrations" group; each child route owns its own header and container.
	return <Outlet />;
}
