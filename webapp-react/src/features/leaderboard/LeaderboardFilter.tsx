import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import type { LeaderboardFilterProps } from "./types";
import { TeamFilter } from "./filters/TeamFilter";
import { SortFilter } from "./filters/SortFilter";
import { TimeframeFilter } from "./filters/TimeframeFilter";

export function LeaderboardFilter({ 
  teams, 
  onTeamChange, 
  onSortChange, 
  onTimeframeChange, 
  selectedTeam,
  selectedSort,
}: LeaderboardFilterProps) {
  return (
    <Card>
      <CardHeader className="pb-3">
        <CardTitle className="text-lg">Filter</CardTitle>
      </CardHeader>
      <CardContent className="space-y-6 pt-0">
        <TeamFilter 
          teams={teams}
          onTeamChange={onTeamChange}
          selectedTeam={selectedTeam}
        />

        <Separator />
        
        <SortFilter 
          onSortChange={onSortChange}
          selectedSort={selectedSort}
        />

        <Separator />
        
        <TimeframeFilter 
          onTimeframeChange={onTimeframeChange}
          leaderboardSchedule={
            { day: 1, hour: 9, minute: 0, formatted: 'Mondays at 09:00' }
          }
        />
      </CardContent>
    </Card>
  );
}