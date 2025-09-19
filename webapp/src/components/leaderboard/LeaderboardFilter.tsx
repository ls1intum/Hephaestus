import { SlidersHorizontal } from "lucide-react";
import {
	Card,
	CardContent,
	CardDescription,
	CardHeader,
	CardTitle,
} from "@/components/ui/card";
import { type LeaderboardSortType, SortFilter } from "./SortFilter";
import { TeamFilter, type TeamFilterOption } from "./TeamFilter";
import { TimeframeFilter } from "./TimeframeFilter";

export interface LeaderboardFilterProps {
	teamOptions: TeamFilterOption[];
	onTeamChange?: (team: string) => void;
	onSortChange?: (sort: LeaderboardSortType) => void;
	onTimeframeChange?: (
		afterDate: string,
		beforeDate: string,
		timeframe?: string,
	) => void;
	selectedTeam?: string;
	selectedSort?: LeaderboardSortType;
	initialAfterDate?: string;
	initialBeforeDate?: string;
	leaderboardSchedule?: {
		day: number;
		hour: number;
		minute: number;
		formatted: string;
	};
}

export function LeaderboardFilter({
	teamOptions,
	onTeamChange,
	onSortChange,
	onTimeframeChange,
	selectedTeam,
	selectedSort,
	initialAfterDate,
	initialBeforeDate,
	leaderboardSchedule,
}: LeaderboardFilterProps & {
	initialAfterDate?: string;
	initialBeforeDate?: string;
	leaderboardSchedule?: {
		day: number;
		hour: number;
		minute: number;
	};
}) {
	return (
		<Card>
			<CardHeader>
				<CardTitle>
					<SlidersHorizontal className="inline mr-2 h-4 w-4" /> Leaderboard
					Options
				</CardTitle>
				<CardDescription>
					Customize the leaderboard view by filtering and sorting the data.
				</CardDescription>
			</CardHeader>
			<CardContent>
				<div className="grid grid-cols-1 md:grid-cols-3 gap-4 xl:grid-cols-1">
					<TeamFilter
						options={teamOptions}
						onTeamChange={onTeamChange}
						selectedTeam={selectedTeam}
					/>
					<SortFilter onSortChange={onSortChange} selectedSort={selectedSort} />
					<TimeframeFilter
						onTimeframeChange={onTimeframeChange}
						initialAfterDate={initialAfterDate}
						initialBeforeDate={initialBeforeDate}
						leaderboardSchedule={leaderboardSchedule}
					/>
				</div>
			</CardContent>
		</Card>
	);
}
