import { DropdownMenuSeparator } from "@/components/ui/dropdown-menu";
import { Label } from "@/components/ui/label";
import {
	Select,
	SelectContent,
	SelectGroup,
	SelectItem,
	SelectTrigger,
	SelectValue,
} from "@/components/ui/select";

export interface TeamFilterProps {
	teams: string[];
	onTeamChange?: (team: string) => void;
	selectedTeam?: string;
}

export function TeamFilter({
	teams = [],
	onTeamChange,
	selectedTeam = "all",
}: TeamFilterProps) {
	return (
		<div className="space-y-1.5">
			<Label htmlFor="team">Team</Label>
			<Select
				value={selectedTeam}
				onValueChange={(value) => onTeamChange?.(value)}
			>
				<SelectTrigger id="team" className="w-full">
					<SelectValue placeholder="Select Team" />
				</SelectTrigger>
				<SelectContent>
					<SelectItem value="all">All Teams</SelectItem>
					<DropdownMenuSeparator />
					<SelectGroup>
						{teams.map((team) => (
							<SelectItem key={team} value={team}>
								{team}
							</SelectItem>
						))}
					</SelectGroup>
				</SelectContent>
			</Select>
		</div>
	);
}
