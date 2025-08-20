import type { LeaderboardEntry, TeamInfo } from "@/api/types.gen";
import { Skeleton } from "@/components/ui/skeleton";
import {
	Table,
	TableBody,
	TableCell,
	TableHead,
	TableHeader,
	TableRow,
} from "@/components/ui/table";
import {
	CheckIcon,
	ChevronLeftIcon,
	CommentDiscussionIcon,
	CommentIcon,
	FileDiffIcon,
	NoEntryIcon,
} from "@primer/octicons-react";
import { AwardIcon } from "lucide-react";


export interface TeamLeaderboardTableProps {
    isLoading: boolean;
    leaderboard?: LeaderboardEntry[]; //TODO: check if it works with that or if i need to implement a TeamLeaderboardEntry
    currentTeam?: TeamInfo;
    onTeamClick?: (teamname: string) => void;
}

export function TeamLeaderboardTable({
    isLoading,
    leaderboard = [],
    currentTeam,
    onTeamClick,
}: TeamLeaderboardTableProps) {
    if (isLoading) {
        return <TeamLeaderboardTableSkeleton/>
    }

    if (!leaderboard.length) {
        return (
            <div className="flex flex-col items-center justify-center p-8 text-center">
				<NoEntryIcon className="h-12 w-12 text-github-danger-foreground mb-2" />
				<h3 className="text-lg font-medium">No entries found</h3>
				<p className="text-muted-foreground">
					There are no team leaderboard entries available.
				</p>
			</div>
        );
    }

    return (
        <Table>
            <TableHeader>
				<TableRow>
                    {/* TODO: review header spacings and tweak accordingly */}
					<TableHead className="text-center w-10">Rank</TableHead>
					<TableHead className="text-center w-20">League</TableHead>
					<TableHead className="w-56">Team</TableHead>
					<TableHead className="text-center">
						<div className="flex justify-center items-center gap-1 text-github-done-foreground">
							<span className="flex items-center gap-0.5">
								<AwardIcon className="size-4" /> Score
							</span>
						</div>
					</TableHead>
					<TableHead>Activity</TableHead>
				</TableRow>
			</TableHeader>
            <TableBody>
                {/* TODO: Here needs to be a table body calculation like in the LeaderboardTable.tsx file for the normal Leaderboard but with teams data */}
                {leaderboard.map((entry) => {
                //    Decide later wether to use row styling for logged in users of a team
                    return (
                        <TableRow>

                        </TableRow>
                    );
                })
                }
            </TableBody>
        </Table>
    );
}

function TeamLeaderboardTableSkeleton() {
	return (
		<Table>
			<TableHeader>
				<TableRow>
					<TableHead className="text-center w-16">Rank</TableHead>
					<TableHead className="text-center w-20">League</TableHead>
					<TableHead>Team</TableHead>
					<TableHead className="text-center">Score</TableHead>
					<TableHead>Activity</TableHead>
				</TableRow>
			</TableHeader>
			<TableBody>
				{Array.from({ length: 10 }).map((_, idx) => (
					// biome-ignore lint/suspicious/noArrayIndexKey: Data is static and not user-generated
					<TableRow key={`skeleton-${idx}`}>
						<TableCell>
							<Skeleton
								className="h-5 w-7"
								style={{ width: `${20 + 1 * idx}px` }}
							/>
						</TableCell>
						<TableCell>
							<Skeleton className="h-8 w-8 mx-auto" />
						</TableCell>
						<TableCell className="py-2">
							<div className="flex items-center gap-2">
								<Skeleton className="w-10 h-10 rounded-full" />
								<Skeleton
									className="h-5"
									style={{ width: `${100 + (idx % 3) * 75}px` }}
								/>
							</div>
						</TableCell>
						<TableCell className="text-center">
							<Skeleton
								className="h-5 mx-auto"
								style={{ width: `${20 + (10 - idx) + (idx % 3) * 4}px` }}
							/>
						</TableCell>
						<TableCell className="py-2">
							<Skeleton
								className="h-5"
								style={{ width: `${30 + ((idx % 4) * 20) / (idx + 1)}px` }}
							/>
						</TableCell>
					</TableRow>
				))}
			</TableBody>
		</Table>
	);
}
