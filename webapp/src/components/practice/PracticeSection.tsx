import { RefreshCw, Search, XCircleIcon } from "lucide-react";
import { EmptyState } from "@/components/shared/EmptyState";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { usePracticeFindings } from "@/hooks/use-practice-findings";
import { FindingsList } from "./FindingsList";
import { PracticeSummaryGrid } from "./PracticeSummaryGrid";

export interface PracticeSectionProps {
	workspaceSlug: string;
}

export function PracticeSection({ workspaceSlug }: PracticeSectionProps) {
	const {
		practicesEnabled,
		isFeaturesLoading,
		visibleSummaries,
		findings,
		practiceOptions,
		selectedPracticeSlug,
		selectedVerdict,
		onPracticeSelect,
		onVerdictChange,
		isInitialLoading,
		isError,
		hasSummaries,
		hasFindings,
		hasMore,
		isFetchingMore,
		fetchMore,
		retry,
	} = usePracticeFindings(workspaceSlug);

	// Don't render anything while checking feature flag or if disabled
	if (isFeaturesLoading || !practicesEnabled) {
		return null;
	}

	// Error state
	if (!isInitialLoading && isError) {
		return (
			<div className="flex flex-col gap-3">
				<h2 className="text-xl font-semibold">Practices</h2>
				<Alert variant="destructive" className="max-w-xl">
					<XCircleIcon className="h-4 w-4" />
					<AlertTitle>Failed to load practice data</AlertTitle>
					<AlertDescription>Something went wrong loading your practice findings.</AlertDescription>
				</Alert>
				<Button variant="outline" size="sm" className="self-start" onClick={retry}>
					<RefreshCw className="mr-2 h-4 w-4" />
					Retry
				</Button>
			</div>
		);
	}

	// Fully empty: no summaries AND no findings → show empty state
	if (!isInitialLoading && !hasSummaries && !hasFindings) {
		return (
			<div className="flex flex-col gap-3">
				<h2 className="text-xl font-semibold">Practices</h2>
				<EmptyState
					icon={Search}
					title="No practice findings yet"
					description="Findings from automated practice detection will appear here as you contribute."
				/>
			</div>
		);
	}

	return (
		<div className="flex flex-col gap-6" aria-busy={isInitialLoading}>
			<h2 className="text-xl font-semibold">Practices</h2>
			<PracticeSummaryGrid
				summaries={visibleSummaries}
				selectedPracticeSlug={selectedPracticeSlug}
				onPracticeSelect={onPracticeSelect}
				isLoading={isInitialLoading}
			/>
			<FindingsList
				findings={findings}
				practiceOptions={practiceOptions}
				selectedPracticeSlug={selectedPracticeSlug}
				selectedVerdict={selectedVerdict}
				onPracticeSelect={onPracticeSelect}
				onVerdictChange={onVerdictChange}
				hasMore={hasMore}
				onLoadMore={fetchMore}
				isLoading={isInitialLoading}
				isLoadingMore={isFetchingMore}
			/>
		</div>
	);
}
