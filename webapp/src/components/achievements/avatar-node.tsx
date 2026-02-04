import { Handle, Position } from "@xyflow/react";
import { memo } from "react";
import { getLeagueTier } from "@/components/leaderboard/utils";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { cn } from "@/lib/utils";

interface AvatarNodeData {
	level?: number;
	leaguePoints?: number;
	avatarUrl?: string;
	name?: string;
}

function AvatarNodeComponent({ data }: { data: AvatarNodeData }) {
	const level = data.level ?? 1;
	const leaguePoints = data.leaguePoints ?? 0;
	const rawTier = getLeagueTier(leaguePoints);
	const leagueTier = rawTier === "none" ? "bronze" : rawTier;

	return (
		<div className="relative group">
			{/* Pulse effect */}
			<div className="absolute inset-0 rounded-full bg-primary/20 animate-ping opacity-75 duration-3000 pointer-events-none" />

			{/* Avatar with Level Badge - Matching ProfileHeader.tsx styling */}
			<div className="relative shrink-0 transition-transform duration-300 hover:scale-105">
				<Avatar className="size-24 border-4 border-background shadow-[0_0_30px_rgba(var(--shadow-rgb),0.3)]">
					<AvatarImage src={`https://github.com/${data.name}.png`} alt={`${data.name}'s avatar`} />
					<AvatarFallback className="text-2xl font-bold bg-secondary/50">
						{data.name?.slice(0, 2)?.toUpperCase() ?? "HP"}
					</AvatarFallback>
				</Avatar>

				{/* Level Badge */}
				<Tooltip>
					<TooltipTrigger
						render={
							<div
								className={cn(
									"absolute -bottom-1 -right-1 flex size-9 items-center justify-center rounded-full border-4 border-background text-primary-foreground font-bold text-sm shadow-md cursor-help",
									`bg-league-${leagueTier}`,
								)}
							/>
						}
					>
						{level}
					</TooltipTrigger>
					<TooltipContent side="bottom">
						<p>Level {level}</p>
					</TooltipContent>
				</Tooltip>
			</div>

			{/* Source Handle - connect to first nodes */}
			<Handle
				type="source"
				position={Position.Bottom}
				className="!bg-transparent !border-0 !w-0 !h-0"
				style={{ top: "50%", left: "50%", transform: "translate(-50%, -50%)" }}
			/>
		</div>
	);
}

export const AvatarNode = memo(AvatarNodeComponent);
