import { useQuery } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { getAllTeamsOptions } from "@/api/@tanstack/react-query.gen";
import { TeamsPage } from "@/components/teams/TeamsPage";
import { NoWorkspace } from "@/components/workspace/NoWorkspace";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/teams/")({
	component: TeamsContainer,
	staticData: {
		workspaceSwitch: { target: "workspace.teams" },
	},
});

function TeamsContainer() {
	const { workspaceSlug } = Route.useParams();
	const teamsQuery = useQuery({
		...getAllTeamsOptions({ path: { workspaceSlug: workspaceSlug ?? "" } }),
		enabled: Boolean(workspaceSlug),
	});

	if (!workspaceSlug) {
		return <NoWorkspace />;
	}

	return (
		<TeamsPage teams={teamsQuery.data || []} isLoading={teamsQuery.isLoading || !workspaceSlug} />
	);
}
