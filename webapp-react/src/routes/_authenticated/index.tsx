import { createFileRoute, retainSearchParams } from '@tanstack/react-router'
import { useQuery } from "@tanstack/react-query";
import { useNavigate } from "@tanstack/react-router";
import { z } from 'zod';
import { zodValidator, fallback } from '@tanstack/zod-adapter';
import dayjs from "dayjs";
import isoWeek from "dayjs/plugin/isoWeek";
import { useAuth } from "@/lib/auth/AuthContext";
import { getLeaderboardOptions, getMetaDataOptions, getUserProfileOptions } from "@/api/@tanstack/react-query.gen";
import { LeaderboardPage } from '@/features/leaderboard/LeaderboardPage';
import type { LeaderboardSortType } from '@/features/leaderboard/types';

// Extend dayjs with the isoWeek plugin
dayjs.extend(isoWeek);

// Calculate default date range
const today = dayjs();
const startOfLastWeek = today.subtract(1, 'week').startOf('isoWeek').format('YYYY-MM-DD');
const endOfLastWeek = today.subtract(1, 'week').endOf('isoWeek').format('YYYY-MM-DD');

// Define search params schema for validation and types
const leaderboardSearchSchema = z.object({
  team: fallback(z.string(), 'all'),
  sort: fallback(z.enum(['SCORE', 'LEAGUE_POINTS']), 'SCORE'),
  after: fallback(z.string(), startOfLastWeek),
  before: fallback(z.string(), endOfLastWeek),
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
        team: team !== 'all' ? team : undefined,
        sort: sort === "SCORE" ? sort : "SCORE" // Ensure API compatibility
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
  const leaderboardEnd = dayjs(before).format("dddd, MMMM D, YYYY");

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

  // Handle timeframe changes
  const handleTimeframeChange = (after: string, before: string) => {
    navigate({
      search: (prev) => ({
        ...prev,
        after,
        before
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
      leaderboardEnd={leaderboardEnd}
      onTeamChange={handleTeamChange}
      onSortChange={handleSortChange}
      onTimeframeChange={handleTimeframeChange}
    />
  );
}