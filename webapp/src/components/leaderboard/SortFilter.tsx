import { Label } from "@/components/ui/label";
import {
	Select,
	SelectContent,
	SelectItem,
	SelectTrigger,
	SelectValue,
} from "@/components/ui/select";

export type LeaderboardSortType = "SCORE" | "LEAGUE_POINTS";

const SORT_OPTIONS: Array<{ value: LeaderboardSortType; label: string }> = [
	{ value: "SCORE", label: "Score" },
	{ value: "LEAGUE_POINTS", label: "League Points" },
];

export interface SortFilterProps {
	onSortChange?: (sort: LeaderboardSortType) => void;
	selectedSort?: LeaderboardSortType;
}

export function SortFilter({ onSortChange, selectedSort = "SCORE" }: SortFilterProps) {
	return (
		<div className="space-y-1.5">
			<Label htmlFor="sort">Sort by</Label>
			<Select
				value={selectedSort}
				onValueChange={(value) => value && onSortChange?.(value as LeaderboardSortType)}
				items={SORT_OPTIONS}
			>
				<SelectTrigger id="sort" className="w-full">
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
