import { createFileRoute } from "@tanstack/react-router";
import { useQuery } from "@tanstack/react-query";
import { getTeamsOptions } from "@/api/@tanstack/react-query.gen";
import { TeamsPage } from "@/features/teams/TeamsPage";

export const Route = createFileRoute("/_authenticated/teams")({
  component: TeamsContainer,
});

function TeamsContainer() {
  const teamsQuery = useQuery(getTeamsOptions({}));
  
  return (
    <TeamsPage
      teams={teamsQuery.data || []}
      isLoading={teamsQuery.isLoading}
    />
  );
}