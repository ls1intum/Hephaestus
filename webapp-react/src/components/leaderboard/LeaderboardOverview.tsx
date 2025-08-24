import {
	differenceInHours,
	differenceInMinutes,
	differenceInSeconds,
	isPast,
} from "date-fns";
import {
	CalendarClock,
	MoveRight,
	TrendingDown,
	TrendingUp,
} from "lucide-react";
import { useEffect, useState } from "react";
import type { LeaderboardEntry } from "@/api/types.gen";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { LeagueInfoDialog } from "./LeagueInfoDialog";
import { LeagueProgressCard } from "./LeagueProgressCard";

export interface LeaderboardOverviewProps {
	leaderboardEntry: LeaderboardEntry;
	leaguePoints: number;
	leaderboardEnd?: string;
	leaguePointsChange?: number;
}

export function LeaderboardOverview({
	leaderboardEntry,
	leaguePoints,
	leaderboardEnd,
	leaguePointsChange = 0,
}: LeaderboardOverviewProps) {
	const [leagueInfoOpen, setLeagueInfoOpen] = useState(false);
	const [countdown, setCountdown] = useState<string>("Calculating...");

	// Use an effect to update the countdown timer every second
	useEffect(() => {
		// Calculate countdown function that uses the current leaderboardEnd
		const calculateCountdown = () => {
			if (!leaderboardEnd) return "N/A";

			const endDate = new Date(leaderboardEnd);

			if (isPast(endDate)) {
				return "Ended";
			}

			const now = new Date();
			const diffHours = differenceInHours(endDate, now);
			const diffMinutes = differenceInMinutes(endDate, now) % 60;
			const diffSeconds = differenceInSeconds(endDate, now) % 60;

			if (diffHours > 24) {
				const days = Math.floor(diffHours / 24);
				const hours = diffHours % 24;
				return `${days}d ${hours}h`;
			}

			if (diffHours > 0) {
				return `${diffHours}h ${diffMinutes}m`;
			}

			if (diffMinutes > 0) {
				return `${diffMinutes}m ${diffSeconds}s`;
			}

			return `${diffSeconds}s`;
		};

		// Initial calculation
		setCountdown(calculateCountdown());

		// Update the countdown every second
		const timer = setInterval(() => {
			setCountdown(calculateCountdown());
		}, 1000);

		// Cleanup interval on unmount
		return () => clearInterval(timer);
	}, [leaderboardEnd]);

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
									<span className="text-3xl font-light text-muted-foreground">
										#
									</span>
									<span className="text-4xl text-primary font-light">
										{leaderboardEntry.rank}
									</span>
								</span>
								<div className="h-10 w-10 rounded-full bg-muted flex items-center justify-center overflow-hidden">
									{leaderboardEntry.user.avatarUrl ? (
										<img
											src={leaderboardEntry.user.avatarUrl}
											alt={`${leaderboardEntry.user.name}'s avatar`}
											className="h-full w-full object-cover"
										/>
									) : (
										<span className="text-sm font-medium">
											{leaderboardEntry.user.name.slice(0, 2).toUpperCase()}
										</span>
									)}
								</div>
							</div>
							<span className="text-muted-foreground">
								{leaderboardEntry.user.name}
							</span>
						</div>
					</Button>

					<div className="flex flex-col items-center gap-1 text-center">
						<div className="flex items-center gap-1">
							<CalendarClock className="h-4 w-4 text-muted-foreground" />
							<span className="text-sm text-muted-foreground">
								Leaderboard ends in:
							</span>
						</div>
						<span className="text-xl font-medium">{countdown}</span>
						<div className="flex flex-wrap items-center justify-center gap-1 mt-1 text-sm">
							<span className="text-muted-foreground">
								League points change:
							</span>
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
						<LeagueProgressCard
							leaguePoints={leaguePoints}
							onInfoClick={() => setLeagueInfoOpen(true)}
						/>
					</div>
				</div>
			</CardContent>

			<LeagueInfoDialog
				open={leagueInfoOpen}
				onOpenChange={setLeagueInfoOpen}
			/>
		</Card>
	);
}
