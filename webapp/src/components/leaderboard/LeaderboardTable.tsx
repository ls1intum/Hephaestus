import {
	CheckIcon,
	ChevronLeftIcon,
	CommentDiscussionIcon,
	CommentIcon,
	FileDiffIcon,
	NoEntryIcon,
} from "@primer/octicons-react";
import { AwardIcon } from "lucide-react";
import type { LeaderboardEntry, UserInfo } from "@/api/types.gen";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
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
	Tooltip,
	TooltipContent,
	TooltipTrigger,
} from "@/components/ui/tooltip";
import { cn } from "@/lib/utils";
import { LeagueIcon } from "./LeagueIcon";
import { ReviewsPopover } from "./ReviewsPopover";

type TeamLeaderboardEntry = LeaderboardEntry & {
	team: NonNullable<LeaderboardEntry["team"]>;
};

export type LeaderboardVariant = "individual" | "team";

export interface LeaderboardTableProps {
	leaderboard?: LeaderboardEntry[] | TeamLeaderboardEntry[];
	isLoading: boolean;
	variant?: LeaderboardVariant;
	currentUser?: UserInfo;
	onUserClick?: (username: string) => void;
	onTeamClick?: (teamName: string) => void;
}

export function LeaderboardTable({
	leaderboard = [],
	isLoading,
	variant = "individual",
	currentUser,
	onUserClick,
	onTeamClick,
}: LeaderboardTableProps) {
	if (isLoading) {
		return <LeaderboardTableSkeleton />;
	}

	if (!leaderboard.length) {
		return (
			<div className="flex flex-col items-center justify-center p-8 text-center">
				<NoEntryIcon className="h-12 w-12 text-github-danger-foreground mb-2" />
				<h3 className="text-lg font-medium">No entries found</h3>
				<p className="text-muted-foreground">
					There are no leaderboard entries available.
				</p>
			</div>
		);
	}

	const isTeam = variant === "team";

	return (
		<Table>
			<TableHeader>
				<TableRow>
					<TableHead className="text-center w-10">Rank</TableHead>
					{!isTeam && (
						<TableHead className="text-center w-20">League</TableHead>
					)}
					<TableHead className="w-56">
						{isTeam ? "Team" : "Contributor"}
					</TableHead>
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
				{(leaderboard as LeaderboardEntry[]).map((entry) => {
					if (isTeam) {
						const team = (entry as TeamLeaderboardEntry).team;
						if (!team) return null;
						return (
							<TableRow
								key={team.name}
								id={`team-${team.id}`}
								className="cursor-pointer"
								onClick={() => onTeamClick?.(team.name)}
							>
								<TableCell className="text-center">{entry.rank}</TableCell>
								<TableCell>
									<div className="flex items-center gap-2 font-medium">
										<Avatar className="size-9">
											<AvatarImage
												src={`https://avatars.githubusercontent.com/t/${team.id}?s=512&v=4`}
												alt={`${team.name}'s avatar`}
											/>
											<AvatarFallback>
												{team.name.slice(0, 2).toUpperCase()}
											</AvatarFallback>
										</Avatar>
										<span className="text-muted-foreground text-wrap">
											{team.name}
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
					}

					const user = entry.user;
					if (!user) {
						return null;
					}

					const currentUserLogin = currentUser?.login
						? currentUser.login.toLowerCase()
						: undefined;
					const isCurrentUser = currentUserLogin === user.login.toLowerCase();

					return (
						<TableRow
							key={user.login}
							id={`rank-${entry.rank}`}
							className={cn(
								"cursor-pointer",
								isCurrentUser &&
									"bg-accent dark:bg-accent/30 dark:hover:bg-accent/50",
							)}
							onClick={() => {
								onUserClick?.(user.login);
							}}
						>
							<TableCell className="text-center">{entry.rank}</TableCell>
							<TableCell className="px-0">
								<div className="flex flex-col justify-center items-center">
									<LeagueIcon leaguePoints={user.leaguePoints} showPoints />
								</div>
							</TableCell>
							<TableCell>
								<div className="flex items-center gap-2 font-medium">
									<Avatar className="size-9">
										<AvatarImage
											src={user.avatarUrl}
											alt={`${user.name}'s avatar`}
										/>
										<AvatarFallback>
											{user.name.slice(0, 2).toUpperCase()}
										</AvatarFallback>
									</Avatar>
									<span className="text-muted-foreground text-wrap">
										{user.name}
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
												highlight={isCurrentUser}
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
				})}
			</TableBody>
		</Table>
	);
}

function LeaderboardTableSkeleton() {
	return (
		<Table>
			<TableHeader>
				<TableRow>
					<TableHead className="text-center w-16">Rank</TableHead>
					<TableHead className="text-center w-20">League</TableHead>
					<TableHead>Contributor</TableHead>
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
