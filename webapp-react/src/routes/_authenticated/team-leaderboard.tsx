import {createFileRoute, retainSearchParams, useNavigate} from '@tanstack/react-router';
import { TeamLeaderboardPage } from '@/components/team-leaderboard/TeamLeaderboardPage';
import { z } from "zod";
import { endOfISOWeek, formatISO, startOfISOWeek } from "date-fns";
import { zodValidator } from "@tanstack/zod-adapter";
import { useAuth } from "@/integrations/auth";
import {useQuery} from "@tanstack/react-query";
import {getMetaDataOptions, getTeamLeaderboardOptions} from "@/api/@tanstack/react-query.gen.ts";

// Calculate default date range with ISO 8601 format including timezone
const today = new Date();
const startOfCurrentWeekDate = startOfISOWeek(today);
const endOfCurrentWeekDate = endOfISOWeek(today);
const startOfCurrentWeek = formatISO(startOfCurrentWeekDate);
const endOfCurrentWeek = formatISO(endOfCurrentWeekDate);

// Define search params schema for validation and types
const teamLeaderboardSearchSchema = z.object({
    after: z.string().optional().default(startOfCurrentWeek),
    before: z.string().optional().default(endOfCurrentWeek),
});

// Export route with search param validation
export const Route = createFileRoute('/_authenticated/team-leaderboard')({
  component: TeamLeaderboardContainer,
    validateSearch: zodValidator(teamLeaderboardSearchSchema),
    // Configure search middleware to retain params when navigating
    search: {
        middlewares: [retainSearchParams(["after", "before"])],
    },
})

function TeamLeaderboardContainer() {
    // Get the current user from auth context
    const { username } = useAuth();

    // Access properly validated search params with correct types
    const {after, before } = Route.useSearch();
    const navigate = useNavigate({ from: Route.fullPath });

    // Query for metadata (teams, schedule info)
    const metaQuery = useQuery({
        ...getMetaDataOptions({}),
    });

    const teamLeaderboardQuery = useQuery({
       ...getTeamLeaderboardOptions({
           query: {
               after: new Date(after || startOfCurrentWeek),
               before: new Date(before || endOfCurrentWeek),
           },
       }),
        enabled: Boolean(after && before && metaQuery.data)
    });

    // Handle team navigation
    const handleTeamClick = () => {
        navigate({ to: "/teams"}); // TODO: scroll team into view on /teams page based on clicked team
    };

    return (
        <TeamLeaderboardPage
            isLoading={teamLeaderboardQuery.isPending || metaQuery.isPending}
            teamLeaderboard={teamLeaderboardQuery.data || []}
            // currentTeam={}
            onTeamClick={handleTeamClick}
            teams={[]} // TODO: insert teams query here -> functionality NYI
            // TODO: implement handler for onTeamChange, onSortChange, onTimeframeChange if needed
            // onTeamChange={}
            // onSortChange={}
            // onTimeframeChange={}
            // TODO: implement these properties if needed ( i think they are currently not in use)
            // selectedTeam={}
            // selectedSort={}
            // initialAfterDate={}
            // initialBeforeDate={}
            // leaderboardSchedule={}
        />
    );
}
