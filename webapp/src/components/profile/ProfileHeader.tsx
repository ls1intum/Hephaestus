import { format } from "date-fns";
import type { ProfileXpRecord, RepositoryInfo, UserInfo } from "@/api/types.gen";
import { LeagueIcon } from "@/components/leaderboard/LeagueIcon";
import { getLeagueTier } from "@/components/leaderboard/utils.ts";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Skeleton } from "@/components/ui/skeleton";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { cn } from "@/lib/utils.ts";
import { XpProgress } from "./XpProgress";

export interface ProfileHeaderProps {
	user?: UserInfo;
	firstContribution?: Date;
	contributedRepositories?: RepositoryInfo[];
	leaguePoints?: number;
	userXpRecord?: ProfileXpRecord;
	isLoading: boolean;
}

export function ProfileHeader({
	user,
	firstContribution,
	leaguePoints = 0,
	userXpRecord = { currentLevel: 1, currentLevelXP: 0, totalXP: 0, xpNeeded: 150 },
	isLoading,
}: ProfileHeaderProps) {
	// Unpack XP data
	const { currentLevel: level, currentLevelXP: currentXp, xpNeeded, totalXP } = userXpRecord;

	// Format the first contribution date if available
	const formattedFirstContribution = firstContribution
		? format(firstContribution, "MMMM yyyy")
		: undefined;

	const rawTier = getLeagueTier(leaguePoints);
	const leagueTier = rawTier === "none" ? "bronze" : rawTier;

	return (
		<div className="flex items-start justify-between gap-6 mx-8">
			{/* Left section: Avatar + User Info + XP */}
			<div className="flex flex-col gap-4 w-full max-w-xl">
				{/* Row 1: Avatar + Name + GitHub Link */}
				<div className="flex items-center gap-4">
					{/* Avatar with Level Badge */}
					<div className="relative shrink-0">
						{isLoading ? (
							<Avatar className="size-16">
								<Skeleton className="h-full w-full rounded-full" />
							</Avatar>
						) : (
							<Avatar className="size-16 border-2 border-background shadow-sm">
								<AvatarImage src={user?.avatarUrl} alt={`${user?.login}'s avatar`} />
								<AvatarFallback>{user?.login?.slice(0, 2)?.toUpperCase()}</AvatarFallback>
							</Avatar>
						)}

						{/* Level Badge */}
						{isLoading ? (
							<Skeleton className="absolute -bottom-1 -right-1 size-7 rounded-full" />
						) : (
							<Tooltip>
								<TooltipTrigger
									render={
										<div
											className={cn(
												"absolute -bottom-1 -right-1 flex size-7 items-center justify-center rounded-full border-2 border-background text-primary-foreground font-bold text-xs",
												`bg-league-${leagueTier}`,
											)}
										>
											{level}
										</div>
									}
								/>
								<TooltipContent side="bottom">
									<p>Level {level}</p>
								</TooltipContent>
							</Tooltip>
						)}
					</div>

					{/* Name + GitHub Link */}
					{isLoading ? (
						<div className="flex flex-col gap-1.5">
							<Skeleton className="h-7 w-40" />
							<Skeleton className="h-5 w-48" />
						</div>
					) : user ? (
						<div className="flex flex-col gap-0.5">
							<h1 className="text-xl md:text-2xl font-bold leading-tight">{user.name}</h1>
							<a
								className="text-sm md:text-base text-muted-foreground hover:text-primary transition-colors"
								href={user.htmlUrl}
								target="_blank"
								rel="noopener noreferrer"
							>
								github.com/{user.login}
							</a>
						</div>
					) : null}
				</div>

				{/* Row 2: XP Progress Bar with Contributing Since */}
				{isLoading ? (
					<div className="flex flex-col gap-2">
						<Skeleton className="h-4 w-48" />
						<Skeleton className="h-2.5 w-full max-w-sm" />
						<Skeleton className="h-4 w-40" />
					</div>
				) : (
					<XpProgress
						className="max-w-sm"
						currentXP={currentXp}
						xpNeeded={xpNeeded}
						nextLevel={level + 1}
						totalXP={totalXP}
						contributingSince={formattedFirstContribution}
					/>
				)}
			</div>

			{/* Right section: League Icon + Points */}
			<div className="flex flex-col items-center gap-1 shrink-0">
				{isLoading ? (
					<>
						<Skeleton className="size-16 rounded-full" />
						<Skeleton className="h-5 w-12" />
					</>
				) : (
					<>
						<LeagueIcon leaguePoints={leaguePoints} size="lg" />
						<span className="text-muted-foreground text-base font-semibold">{leaguePoints}</span>
					</>
				)}
			</div>
		</div>
	);
}
