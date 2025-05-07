import React from "react";
import { StarIcon } from "@primer/octicons-react";
import { Progress } from "@/components/ui/progress";
import { LeagueIcon, getLeagueFromPoints } from "./LeagueIcon";
import { LeagueInfoModal } from "./LeagueInfoModal";
import { cn } from "@/lib/utils";

type LeagueEloCardProps = {
  leaguePoints: number;
  className?: string;
};

export function LeagueEloCard({ leaguePoints, className }: LeagueEloCardProps) {
  const currentLeague = getLeagueFromPoints(leaguePoints);
  
  // Calculate progress to next league
  const progressValue = 
    ((leaguePoints - currentLeague.minPoints) * 100) / 
    (currentLeague.maxPoints - currentLeague.minPoints);
  
  return (
    <div className={cn("flex items-center gap-2 2xl:gap-4", className)}>
      <LeagueIcon leaguePoints={leaguePoints} size="lg" />
      {currentLeague && (
        <div className="flex flex-col min-w-[140px]">
          <div className="flex items-center gap-2">
            <div>
              <span className="text-sm font-semibold text-github-muted-foreground">{currentLeague.name}</span>
              {/* Points display */}
              <div className="w-full flex items-center justify-center gap-1 text-sm text-github-muted-foreground">
                <span className="whitespace-nowrap">
                  {currentLeague.maxPoints === Infinity ? (
                    leaguePoints
                  ) : (
                    `${leaguePoints} / ${currentLeague.maxPoints}`
                  )}
                </span>
                <StarIcon className="text-base flex-shrink-0" />
              </div>
            </div>
            <div className="flex-shrink-0">
              <LeagueInfoModal />
            </div>
          </div>
          {/* Progress bar container */}
          <div className="flex items-center gap-2 mt-1">
            <Progress 
              value={progressValue} 
              className="w-full bg-secondary h-2 inline-flex overflow-hidden relative rounded-md" 
              // This would ideally come from your theme but we're using inline styles for now
              style={{
                "--progress-background": `hsl(var(--league-${currentLeague.name.toLowerCase()}))`
              } as React.CSSProperties}
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
  );
}