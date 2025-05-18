import type { LeaderboardEntry, UserInfo } from "@/api/types.gen";
import { LeaderboardFilter } from "./LeaderboardFilter";
import { LeaderboardLegend } from "./LeaderboardLegend";
import { LeaderboardOverview } from "./LeaderboardOverview";
import { LeaderboardTable } from "./LeaderboardTable";
import type { LeaderboardSortType } from "./SortFilter";

interface LeaderboardPageProps {
  leaderboard?: LeaderboardEntry[];
  isLoading: boolean;
  currentUser?: UserInfo;
  currentUserEntry?: LeaderboardEntry;
  leaguePoints?: number;
  leaguePointsChange?: number;
  teams: string[];
  onTeamChange?: (team: string) => void;
  onSortChange?: (sort: LeaderboardSortType) => void;
  onTimeframeChange?: (afterDate: string, beforeDate: string, timeframe?: string) => void;
  selectedTeam?: string;
  selectedSort?: LeaderboardSortType;
  initialAfterDate?: string;
  initialBeforeDate?: string;
  leaderboardEnd?: string;
  leaderboardSchedule?: {
    day: number;
    hour: number;
    minute: number;
  };
}

export function LeaderboardPage({
  leaderboard,
  isLoading,
  currentUser,
  currentUserEntry,
  leaguePoints = 0,
  leaguePointsChange = 0,
  teams,
  onTeamChange,
  onSortChange,
  onTimeframeChange,
  selectedTeam,
  selectedSort,
  initialAfterDate,
  initialBeforeDate,
  leaderboardEnd,
  leaderboardSchedule
}: LeaderboardPageProps) {
  // Add formatted property to the leaderboardSchedule object if it exists
  const formattedSchedule = leaderboardSchedule 
    ? {
        ...leaderboardSchedule,
        formatted: `${String(leaderboardSchedule.hour).padStart(2, '0')}:${String(leaderboardSchedule.minute).padStart(2, '0')} on day ${leaderboardSchedule.day}`
      }
    : undefined;
    
  return (
    <div className="flex flex-col items-center">
      <div className="w-full max-w-[1400px]">
        <div className="grid grid-cols-1 xl:grid-cols-4 gap-y-4 xl:gap-8">
          <div className="space-y-4 col-span-1">
            <div className="flex flex-col mb-4">
              <h1 className="text-3xl font-bold">Leaderboard</h1>
              {currentUser && (
                <h2 className="text-xl text-muted-foreground">
                  Hi {currentUser.name} 👋
                </h2>
              )}
            </div>
            <div className="xl:sticky xl:top-4 xl:self-start xl:max-h-[calc(100vh-2rem)] xl:overflow-auto">
              <LeaderboardFilter
                teams={teams}
                onTeamChange={onTeamChange}
                onSortChange={onSortChange}
                onTimeframeChange={onTimeframeChange}
                selectedTeam={selectedTeam}
                selectedSort={selectedSort}
                initialAfterDate={initialAfterDate}
                initialBeforeDate={initialBeforeDate}
                leaderboardSchedule={formattedSchedule}
              />
            </div>
          </div>
          
          <div className="col-span-2 space-y-4">
            {currentUserEntry && leaguePoints !== undefined && (
              <LeaderboardOverview
                leaderboardEntry={currentUserEntry}
                leaguePoints={leaguePoints}
                leaderboardEnd={leaderboardEnd}
                leaguePointsChange={leaguePointsChange}
              />
            )}
            
            <div className="border rounded-md border-input overflow-auto">
              <LeaderboardTable
                leaderboard={leaderboard}
                isLoading={isLoading}
                currentUser={currentUser}
              />
            </div>
          </div>
          
          <div className="col-span-1 xl:sticky xl:top-4 xl:self-start xl:max-h-[calc(100vh-2rem)] xl:overflow-auto">
            <LeaderboardLegend />
          </div>
        </div>
      </div>
    </div>
  );
}