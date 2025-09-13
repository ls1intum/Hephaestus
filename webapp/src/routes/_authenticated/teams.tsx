import { useQuery } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { getAllTeamsOptions } from "@/api/@tanstack/react-query.gen";
import { TeamsPage } from "@/components/teams/TeamsPage";

export const Route = createFileRoute("/_authenticated/teams")({
	component: TeamsContainer,
});

function TeamsContainer() {
	const teamsQuery = useQuery(getAllTeamsOptions({}));

	return (
		<TeamsPage teams={teamsQuery.data || []} isLoading={teamsQuery.isLoading} />
	);
}
