import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import type { LeaderboardFilterProps } from "./types";
import { TeamFilter } from "./TeamFilter";
import { SortFilter } from "./SortFilter";
import { TimeframeFilter } from "./TimeframeFilter";
import { SlidersHorizontal } from "lucide-react";

export function LeaderboardFilter({ 
  teams, 
  onTeamChange, 
  onSortChange, 
  onTimeframeChange, 
  selectedTeam,
  selectedSort,
  initialAfterDate,
  initialBeforeDate,
  leaderboardSchedule
}: LeaderboardFilterProps & { 
  initialAfterDate?: string;
  initialBeforeDate?: string;
  leaderboardSchedule?: {
    day: number;
    hour: number;
    minute: number;
  };
}) {
  return (
    <Card>
      <CardHeader>
        <CardTitle><SlidersHorizontal className="inline mr-2 h-4 w-4" /> Leaderboard Options</CardTitle>
        <CardDescription>
          Customize the leaderboard view by filtering and sorting the data.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 xl:grid-cols-1">
          <TeamFilter 
            teams={teams}
            onTeamChange={onTeamChange}
            selectedTeam={selectedTeam}
          />
          <SortFilter 
            onSortChange={onSortChange}
            selectedSort={selectedSort}
          />
          <TimeframeFilter 
            onTimeframeChange={onTimeframeChange}
            initialAfterDate={initialAfterDate}
            initialBeforeDate={initialBeforeDate}
            leaderboardSchedule={leaderboardSchedule}
          />
        </div>
      </CardContent>
    </Card>
  );
}