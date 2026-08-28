import { createFileRoute, Outlet } from "@tanstack/react-router";

import { SyncFreshnessBanner } from "@/components/admin/integrations/SyncFreshnessBanner";
import { Spinner } from "@/components/ui/spinner";
import { NoWorkspace } from "@/components/workspace/NoWorkspace";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";
import { useSyncEvents } from "@/hooks/use-sync-events";
import { SyncLivenessProvider } from "@/hooks/use-sync-liveness";
import { workspaceAdminHead } from "@/lib/page-title";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/integrations")({
	head: workspaceAdminHead("Integrations"),
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
			<div className="space-y-6">
				<SyncFreshnessBanner />
				<Outlet />
			</div>
		</SyncLivenessProvider>
	);
}
