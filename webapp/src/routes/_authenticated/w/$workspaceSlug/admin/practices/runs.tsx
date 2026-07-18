import { createFileRoute } from "@tanstack/react-router";
import { AgentActivityPage } from "@/components/admin/ai/AgentActivityPage";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/practices/runs")({
	component: RunsContainer,
});

function RunsContainer() {
	const { workspaceSlug } = useActiveWorkspaceSlug();
	return <AgentActivityPage workspaceSlug={workspaceSlug ?? ""} />;
}
