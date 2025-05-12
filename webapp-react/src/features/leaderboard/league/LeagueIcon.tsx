import type { LeagueIconProps } from "../types";
import { 
  LeagueBronzeIcon, 
  LeagueNoneIcon, 
  LeagueSilverIcon, 
  LeagueGoldIcon, 
  LeagueDiamondIcon, 
  LeagueMasterIcon 
} from "@/features/leaderboard/league/LeagueIcons";
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import { cn } from "@/lib/utils";
import { getLeagueTier, getLeagueLabel } from "./utils";

export function LeagueIcon({ 
  leaguePoints, 
  size = "default", 
  showPoints = false,
  className 
}: LeagueIconProps) {
  // Get tier and label based on league points
  const tier = getLeagueTier(leaguePoints);
  const label = getLeagueLabel(tier);
  
  // Get the appropriate icon component based on the tier
  const IconComponent = {
    none: LeagueNoneIcon,
    bronze: LeagueBronzeIcon,
    silver: LeagueSilverIcon,
    gold: LeagueGoldIcon,
    diamond: LeagueDiamondIcon,
    master: LeagueMasterIcon,
  }[tier];
  
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <div className={cn("flex flex-col items-center justify-center", className)}>
          <IconComponent 
            size={size}
            aria-label={`${label} tier`}
          />
          {showPoints && (
            <span className="text-xs font-semibold text-muted-foreground">{leaguePoints}</span>
          )}
        </div>
      </TooltipTrigger>
      <TooltipContent>
        <p>{label} League</p>
      </TooltipContent>
    </Tooltip>
  );
}