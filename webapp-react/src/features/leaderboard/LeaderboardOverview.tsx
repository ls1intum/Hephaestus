import { Card, CardContent } from "@/components/ui/card";
import type { LeaderboardOverviewProps } from "./types";
import { LeagueIcon } from "./league/LeagueIcon";
import { Award, CalendarClock, Trophy } from "lucide-react";

export function LeaderboardOverview({ 
  leaderboardEntry, 
  leaguePoints,
  leaderboardEnd
}: LeaderboardOverviewProps) {
  // Calculate percentage to next tier
  const getCurrentTierMax = () => {
    if (leaguePoints < 500) return 500;
    if (leaguePoints < 1000) return 1000;
    if (leaguePoints < 1500) return 1500;
    if (leaguePoints < 2000) return 2000;
    return 2000; // No higher tier
  };
  
  const getNextTierName = () => {
    if (leaguePoints < 500) return "Silver";
    if (leaguePoints < 1000) return "Gold";
    if (leaguePoints < 1500) return "Diamond";
    if (leaguePoints < 2000) return "Master";
    return ""; // Already at highest tier
  };
  
  const getCurrentTierMin = () => {
    if (leaguePoints < 500) return 0;
    if (leaguePoints < 1000) return 500;
    if (leaguePoints < 1500) return 1000;
    if (leaguePoints < 2000) return 1500;
    return 2000;
  };
  
  const getTierProgress = () => {
    const max = getCurrentTierMax();
    const min = getCurrentTierMin();
    const range = max - min;
    const progress = ((leaguePoints - min) / range) * 100;
    return Math.min(Math.max(progress, 0), 100);
  };
  
  const tierProgress = getTierProgress();
  const nextTier = getNextTierName();

  return (
    <Card>
      <CardContent className="pt-6">
        <div className="flex flex-col sm:flex-row justify-between items-center gap-4">
          <div className="flex items-center gap-4">
            <LeagueIcon leaguePoints={leaguePoints} size="lg" />
            <div>
              <h2 className="text-xl font-bold">
                {leaderboardEntry.user.name}
              </h2>
              <div className="flex items-center gap-1">
                <Trophy className="h-4 w-4 text-primary" />
                <span className="text-md font-semibold">
                  Rank: {leaderboardEntry.rank}
                </span>
                <span className="mx-2">•</span>
                <Award className="h-4 w-4 text-primary" />
                <span className="text-md font-semibold">
                  Score: {leaderboardEntry.score}
                </span>
              </div>
            </div>
          </div>

          {leaderboardEnd && (
            <div className="flex items-center gap-1 text-sm text-muted-foreground">
              <CalendarClock className="h-4 w-4" />
              <span>Leaderboard ends: {leaderboardEnd}</span>
            </div>
          )}
        </div>

        {nextTier && (
          <div className="mt-4 space-y-2">
            <div className="flex items-center justify-between text-sm">
              <span>Progress to {nextTier}</span>
              <span>{leaguePoints}/{getCurrentTierMax()} points</span>
            </div>
            <div className="h-2 w-full bg-secondary rounded-full overflow-hidden">
              <div 
                className="h-full bg-primary rounded-full" 
                style={{ width: `${tierProgress}%` }}
              />
            </div>
          </div>
        )}
        
        <div className="mt-4 grid grid-cols-2 md:grid-cols-4 gap-3">
          <div className="p-2 border rounded-md text-center">
            <div className="text-sm font-medium text-muted-foreground">Reviewed PRs</div>
            <div className="text-xl font-bold">{leaderboardEntry.numberOfReviewedPRs}</div>
          </div>
          <div className="p-2 border rounded-md text-center">
            <div className="text-sm font-medium text-muted-foreground">Approvals</div>
            <div className="text-xl font-bold text-success">{leaderboardEntry.numberOfApprovals}</div>
          </div>
          <div className="p-2 border rounded-md text-center">
            <div className="text-sm font-medium text-muted-foreground">Changes Requested</div>
            <div className="text-xl font-bold text-destructive">{leaderboardEntry.numberOfChangeRequests}</div>
          </div>
          <div className="p-2 border rounded-md text-center">
            <div className="text-sm font-medium text-muted-foreground">Comments</div>
            <div className="text-xl font-bold">{leaderboardEntry.numberOfComments + leaderboardEntry.numberOfCodeComments}</div>
          </div>
        </div>
      </CardContent>
    </Card>
  );
}