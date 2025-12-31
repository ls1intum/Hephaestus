import { Users, UsersRound } from "lucide-react";
import { Label } from "@/components/ui/label";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import type { MemberCountMode } from "@/lib/team-hierarchy";

export interface MemberCountModeToggleProps {
	mode: MemberCountMode;
	onModeChange: (mode: MemberCountMode) => void;
	className?: string;
}

/**
 * Toggle control for switching between direct and rollup member counting modes.
 *
 * - **Direct**: Count only team's own members
 * - **Rollup**: Count unique members from team + all subteams
 */
export function MemberCountModeToggle({
	mode,
	onModeChange,
	className = "",
}: MemberCountModeToggleProps) {
	return (
		<div className={`flex flex-col gap-1.5 ${className}`}>
			<Label>Member Count</Label>
			<ToggleGroup
				type="single"
				value={mode}
				onValueChange={(value) => {
					if (value) {
						onModeChange(value as MemberCountMode);
					}
				}}
				className="justify-start"
			>
				<Tooltip>
					<TooltipTrigger asChild>
						<ToggleGroupItem value="direct" aria-label="Direct members only" className="gap-1.5">
							<Users className="h-4 w-4" />
							<span className="hidden sm:inline">Direct</span>
						</ToggleGroupItem>
					</TooltipTrigger>
					<TooltipContent>
						<p>Count only direct team members</p>
					</TooltipContent>
				</Tooltip>

				<Tooltip>
					<TooltipTrigger asChild>
						<ToggleGroupItem
							value="rollup"
							aria-label="Include subteam members"
							className="gap-1.5"
						>
							<UsersRound className="h-4 w-4" />
							<span className="hidden sm:inline">Include Subteams</span>
						</ToggleGroupItem>
					</TooltipTrigger>
					<TooltipContent>
						<p>Count unique members from team + all subteams</p>
					</TooltipContent>
				</Tooltip>
			</ToggleGroup>
		</div>
	);
}

export default MemberCountModeToggle;
