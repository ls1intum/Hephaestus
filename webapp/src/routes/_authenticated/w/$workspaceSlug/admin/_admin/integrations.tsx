import { createFileRoute, Outlet } from "@tanstack/react-router";
import { Spinner } from "@/components/ui/spinner";
import { NoWorkspace } from "@/components/workspace/NoWorkspace";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";
import { useSyncEvents } from "@/hooks/use-sync-events";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/_admin/integrations")({
	component: IntegrationsLayout,
});

function IntegrationsLayout() {
	const { workspaceSlug, isLoading } = useActiveWorkspaceSlug();

	const livePushUnavailable = useSyncEvents(workspaceSlug);

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

	return (
		<>
			{livePushUnavailable && (
				<div
					role="status"
					aria-live="polite"
					className="border-b px-6 py-2 text-muted-foreground text-xs"
				>
					Live updates are unavailable — this section is refreshing periodically instead.
				</div>
			)}
			<Outlet />
		</>
	);
}
