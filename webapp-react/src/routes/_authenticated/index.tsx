import { createFileRoute, retainSearchParams } from '@tanstack/react-router'
import { useQuery } from "@tanstack/react-query";
import { useNavigate } from "@tanstack/react-router";
import { z } from 'zod';
import { zodValidator } from '@tanstack/zod-adapter';
import { 
  format, 
  subWeeks, 
  startOfISOWeek, 
  endOfISOWeek, 
  formatISO 
} from "date-fns";
import { useAuth } from "@/lib/auth/AuthContext";
import { getLeaderboardOptions, getMetaDataOptions, getUserProfileOptions } from "@/api/@tanstack/react-query.gen";
import { LeaderboardPage } from '@/features/leaderboard/LeaderboardPage';
import type { LeaderboardSortType } from '@/features/leaderboard/types';

// Calculate default date range with ISO 8601 format including timezone
const today = new Date();
const startOfLastWeekDate = startOfISOWeek(subWeeks(today, 1));
const endOfLastWeekDate = endOfISOWeek(subWeeks(today, 1));
const startOfLastWeek = formatISO(startOfLastWeekDate);
const endOfLastWeek = formatISO(endOfLastWeekDate);

// Define search params schema for validation and types
const leaderboardSearchSchema = z.object({
  team: z.string().default('all'),
  sort: z.enum(['SCORE', 'LEAGUE_POINTS']).default('SCORE'),
  after: z.string().optional().default(startOfLastWeek),
  before: z.string().optional().default(endOfLastWeek),
});

// Export route with search param validation
export const Route = createFileRoute('/_authenticated/')({
  component: LeaderboardContainer,
  validateSearch: zodValidator(leaderboardSearchSchema),
  // Configure search middleware to retain params when navigating
  search: {
    middlewares: [retainSearchParams(['team', 'sort', 'after', 'before'])],
  }
})

function LeaderboardContainer() {
  // Get the current user from auth context
  const { username } = useAuth();
  
  // Access properly validated search params with correct types
  const { team, sort, after, before } = Route.useSearch();
  const navigate = useNavigate({ from: Route.fullPath });
  
  // Query for metadata (teams, schedule info)
  const metaQuery = useQuery({
    ...getMetaDataOptions({}),
  });
  
  // Query for leaderboard data based on filters
  const leaderboardQuery = useQuery({
    ...getLeaderboardOptions({
      query: {
        after,
        before,
        team,
        sort,
      }
    }),
    enabled: Boolean(after && before && metaQuery.data),
  });
  
  // Query for user profile data
  const userProfileQuery = useQuery({
    ...getUserProfileOptions({ 
      path: { login: username || '' } 
    }),
    enabled: Boolean(username),
  });
  
  // Find the current user's entry in the leaderboard
  const currentUserEntry = username 
    ? leaderboardQuery.data?.find(entry => 
        entry.user.login.toLowerCase() === username.toLowerCase()
      )
    : undefined;
  
  // Format leaderboard end date (using the beforeParam as the end date)
  const leaderboardEnd = format(new Date(before), "EEEE, MMMM d, yyyy");

  // Use a fixed leaderboard schedule since it seems the metadata doesn't provide this
  // Mondays at 9:00 AM is the default schedule
  const leaderboardSchedule = {
    day: 1, // Monday
    hour: 9,
    minute: 0,
  };

  // Handle team filter changes
  const handleTeamChange = (team: string) => {
    navigate({
      search: (prev) => ({
        ...prev, 
        team
      }),
    });
  };

  // Handle sort changes with the correct type
  const handleSortChange = (sort: LeaderboardSortType) => {
    navigate({
      search: (prev) => ({
        ...prev,
        sort
      }),
    });
  };

  // Handle timeframe changes - note we're not passing timeframe in URL anymore
  const handleTimeframeChange = (afterDate: string, beforeDate: string) => {
    navigate({
      search: (prev) => ({
        ...prev,
        after: afterDate,
        before: beforeDate
      }),
    });
  };

  return (
    <LeaderboardPage 
      leaderboard={leaderboardQuery.data || []}
      isLoading={leaderboardQuery.isPending || metaQuery.isPending}
      currentUser={userProfileQuery.data?.userInfo}
      currentUserEntry={currentUserEntry}
      leaguePoints={userProfileQuery.data?.userInfo?.leaguePoints}
      teams={metaQuery.data?.teams.map(team => team.name) || []}
      selectedTeam={team}
      selectedSort={sort}
      initialAfterDate={after}
      initialBeforeDate={before}
      leaderboardEnd={leaderboardEnd}
      leaderboardSchedule={leaderboardSchedule}
      onTeamChange={handleTeamChange}
      onSortChange={handleSortChange}
      onTimeframeChange={handleTimeframeChange}
    />
  );
}