import { createFileRoute } from "@tanstack/react-router";
import { AgentActivityPage } from "@/components/admin/ai/AgentActivityPage";
import { NoWorkspace } from "@/components/workspace/NoWorkspace";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/_admin/ai/activity")({
	component: ActivityContainer,
});

function ActivityContainer() {
	const { workspaceSlug, isLoading } = useActiveWorkspaceSlug();

	if (!workspaceSlug && !isLoading) {
		return <NoWorkspace />;
	}

	return <AgentActivityPage workspaceSlug={workspaceSlug ?? ""} />;
}
