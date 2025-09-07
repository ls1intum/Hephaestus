import type { TeamLeaderboardEntry, TeamInfo } from "@/api/types.gen";
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
import {Avatar, AvatarFallback, AvatarImage} from "@/components/ui/avatar.tsx";
import {ReviewsPopover} from "@/components/leaderboard/ReviewsPopover.tsx";
import {Tooltip, TooltipContent, TooltipTrigger} from "@/components/ui/tooltip.tsx";


export interface TeamLeaderboardTableProps {
    isLoading: boolean;
    teamLeaderboard?: TeamLeaderboardEntry[]; //TODO: check if it works with that or if i need to implement a TeamLeaderboardEntry
    currentTeam?: TeamInfo; // TODO: Maybe integrate that later on if the board works to highlight the teams of the logged in user
    onTeamClick?: (teamName: string) => void;
}

export function TeamLeaderboardTable({
    isLoading,
    teamLeaderboard = [],
    // @ts-ignore
    currentTeam,
    onTeamClick,
}: TeamLeaderboardTableProps) {
    if (isLoading) {
        return <TeamLeaderboardTableSkeleton/>
    }

    if (!teamLeaderboard.length) {
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
            <TeamLeaderboardTableHeader />
            <TableBody>
                {/* TODO: Here needs to be a table body calculation like in the LeaderboardTable.tsx file for the normal Leaderboard but with teams data */}
                {teamLeaderboard.map((entry) => {
                //    Decide later whether to use row styling for logged-in users of a team
                    return (
                        <TableRow
                            key={entry.team.name}
                            id={`team-${entry.team.id}`}
                            className="cursor-pointer"
                            onClick={() => {
                                onTeamClick?.(entry.team.name)
                            }}
                        >
                            <TableCell className="text-center">{entry.rank}</TableCell>
                            <TableCell>
                                <div className="flex items-center gap-2 font-medium">
                                    <Avatar className="size-9">
                                        <AvatarImage
                                            src={entry.team.color}
                                            alt={`${entry.team.name}'s avatar`}
                                        />
                                        <AvatarFallback>
                                            {entry.team.name.slice(0, 2).toUpperCase()}
                                        </AvatarFallback>
                                    </Avatar>
                                    <span className="text-muted-foreground text-wrap">
										{entry.team.name}
									</span>
                                </div>
                            </TableCell>
                            <TableCell className="text-center font-medium">
                                {entry.score}
                            </TableCell>
                            <TableCell>
                                <div className="flex items-center gap-2">
                                    {entry.numberOfReviewedPRs > 0 && (
                                        <>
                                            <ReviewsPopover
                                                reviewedPRs={entry.reviewedPullRequests}
                                                // highlight={isCurrentUser}
                                            />
                                            <div className="flex items-center text-github-muted-foreground">
                                                <ChevronLeftIcon className="h-4 w-4" />
                                            </div>
                                        </>
                                    )}
                                    {entry.numberOfChangeRequests > 0 && (
                                        <Tooltip>
                                            <TooltipTrigger asChild>
                                                <div className="flex items-center gap-1 text-github-danger-foreground">
                                                    <FileDiffIcon className="h-4 w-4" />
                                                    <span>{entry.numberOfChangeRequests}</span>
                                                </div>
                                            </TooltipTrigger>
                                            <TooltipContent>Changes Requested</TooltipContent>
                                        </Tooltip>
                                    )}
                                    {entry.numberOfApprovals > 0 && (
                                        <Tooltip>
                                            <TooltipTrigger asChild>
                                                <div className="flex items-center gap-1 text-github-success-foreground">
                                                    <CheckIcon className="h-4 w-4" />
                                                    <span>{entry.numberOfApprovals}</span>
                                                </div>
                                            </TooltipTrigger>
                                            <TooltipContent>Approvals</TooltipContent>
                                        </Tooltip>
                                    )}
                                    {entry.numberOfComments + entry.numberOfUnknowns > 0 && (
                                        <Tooltip>
                                            <TooltipTrigger asChild>
                                                <div className="flex items-center gap-1 text-github-muted-foreground">
                                                    <CommentIcon className="h-4 w-4" />
                                                    <span>
														{entry.numberOfComments + entry.numberOfUnknowns}
													</span>
                                                </div>
                                            </TooltipTrigger>
                                            <TooltipContent>Comments</TooltipContent>
                                        </Tooltip>
                                    )}
                                    {entry.numberOfCodeComments > 0 && (
                                        <Tooltip>
                                            <TooltipTrigger asChild>
                                                <div className="flex items-center gap-1 text-github-muted-foreground">
                                                    <CommentDiscussionIcon className="h-4 w-4" />
                                                    <span>{entry.numberOfCodeComments}</span>
                                                </div>
                                            </TooltipTrigger>
                                            <TooltipContent>Code comments</TooltipContent>
                                        </Tooltip>
                                    )}
                                </div>
                            </TableCell>
                        </TableRow>
                    );
                })
                }
            </TableBody>
        </Table>
    );
}

function TeamLeaderboardTableHeader() {
    return (
        <TableHeader>
            <TableRow>
                {/* TODO: review header spacings and tweak accordingly */}
                <TableHead className="w-10">Rank</TableHead>
                {/*TODO: Review for Team League Icons -> if a team league system is necessary or meaningful*/}
                {/*<TableHead className="text-center w-20">League</TableHead>*/}
                <TableHead className="">Team</TableHead>
                <TableHead className="w-25 text-center">
                    <div className="flex justify-center items-center gap-1 text-github-done-foreground">
							<span className="flex items-center gap-0.5">
								<AwardIcon className="size-4" /> Score
							</span>
                    </div>
                </TableHead>
                <TableHead>Activity</TableHead>
            </TableRow>
        </TableHeader>
    );
}

function TeamLeaderboardTableSkeleton() {
	return (
		<Table>
            <TeamLeaderboardTableHeader />
			<TableBody>
				{Array.from({ length: 10 }).map((_, idx) => (
					// biome-ignore lint/suspicious/noArrayIndexKey: Data is static and not user-generated
					<TableRow key={`skeleton-${idx}`}>
						<TableCell>
							<Skeleton
								className="h-5 w-7"
								style={{ width: `${20 + idx}px` }}
							/>
						</TableCell>
                        {/*TODO: Maybe reintroduce later when team league icons are a thing :D*/}
						{/*<TableCell>*/}
						{/*	<Skeleton className="h-8 w-8 mx-auto" />*/}
						{/*</TableCell>*/}
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
