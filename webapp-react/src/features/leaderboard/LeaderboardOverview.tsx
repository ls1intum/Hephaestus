import { useState } from "react";
import { Card, CardContent } from "@/components/ui/card";
import type { LeaderboardOverviewProps } from "./types";
import { CalendarClock, TrendingUp, TrendingDown, MoveRight } from "lucide-react";
import { Button } from "@/components/ui/button";
import { LeagueInfoDialog } from "./LeagueInfoDialog";
import { differenceInHours, isPast } from "date-fns";
import { LeagueProgressCard } from "./league/LeagueProgressCard";

export function LeaderboardOverview({ 
  leaderboardEntry, 
  leaguePoints,
  leaderboardEnd,
  leaguePointsChange = 0
}: LeaderboardOverviewProps) {
  const [leagueInfoOpen, setLeagueInfoOpen] = useState(false);
    
  // Calculate relative time for leaderboard end
  const getRelativeEndTime = () => {
    if (!leaderboardEnd) return "N/A";
    
    const endDate = new Date(leaderboardEnd);
    
    if (isPast(endDate)) {
      return "Ended";
    }
    
    const diffHours = differenceInHours(endDate, new Date());
    
    if (diffHours > 24) {
      const days = Math.floor(diffHours / 24);
      const hours = diffHours % 24;
      return `${days}d ${hours}h`;
    }
    
    return `${diffHours}h`;
  };
    
  // Scroll to the user's rank in the leaderboard table
  const scrollToRank = (rank: number) => {
    const element = document.getElementById(`rank-${rank}`);
    if (element) {
      element.scrollIntoView({ behavior: "smooth" });
    }
  };

  return (
    <Card>
      <CardContent>
        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 items-center justify-between gap-4">
          <Button 
            variant="ghost" 
            className="h-full sm:col-span-2 md:col-span-1 flex flex-col items-center"
            onClick={() => scrollToRank(leaderboardEntry.rank)}
          >
            <div className="flex flex-col items-center gap-2">
              <div className="flex items-end gap-2">
                <span>
                  <span className="text-3xl font-light text-muted-foreground">#</span>
                  <span className="text-4xl text-primary font-light">{leaderboardEntry.rank}</span>
                </span>
                <div className="h-10 w-10 rounded-full bg-muted flex items-center justify-center overflow-hidden">
                  {leaderboardEntry.user.avatarUrl ? (
                    <img src={leaderboardEntry.user.avatarUrl} alt={`${leaderboardEntry.user.name}'s avatar`} className="h-full w-full object-cover" />
                  ) : (
                    <span className="text-sm font-medium">
                      {leaderboardEntry.user.name.slice(0, 2).toUpperCase()}
                    </span>
                  )}
                </div>
              </div>
              <span className="text-muted-foreground">{leaderboardEntry.user.name}</span>
            </div>
          </Button>

          <div className="flex flex-col items-center gap-1 text-center">
            <div className="flex items-center gap-1">
              <CalendarClock className="h-4 w-4 text-muted-foreground" />
              <span className="text-sm text-muted-foreground">Leaderboard ends in:</span>
            </div>
            <span className="text-xl font-medium">{getRelativeEndTime()}</span>
            <div className="flex flex-wrap items-center justify-center gap-1 mt-1 text-sm">
              <span className="text-muted-foreground">League points change:</span>
              <div className="flex items-center gap-1">
                <span className="font-medium">{leaguePointsChange}</span>
                {leaguePointsChange > 0 ? (
                  <TrendingUp className="h-4 w-4" />
                ) : leaguePointsChange < 0 ? (
                  <TrendingDown className="h-4 w-4" />
                ) : (
                  <MoveRight className="h-4 w-4" />
                )}
              </div>
            </div>
          </div>

          <div className="flex flex-col items-center gap-1 text-center">
            <LeagueProgressCard 
              leaguePoints={leaguePoints}
              onInfoClick={() => setLeagueInfoOpen(true)}
            />
          </div>
        </div>
      </CardContent>
      
      <LeagueInfoDialog open={leagueInfoOpen} onOpenChange={setLeagueInfoOpen} />
    </Card>
  );
}