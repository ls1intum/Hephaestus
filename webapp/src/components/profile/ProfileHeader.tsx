import { ClockIcon } from "@primer/octicons-react";
import { format } from "date-fns";
import type { RepositoryInfo, UserInfo } from "@/api/types.gen";
import { LeagueIcon } from "@/components/leaderboard/LeagueIcon";
import { getLeagueTier } from "@/components/leaderboard/utils.ts";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import { XpSystem } from "@/lib/xp";
import { cn } from "@/lib/utils.ts";
import { XpProgress } from "./XpProgress";

export interface ProfileHeaderProps {
  user?: UserInfo;
  firstContribution?: Date;
  contributedRepositories?: RepositoryInfo[];
  leaguePoints?: number;
  isLoading: boolean;
}

export function ProfileHeader({
  user,
  firstContribution,
  leaguePoints = 0,
  isLoading,
}: ProfileHeaderProps) {
  // Calculate level stats from total XP (leaguePoints)
  // TODO: This should use the totalXp and NOT the league points
  const { level, currentLevelXP, xpNeeded } = XpSystem.getLevelProgress(
    leaguePoints || 0,
  );

  // Format the first contribution date if available
  const formattedFirstContribution = firstContribution
    ? format(firstContribution, "MMMM do, yyyy")
    : undefined;
  const rawTier = getLeagueTier(leaguePoints);
  const leagueTier = !rawTier || rawTier === "none" ? "bronze" : rawTier;

  return (
    <div className="flex items-center justify-between mx-8">
      <div className="flex gap-6 items-center w-full">
        {/* Game-style Avatar Frame with Level Bubble */}
        <div className="relative shrink-0">
          {isLoading ? (
            <Avatar className="w-24 h-24">
              <Skeleton className="h-full w-full rounded-full" />
            </Avatar>
          ) : (
            <Avatar className="w-24 h-24 border-2 border-background">
              <AvatarImage
                src={user?.avatarUrl}
                alt={`${user?.login}'s avatar`}
              />
              <AvatarFallback>
                {user?.login?.slice(0, 2)?.toUpperCase()}
              </AvatarFallback>
            </Avatar>
          )}

          {/* Level Bubble (Bronze league style) */}
          {isLoading ? (
            <Skeleton className="absolute -bottom-1 -right-1 flex h-9 w-9 items-center justify-center rounded-full border-background" />
          ) : (
            <Tooltip>
              <TooltipTrigger asChild>
                <div
                  className={cn(
                    "absolute -bottom-1 -right-1 flex h-9 w-9 items-center justify-center rounded-full  border-2 border-background text-primary-foreground font-black text-sm z-10",
                    `bg-league-${leagueTier}`,
                  )}
                >
                  {level}
                </div>
              </TooltipTrigger>
              <TooltipContent side="bottom">
                <p>LEVEL</p>
              </TooltipContent>
            </Tooltip>
          )}
        </div>

        {/* User information HUD */}
        {isLoading ? (
          <div className="flex flex-col gap-2 w-full max-w-xl">
            <Skeleton className="h-8 w-48" />
            <Skeleton className="h-5 w-64" />
            <Skeleton className="h-5 w-80" />
            <Skeleton className="h-12 w-full max-w-sm mt-2" />
          </div>
        ) : user ? (
          <div className="flex flex-col gap-3 w-full max-w-xl">
            <div className="flex flex-col gap-1">
              {/* User name */}
              <h1 className="text-2xl md:text-3xl font-bold leading-6">
                {user.name}
              </h1>

              {/* GitHub profile link */}
              <a
                className="md:text-lg font-medium text-muted-foreground mb-1 hover:text-github-accent-foreground"
                href={user.htmlUrl}
                target="_blank"
                rel="noopener noreferrer"
              >
                github.com/{user.login}
              </a>

              {/* First contribution */}
              {formattedFirstContribution && (
                <div className="flex items-center gap-1 md:gap-2 text-muted-foreground font-medium text-sm md:text-base">
                  <ClockIcon size={16} className="overflow-visible" />
                  <span>Contributing since {formattedFirstContribution}</span>
                </div>
              )}
            </div>

            {/* Level Bar - Integrated under user info */}
            {/*TODO This should not take in the leaguePoints but the totalXP*/}
            <XpProgress
              className="max-w-sm"
              level={level}
              currentXP={currentLevelXP}
              xpNeeded={xpNeeded}
              totalXP={leaguePoints}
              showBadge={false}
            />
          </div>
        ) : null}
      </div>

      {/* League information */}
      <div className="flex flex-col justify-center items-center gap-2 shrink-0">
        {isLoading ? (
          <>
            <Skeleton className="size-28 rounded-full" />
            <Skeleton className="h-8 w-16" />
          </>
        ) : (
          <>
            <LeagueIcon leaguePoints={leaguePoints} size="max" />
            <span className="text-muted-foreground text-xl md:text-2xl font-bold leading-6">
              {leaguePoints}
            </span>
          </>
        )}
      </div>
    </div>
  );
}
