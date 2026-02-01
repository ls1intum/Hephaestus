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

export interface TeamFilterOption {
	value: string;
	label: string;
}
export interface TeamFilterProps {
	options: TeamFilterOption[];
	onTeamChange?: (team: string) => void;
	selectedTeam?: string;
}

export function TeamFilter({ options = [], onTeamChange, selectedTeam = "all" }: TeamFilterProps) {
	return (
		<div className="space-y-1.5">
			<Label htmlFor="team">Team</Label>
			<Select value={selectedTeam} onValueChange={(value) => value && onTeamChange?.(value)}>
				<SelectTrigger id="team" className="w-full">
					<SelectValue placeholder="Select Team" />
				</SelectTrigger>
				<SelectContent>
					<SelectItem value="all">All Teams</SelectItem>
					<DropdownMenuSeparator />
					<SelectGroup>
						{options.map((opt) => (
							<SelectItem key={opt.value} value={opt.value}>
								{opt.label}
							</SelectItem>
						))}
					</SelectGroup>
				</SelectContent>
			</Select>
		</div>
	);
}
