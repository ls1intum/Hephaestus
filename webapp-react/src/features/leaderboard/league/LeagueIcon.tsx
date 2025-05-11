import type { LeagueIconProps } from "../types";
import { 
  LeagueBronzeIcon, 
  LeagueNoneIcon, 
  LeagueSilverIcon, 
  LeagueGoldIcon, 
  LeagueDiamondIcon, 
  LeagueMasterIcon 
} from "@/features/leaderboard/league/LeagueIcons";

export function LeagueIcon({ leaguePoints, size = "default", showPoints = false }: LeagueIconProps) {
  // Default to bronze tier if no league points provided
  const points = leaguePoints ?? 0;
  
  let tier: "bronze" | "silver" | "gold" | "diamond" | "master" | "none";
  let label: string;
  
  // Determine tier based on league points
  if (points < 500) {
    tier = "bronze";
    label = "Bronze";
  } else if (points < 1000) {
    tier = "silver";
    label = "Silver";
  } else if (points < 1500) {
    tier = "gold";
    label = "Gold";
  } else if (points < 2000) {
    tier = "diamond";
    label = "Diamond";
  } else {
    tier = "master";
    label = "Master";
  }

  // If no league points provided, use the none tier
  if (leaguePoints === undefined) {
    tier = "none";
    label = "Not Ranked";
  }

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
    <div className="flex flex-col items-center justify-center">
      <IconComponent 
        size={size}
        aria-label={`${label} tier`}
      />
      {showPoints && (
        <span className="text-xs font-semibold text-muted-foreground">{points}</span>
      )}
    </div>
  );
}