import type { ContributorPracticeSummary } from "@/api/types.gen";
import { PracticeSummaryCard } from "./PracticeSummaryCard";

/** Placeholder summary for loading skeletons. */
const SKELETON_SUMMARY: ContributorPracticeSummary = {
	practiceName: "",
	practiceSlug: "",
	positiveCount: 0,
	negativeCount: 0,
	totalFindings: 0,
};

export interface PracticeSummaryGridProps {
	summaries: ContributorPracticeSummary[];
	selectedPracticeSlug: string | null;
	onPracticeSelect: (practiceSlug: string | null) => void;
	isLoading?: boolean;
}

export function PracticeSummaryGrid({
	summaries,
	selectedPracticeSlug,
	onPracticeSelect,
	isLoading = false,
}: PracticeSummaryGridProps) {
	if (isLoading) {
		return (
			<div className="flex flex-col gap-3" aria-busy={true}>
				<h3 className="text-lg font-semibold">Practice overview</h3>
				<ul
					aria-label="Practice summaries loading"
					className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3"
				>
					{Array.from({ length: 3 }, (_, i) => (
						<li key={i}>
							<PracticeSummaryCard summary={SKELETON_SUMMARY} isLoading />
						</li>
					))}
				</ul>
			</div>
		);
	}

	// Parent (PracticeSection) handles the global empty state when no findings
	// exist at all. The grid simply hides when no summaries pass the threshold.
	if (summaries.length === 0) {
		return null;
	}

	const handleCardSelect = (practiceSlug: string) => {
		onPracticeSelect(selectedPracticeSlug === practiceSlug ? null : practiceSlug);
	};

	return (
		<div className="flex flex-col gap-3">
			<h3 className="text-lg font-semibold">Practice overview</h3>
			<ul
				aria-label="Practice summaries"
				className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3"
			>
				{summaries.map((summary) => (
					<li key={summary.practiceSlug}>
						<PracticeSummaryCard
							summary={summary}
							isSelected={selectedPracticeSlug === summary.practiceSlug}
							onSelect={handleCardSelect}
						/>
					</li>
				))}
			</ul>
		</div>
	);
}
