import { Loader2, Search } from "lucide-react";
import type { PracticeFindingList } from "@/api/types.gen";
import { EmptyState } from "@/components/shared/EmptyState";
import { Button } from "@/components/ui/button";
import {
	Select,
	SelectContent,
	SelectItem,
	SelectTrigger,
	SelectValue,
} from "@/components/ui/select";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import { FindingsListItem } from "./FindingsListItem";
import { isVerdictFilter, type VerdictFilter } from "./verdict-styles";

const VERDICT_FILTER_OPTIONS: Array<{ value: VerdictFilter; label: string }> = [
	{ value: "ALL", label: "All" },
	{ value: "POSITIVE", label: "Positive" },
	{ value: "NEGATIVE", label: "Negative" },
];

/** Placeholder finding for loading skeletons. */
const SKELETON_FINDING: PracticeFindingList = {
	id: "",
	title: "",
	verdict: "POSITIVE",
	severity: "INFO",
	confidence: 0,
	detectedAt: new Date("2025-01-01T00:00:00Z"),
	practiceName: "",
	practiceSlug: "",
	targetId: 0,
	targetType: "",
};

export interface FindingsListProps {
	findings: PracticeFindingList[];
	practiceOptions: Array<{ value: string; label: string }>;
	selectedPracticeSlug: string | null;
	selectedVerdict: VerdictFilter;
	onPracticeSelect: (practiceSlug: string | null) => void;
	onVerdictChange: (verdict: VerdictFilter) => void;
	workspaceSlug?: string;
	hasMore?: boolean;
	onLoadMore?: () => void;
	isLoading?: boolean;
	isLoadingMore?: boolean;
}

export function FindingsList({
	findings,
	practiceOptions,
	selectedPracticeSlug,
	selectedVerdict,
	onPracticeSelect,
	onVerdictChange,
	workspaceSlug,
	hasMore = false,
	onLoadMore,
	isLoading = false,
	isLoadingMore = false,
}: FindingsListProps) {
	const selectItems = [{ value: "all", label: "All practices" }, ...practiceOptions];

	const hasActiveFilters = selectedPracticeSlug !== null || selectedVerdict !== "ALL";

	if (isLoading) {
		return (
			<div className="flex flex-col gap-3" aria-busy={true}>
				<h3 className="text-lg font-semibold">Findings</h3>
				<ul aria-label="Findings loading" className="flex flex-col gap-2">
					{Array.from({ length: 3 }, (_, i) => (
						<FindingsListItem key={i} finding={SKELETON_FINDING} isLoading />
					))}
				</ul>
			</div>
		);
	}

	return (
		<div className="flex flex-col gap-3">
			<h3 className="text-lg font-semibold">Findings</h3>

			{/* Filter bar */}
			<div className="flex flex-wrap items-center gap-2">
				<Select
					value={selectedPracticeSlug ?? "all"}
					onValueChange={(value) => value && onPracticeSelect(value === "all" ? null : value)}
				>
					<SelectTrigger size="sm">
						<SelectValue placeholder="All practices" />
					</SelectTrigger>
					<SelectContent>
						{selectItems.map((item) => (
							<SelectItem key={item.value} value={item.value}>
								{item.label}
							</SelectItem>
						))}
					</SelectContent>
				</Select>

				<ToggleGroup
					value={[selectedVerdict]}
					onValueChange={(value) => {
						const newValue = value[value.length - 1];
						if (isVerdictFilter(newValue)) onVerdictChange(newValue);
					}}
					aria-label="Filter by verdict"
					className="bg-secondary/50 rounded-lg p-0.5"
				>
					{VERDICT_FILTER_OPTIONS.map((opt) => (
						<ToggleGroupItem
							key={opt.value}
							value={opt.value}
							aria-label={`Show ${opt.label.toLowerCase()} findings`}
							className="h-7 px-2.5 text-xs data-[state=on]:bg-background"
						>
							{opt.label}
						</ToggleGroupItem>
					))}
				</ToggleGroup>
			</div>

			{/* Findings list */}
			{findings.length === 0 ? (
				<EmptyState
					icon={Search}
					title={
						hasActiveFilters ? "No findings match the selected filters" : "No practice findings yet"
					}
					description={
						hasActiveFilters
							? "Try adjusting the practice or verdict filter."
							: "Findings from automated practice detection will appear here."
					}
				/>
			) : (
				<ul aria-label="Practice findings" className="flex flex-col gap-2">
					{findings.map((finding) => (
						<FindingsListItem key={finding.id} finding={finding} workspaceSlug={workspaceSlug} />
					))}
				</ul>
			)}

			{/* Load more */}
			{hasMore && (
				<Button
					variant="outline"
					onClick={onLoadMore}
					disabled={isLoadingMore}
					className="self-center"
				>
					{isLoadingMore && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
					Show more findings
				</Button>
			)}
		</div>
	);
}
