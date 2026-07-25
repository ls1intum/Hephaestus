import { createFileRoute } from "@tanstack/react-router";
import { AgentBindingsPage } from "@/components/admin/ai/AgentBindingsPage";
import { NoWorkspace } from "@/components/workspace/NoWorkspace";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";
import { workspaceAdminHead } from "@/lib/page-title";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/models")({
	head: workspaceAdminHead("AI models"),
	component: ModelsContainer,
});

function ModelsContainer() {
	const { workspaceSlug, isLoading } = useActiveWorkspaceSlug();

	if (!workspaceSlug && !isLoading) {
		return <NoWorkspace />;
	}

	return <AgentBindingsPage workspaceSlug={workspaceSlug ?? ""} />;
}
