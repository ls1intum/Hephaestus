import { useState } from "react";
import { Card, CardContent } from "@/components/ui/card";
import type { LeaderboardOverviewProps } from "./types";
import { LeagueIcon } from "./league/LeagueIcon";
import { CalendarClock, Info, Star, TrendingUp, TrendingDown, MoveRight } from "lucide-react";
import { Button } from "@/components/ui/button";
import { LeagueInfoDialog } from "./LeagueInfoDialog";
import { Progress } from "@/components/ui/progress";
import { getLeagueFromPoints } from "./league/utils";
import { differenceInHours, isPast } from "date-fns";

export function LeaderboardOverview({ 
  leaderboardEntry, 
  leaguePoints,
  leaderboardEnd,
  leaguePointsChange = 0
}: LeaderboardOverviewProps) {
  const [leagueInfoOpen, setLeagueInfoOpen] = useState(false);

  // Get current league from points
  const currentLeague = getLeagueFromPoints(leaguePoints);

  // Calculate progress value based on league min and max points
  const progressValue = currentLeague ? 
    ((leaguePoints - currentLeague.minPoints) * 100) / (currentLeague.maxPoints - currentLeague.minPoints) : 0;
    
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
            <div className="flex items-center gap-2 2xl:gap-4">
              <LeagueIcon leaguePoints={leaguePoints} size="lg" />
              {currentLeague && (
                <div className="flex flex-col min-w-[140px]">
                  <div className="flex items-center gap-2">
                    <div>
                      <span className="text-sm font-semibold text-muted-foreground">{currentLeague.name}</span>
                      {/* Points display */}
                      <div className="w-full flex items-center justify-center gap-1 text-sm text-muted-foreground">
                        <span className="whitespace-nowrap">
                          {currentLeague.maxPoints === Infinity ? 
                            `${leaguePoints}` :
                            `${leaguePoints} / ${currentLeague.maxPoints}`
                          }
                        </span>
                        <Star className="h-4 w-4 flex-shrink-0" />
                      </div>
                    </div>
                    <Button 
                      variant="ghost" 
                      size="sm" 
                      className="h-6 w-6 p-0" 
                      onClick={() => setLeagueInfoOpen(true)}
                    >
                      <Info className="h-4 w-4 text-muted-foreground" />
                    </Button>
                  </div>
                  {/* Progress bar container */}
                  <div className="flex items-center gap-2 mt-1">
                    <Progress 
                      value={progressValue} 
                      className="bg-secondary"
                      indicatorClassName={`bg-league-${currentLeague.name.toLowerCase()}`}
                    />
                    <LeagueIcon 
                      leaguePoints={currentLeague.maxPoints + 1} 
                      size="sm"
                      className="flex-shrink-0" 
                    />
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>
      </CardContent>
      
      <LeagueInfoDialog open={leagueInfoOpen} onOpenChange={setLeagueInfoOpen} />
    </Card>
  );
}