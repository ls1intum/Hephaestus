import { TrophyIcon } from "@primer/octicons-react";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip";
import { cn } from "@/lib/utils";

type LeagueIconProps = {
  leaguePoints: number;
  size?: 'sm' | 'default' | 'lg' | 'max' | 'full';
  className?: string;
};

export const leagueVariants = {
  size: {
    default: 'size-8',
    sm: 'size-6',
    lg: 'size-12',
    max: 'size-28',
    full: 'size-full'
  },
  league: {
    none: 'text-gray-400',
    bronze: 'text-league-bronze',
    silver: 'text-league-silver',
    gold: 'text-league-gold',
    diamond: 'text-league-diamond',
    master: 'text-league-master'
  }
};

export function getLeagueFromPoints(points: number) {
  if (points >= 2000) return { name: "Master", minPoints: 2000, maxPoints: Infinity };
  if (points >= 1500) return { name: "Diamond", minPoints: 1500, maxPoints: 1999 };
  if (points >= 1000) return { name: "Gold", minPoints: 1000, maxPoints: 1499 };
  if (points >= 500) return { name: "Silver", minPoints: 500, maxPoints: 999 };
  return { name: "Bronze", minPoints: 0, maxPoints: 499 };
}

export function LeagueIcon({ leaguePoints, size = 'default', className }: LeagueIconProps) {
  const league = leaguePoints ? getLeagueFromPoints(leaguePoints).name.toLowerCase() : 'none';
  
  const leagueTooltip = () => {
    switch (league) {
      case 'bronze': return 'Bronze League';
      case 'silver': return 'Silver League';
      case 'gold': return 'Gold League';
      case 'diamond': return 'Diamond League';
      case 'master': return 'Master League';
      default: return 'No League';
    }
  };

  const computedClass = cn(
    leagueVariants.size[size], 
    leagueVariants.league[league as keyof typeof leagueVariants.league],
    className
  );

  const renderIcon = () => {
    // This is a simplified version - you would have individual SVG components for each league
    return <TrophyIcon className={computedClass} />;
  };

  return (
    <TooltipProvider>
      <Tooltip>
        <TooltipTrigger>
          {renderIcon()}
        </TooltipTrigger>
        <TooltipContent>
          <span className="flex items-center capitalize">
            {leagueTooltip()}
          </span>
        </TooltipContent>
      </Tooltip>
    </TooltipProvider>
  );
}