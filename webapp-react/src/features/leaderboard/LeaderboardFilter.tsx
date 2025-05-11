import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import type { LeaderboardFilterProps } from "./types";
import { TeamFilter } from "./filters/TeamFilter";
import { SortFilter } from "./filters/SortFilter";
import { TimeframeFilter } from "./filters/TimeframeFilter";
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
    formatted: string;
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
        <div className="flex flex-col gap-4">
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