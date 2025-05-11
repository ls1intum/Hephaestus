import { SelectValue, SelectTrigger, SelectItem, SelectContent, Select } from "@/components/ui/select";
import type { TeamFilterProps } from "../types";

export function TeamFilter({ teams = [], onTeamChange, selectedTeam = "all" }: TeamFilterProps) {
  return (
    <div className="space-y-2">
      <label className="text-sm font-medium">Team</label>
      <Select
        value={selectedTeam}
        onValueChange={(value) => onTeamChange?.(value)}
      >
        <SelectTrigger className="w-full">
          <SelectValue placeholder="Select Team" />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="all">All Teams</SelectItem>
          {teams.map((team) => (
            <SelectItem key={team} value={team}>
              {team}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  );
}