import { Eye, EyeOff, Users, UsersRound } from "lucide-react";
import type { ReactNode } from "react";
import type { LabelInfo, TeamInfo } from "@/api/types.gen";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import type { MemberCountMode } from "@/lib/team-hierarchy";
import { cn } from "@/lib/utils";

export interface TeamCardProps {
	team: TeamInfo;
	/** Direct member count (team's own members) */
	memberCount: number;
	/** Rollup member count (unique members from team + subteams) */
	rollupMemberCount?: number;
	/** Which count mode is currently active */
	countMode?: MemberCountMode;
	labelFilter?: string;
	onToggleVisibility: (hidden: boolean) => void;
	getCatalogLabels: (repoId: number) => LabelInfo[];
	children?: ReactNode;
}

export function TeamCard({
	team,
	memberCount,
	rollupMemberCount,
	countMode = "direct",
	onToggleVisibility,
	children,
}: TeamCardProps) {
	const displayCount =
		countMode === "rollup" && rollupMemberCount !== undefined ? rollupMemberCount : memberCount;
	const isRollupMode = countMode === "rollup";
	const hasSubteamMembers = rollupMemberCount !== undefined && rollupMemberCount > memberCount;
	return (
		<Card className={cn("flex flex-col gap-3", team.hidden ? "bg-muted/40" : "")}>
			<CardHeader className="pb-4">
				<div className="flex flex-wrap items-start justify-between gap-2">
					<div className="min-w-0 flex-1">
						<div className="flex items-center gap-2 min-w-0">
							<h3
								className={cn(
									"font-semibold text-lg truncate",
									team.hidden ? "text-muted-foreground" : "",
								)}
								title={team.name}
							>
								{team.name}
							</h3>
							{team.hidden && (
								<span className="text-[10px] px-1.5 py-0.5 rounded bg-muted text-muted-foreground uppercase tracking-wide">
									Hidden
								</span>
							)}
						</div>
						<div className="flex flex-wrap items-center gap-2 sm:gap-4 text-sm text-muted-foreground">
							<Tooltip>
								<TooltipTrigger asChild>
									<span className="flex items-center gap-1 cursor-help">
										{isRollupMode ? (
											<UsersRound className="h-3 w-3" />
										) : (
											<Users className="h-3 w-3" />
										)}{" "}
										{displayCount} {displayCount === 1 ? "member" : "members"}
										{isRollupMode && hasSubteamMembers && (
											<span className="text-xs text-muted-foreground/70">
												({memberCount} direct)
											</span>
										)}
									</span>
								</TooltipTrigger>
								<TooltipContent>
									{isRollupMode ? (
										<p>
											Unique members from team + subteams
											{hasSubteamMembers && ` (${memberCount} direct)`}
										</p>
									) : (
										<p>Direct team members only</p>
									)}
								</TooltipContent>
							</Tooltip>
							<span>
								{(team.repositories ?? []).length}{" "}
								{(team.repositories ?? []).length === 1 ? "repo" : "repos"}
							</span>
						</div>
					</div>
					<div className="flex items-center gap-1">
						<Button
							variant="ghost"
							size="icon"
							onClick={() => onToggleVisibility(!team.hidden)}
							className="h-8 w-8"
							title={team.hidden ? "Show team" : "Hide team"}
						>
							{team.hidden ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
						</Button>
					</div>
				</div>
			</CardHeader>
			<CardContent>{children}</CardContent>
		</Card>
	);
}

export default TeamCard;
