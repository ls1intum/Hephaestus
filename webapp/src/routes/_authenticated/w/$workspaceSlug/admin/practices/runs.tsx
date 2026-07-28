import { createFileRoute } from "@tanstack/react-router";
import { AgentActivityPage } from "@/components/admin/ai/AgentActivityPage";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";
import { workspaceAdminHead } from "@/lib/page-title";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/practices/runs")({
	head: workspaceAdminHead("Practice runs"),
	component: RunsContainer,
});

function RunsContainer() {
	const { workspaceSlug } = useActiveWorkspaceSlug();
	return <AgentActivityPage workspaceSlug={workspaceSlug ?? ""} />;
}
