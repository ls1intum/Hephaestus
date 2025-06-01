import { Button } from "@/components/ui/button";
import { Progress } from "@/components/ui/progress";
import { Info, Star } from "lucide-react";
import { LeagueIcon } from "./LeagueIcon";
import { getLeagueFromPoints } from "./utils";

export interface LeagueProgressCardProps {
	leaguePoints: number;
	onInfoClick?: () => void;
}

export function LeagueProgressCard({
	leaguePoints,
	onInfoClick,
}: LeagueProgressCardProps) {
	// Get current league from points
	const currentLeague = getLeagueFromPoints(leaguePoints);

	// Calculate progress value based on league min and max points
	const progressValue = currentLeague
		? ((leaguePoints - currentLeague.minPoints) * 100) /
			(currentLeague.maxPoints - currentLeague.minPoints)
		: 0;

	if (!currentLeague) return null;

	return (
		<div className="flex items-center gap-2 2xl:gap-4">
			<LeagueIcon leaguePoints={leaguePoints} size="lg" />
			<div className="flex flex-col -space-y-1 min-w-[140px]">
				<div className="flex items-center gap-2">
					<div>
						<span className="text-sm font-semibold text-muted-foreground">
							{currentLeague.name}
						</span>
						{/* Points display */}
						<div className="w-full flex items-center justify-center gap-1 text-sm text-muted-foreground">
							<span className="whitespace-nowrap">
								{currentLeague.maxPoints === Number.POSITIVE_INFINITY
									? `${leaguePoints}`
									: `${leaguePoints} / ${currentLeague.maxPoints}`}
							</span>
							<Star className="h-4 w-4 flex-shrink-0" />
						</div>
					</div>
					{onInfoClick && (
						<Button variant="ghost" size="icon" onClick={onInfoClick}>
							<Info className="text-muted-foreground" />
						</Button>
					)}
				</div>
				{/* Progress bar container */}
				{currentLeague.maxPoints !== Number.POSITIVE_INFINITY && (
					<div className="flex items-center gap-2 mt-1">
						<Progress
							value={progressValue}
							className="bg-secondary"
							indicatorClassName={{
								"bg-league-bronze": currentLeague.name === "Bronze",
								"bg-league-silver": currentLeague.name === "Silver",
								"bg-league-gold": currentLeague.name === "Gold",
								"bg-league-diamond": currentLeague.name === "Diamond",
							}}
							aria-label={`${Math.round(progressValue)}% progress to next league`}
						/>
						<LeagueIcon
							leaguePoints={currentLeague.maxPoints + 1}
							size="sm"
							className="flex-shrink-0"
						/>
					</div>
				)}
			</div>
		</div>
	);
}
