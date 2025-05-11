import { cn } from "@/lib/utils";
import type { LeagueIconProps } from "./types";
import { Award } from "lucide-react";

export function LeagueIcon({ leaguePoints, size = "md", showPoints = false }: LeagueIconProps) {
  // Default to bronze tier if no league points provided
  const points = leaguePoints ?? 0;
  
  let tier: "bronze" | "silver" | "gold" | "diamond" | "master";
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

  // Map tier to color class
  const tierColorMap = {
    bronze: "text-[hsl(var(--league-bronze))]",
    silver: "text-[hsl(var(--league-silver))]",
    gold: "text-[hsl(var(--league-gold))]",
    diamond: "text-[hsl(var(--league-diamond))]",
    master: "text-[hsl(var(--league-master))]",
  };

  // Map size to icon dimensions
  const sizeMap = {
    sm: "h-5 w-5",
    md: "h-6 w-6",
    lg: "h-8 w-8"
  };
  
  return (
    <div className="flex flex-col items-center justify-center">
      <Award 
        className={cn(
          sizeMap[size],
          tierColorMap[tier], 
          "fill-current"
        )} 
        aria-label={`${label} tier`}
      />
      {showPoints && (
        <span className="text-xs font-semibold text-muted-foreground">{points}</span>
      )}
    </div>
  );
}