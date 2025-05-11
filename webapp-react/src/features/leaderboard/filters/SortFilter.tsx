import { SelectValue, SelectTrigger, SelectItem, SelectContent, Select } from "@/components/ui/select";
import type { SortFilterProps, LeaderboardSortType } from "../types";

const SORT_OPTIONS: Array<{ value: LeaderboardSortType; label: string }> = [
  { value: "SCORE", label: "Score" },
  { value: "LEAGUE_POINTS", label: "League Points" }
];

export function SortFilter({ onSortChange, selectedSort = "SCORE" }: SortFilterProps) {
  return (
    <div className="space-y-2">
      <label className="text-sm font-medium">Sort by</label>
      <Select
        value={selectedSort}
        onValueChange={(value) => onSortChange?.(value as LeaderboardSortType)}
      >
        <SelectTrigger className="w-full">
          <SelectValue placeholder="Sort by" />
        </SelectTrigger>
        <SelectContent>
          {SORT_OPTIONS.map((option) => (
            <SelectItem key={option.value} value={option.value}>
              {option.label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  );
}