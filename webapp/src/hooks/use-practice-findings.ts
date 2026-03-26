import { useInfiniteQuery, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import {
	getEngagementOptions,
	getSummaryOptions,
	listFindingsInfiniteOptions,
} from "@/api/@tanstack/react-query.gen";
import type {
	ContributorPracticeSummary,
	FindingFeedbackEngagement,
	ListFindingsData,
	PracticeFindingList,
} from "@/api/types.gen";
import type { VerdictFilter } from "@/components/practice/verdict-styles";
import { useWorkspaceFeatures } from "@/hooks/use-workspace-features";

const INITIAL_PAGE_SIZE = 5;
const STALE_TIME = 30_000;

/** Only show summary cards for practices with at least this many findings. */
const FINDINGS_THRESHOLD = 3;

export interface UsePracticeFindingsReturn {
	practicesEnabled: boolean;
	isFeaturesLoading: boolean;
	visibleSummaries: ContributorPracticeSummary[];
	findings: PracticeFindingList[];
	practiceOptions: Array<{ value: string; label: string }>;
	selectedPracticeSlug: string | null;
	selectedVerdict: VerdictFilter;
	onPracticeSelect: (practiceSlug: string | null) => void;
	onVerdictChange: (verdict: VerdictFilter) => void;
	isInitialLoading: boolean;
	isError: boolean;
	hasSummaries: boolean;
	hasFindings: boolean;
	hasMore: boolean;
	isFetchingMore: boolean;
	fetchMore: () => void;
	retry: () => void;
	totalFindings: number;
	engagement: FindingFeedbackEngagement | undefined;
}

/**
 * Fetches practice summaries and paginated findings for a workspace.
 * Manages filter state (practice slug, verdict) and pagination internally.
 *
 * @param workspaceSlug - The workspace slug
 * @returns Practice data, filter controls, and query states
 */
export function usePracticeFindings(workspaceSlug: string): UsePracticeFindingsReturn {
	const { practicesEnabled, isLoading: featuresLoading } = useWorkspaceFeatures();
	const queryClient = useQueryClient();

	// Filter state is intentionally transient (local state, not URL params).
	// Practice findings are a secondary section on the profile page —
	// persisting filters in the URL would interfere with profile routing.
	const [selectedPracticeSlug, setSelectedPracticeSlug] = useState<string | null>(null);
	const [selectedVerdict, setSelectedVerdict] = useState<VerdictFilter>("ALL");

	// Summary query — one request, all practices
	const summaryQueryOpts = getSummaryOptions({
		path: { workspaceSlug },
	});
	const summaryQuery = useQuery({
		...summaryQueryOpts,
		enabled: practicesEnabled && !featuresLoading,
		staleTime: STALE_TIME,
		placeholderData: (previousData) => previousData,
	});

	// Build query params for findings
	const findingsQueryParams: ListFindingsData["query"] = {
		size: INITIAL_PAGE_SIZE,
		...(selectedPracticeSlug && { practiceSlug: selectedPracticeSlug }),
		...(selectedVerdict !== "ALL" && { verdict: selectedVerdict }),
	};

	// Infinite query for paginated findings
	const findingsInfiniteOpts = listFindingsInfiniteOptions({
		path: { workspaceSlug },
		query: findingsQueryParams,
	});
	const findingsQuery = useInfiniteQuery({
		...findingsInfiniteOpts,
		initialPageParam: 0,
		getNextPageParam: (lastPage) => (lastPage.last ? undefined : (lastPage.number ?? 0) + 1),
		enabled: practicesEnabled && !featuresLoading,
		staleTime: STALE_TIME,
		placeholderData: (previousData) => previousData,
	});

	// Engagement query — feedback stats across all findings
	const engagementQueryOpts = getEngagementOptions({
		path: { workspaceSlug },
	});
	const engagementQuery = useQuery({
		...engagementQueryOpts,
		enabled: practicesEnabled && !featuresLoading && (summaryQuery.data ?? []).length > 0,
		staleTime: STALE_TIME,
	});

	// Derived state
	const allSummaries = summaryQuery.data ?? [];
	const visibleSummaries = allSummaries.filter((s) => s.totalFindings >= FINDINGS_THRESHOLD);
	const findings = findingsQuery.data?.pages.flatMap((p) => p.content ?? []) ?? [];
	const totalFindings = allSummaries.reduce((sum, s) => sum + s.totalFindings, 0);

	const handlePracticeSelect = (practiceSlug: string | null) => {
		setSelectedPracticeSlug(practiceSlug);
		setSelectedVerdict("ALL");
	};

	const retry = () => {
		queryClient.invalidateQueries({ queryKey: summaryQueryOpts.queryKey });
		queryClient.invalidateQueries({ queryKey: findingsInfiniteOpts.queryKey });
		queryClient.invalidateQueries({ queryKey: engagementQueryOpts.queryKey });
	};

	return {
		practicesEnabled,
		isFeaturesLoading: featuresLoading,
		visibleSummaries,
		findings,
		practiceOptions: allSummaries.map((s) => ({
			value: s.practiceSlug,
			label: s.practiceName,
		})),
		selectedPracticeSlug,
		selectedVerdict,
		onPracticeSelect: handlePracticeSelect,
		onVerdictChange: setSelectedVerdict,
		isInitialLoading: summaryQuery.isPending || (findingsQuery.isPending && !findingsQuery.data),
		isError: summaryQuery.isError || findingsQuery.isError,
		hasSummaries: allSummaries.length > 0,
		hasFindings: findings.length > 0,
		hasMore: findingsQuery.hasNextPage,
		isFetchingMore: findingsQuery.isFetchingNextPage,
		fetchMore: findingsQuery.fetchNextPage,
		retry,
		totalFindings,
		engagement: engagementQuery.data,
	};
}
