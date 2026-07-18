import { createFileRoute, Outlet } from "@tanstack/react-router";
import { SyncFreshnessBanner } from "@/components/admin/integrations/SyncFreshnessBanner";
import { Spinner } from "@/components/ui/spinner";
import { NoWorkspace } from "@/components/workspace/NoWorkspace";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";
import { useSyncEvents } from "@/hooks/use-sync-events";
import { SyncLivenessProvider } from "@/hooks/use-sync-liveness";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/integrations")({
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
		<SyncLivenessProvider livePushUnavailable={livePushUnavailable}>
			{/* Offline outranks live-push-lost: while offline TanStack pauses queries rather than failing
			    them, so the polling this banner would otherwise promise isn't happening either. */}
			<SyncFreshnessBanner />
			<Outlet />
		</SyncLivenessProvider>
	);
}
