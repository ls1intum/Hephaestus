import { useQuery } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { getAllTeamsOptions } from "@/api/@tanstack/react-query.gen";
import { TeamsPage } from "@/components/teams/TeamsPage";
import { useWorkspace } from "@/hooks/use-workspace";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/teams/")({
	component: TeamsContainer,
});

function TeamsContainer() {
	// Workspace is loaded by the parent layout route and provided via context
	const { workspaceSlug } = useWorkspace();
	const teamsQuery = useQuery({
		...getAllTeamsOptions({ path: { workspaceSlug } }),
	});

	return <TeamsPage teams={teamsQuery.data || []} isLoading={teamsQuery.isLoading} />;
}
