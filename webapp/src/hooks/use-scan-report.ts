import {
	type InfiniteData,
	type UseInfiniteQueryResult,
	useInfiniteQuery,
	useMutation,
	useQuery,
	useQueryClient,
} from "@tanstack/react-query";

import {
	getScanOptions,
	getScanQueryKey,
	getScanReportOptions,
	getScanReportQueryKey,
	listScanReportItemsInfiniteOptions,
	listScanReportItemsQueryKey,
	listScanReportProblemsInfiniteOptions,
	listScanReportProblemsQueryKey,
	listScansQueryKey,
	updateScanStatusMutation,
} from "@/api/@tanstack/react-query.gen";
import { exportScanSarif } from "@/api/sdk.gen";
import type { ScanReportItem, ScanReportProblem } from "@/api/types.gen";
import { isActiveScan, SCAN_POLL_MS } from "@/components/admin/practice-scans/scan-display";
import type { ScanListState } from "@/components/admin/practice-scans/scan-list-state";
import type { ScanAction, ScanReportState } from "@/components/admin/practice-scans/ScanReportPage";
import type { ProblemBaselineState } from "@/components/practice-vocabulary/scan-baseline-defs";
import type { ScanEntryStatus } from "@/components/practice-vocabulary/scan-entry-defs";

import { useInvalidateOnSettle } from "./use-invalidate-on-settle";

export const SCAN_REPORT_PAGE_SIZE = 50;

export interface ScanReportFilters {
	baseline?: ProblemBaselineState;
	fileStatus?: ScanEntryStatus;
}

export interface ScanReportData {
	state: ScanReportState;
	problems: ScanListState<ScanReportProblem>;
	files: ScanListState<ScanReportItem>;
	cancel: ScanAction;
	exportSarif: ScanAction;
}

function nextCursor(page: { hasNext: boolean; nextCursor: string | null }) {
	return page.hasNext ? (page.nextCursor ?? undefined) : undefined;
}

function listState<TPage extends { content: readonly TEntry[]; hasNext: boolean }, TEntry>(
	query: UseInfiniteQueryResult<InfiniteData<TPage>>,
): ScanListState<TEntry> {
	if (query.data !== undefined) {
		return {
			status: "ready",
			entries: query.data.pages.flatMap((page) => page.content),
			hasNext: query.hasNextPage,
			loadingMore: query.isFetchingNextPage,
			loadMoreError: query.isFetchNextPageError ? query.error : undefined,
			onLoadMore: () => {
				void query.fetchNextPage();
			},
		};
	}
	if (query.isError) {
		return {
			status: "error",
			error: query.error,
			onRetry: () => {
				void query.refetch();
			},
		};
	}
	return { status: "loading" };
}

export function sarifFilename(disposition: string | null): string | undefined {
	const match = /filename="?(?<name>[^";]+)"?/iu.exec(disposition ?? "");
	return match?.groups?.name;
}

/**
 * Retains loaded data during refetches; errors apply only before the first successful read.
 * Active scan and report queries poll independently until each reaches a terminal state.
 */
export function useScanReport(
	workspaceSlug: string,
	scanId: string,
	filters: ScanReportFilters,
): ScanReportData {
	const queryClient = useQueryClient();
	const path = { workspaceSlug, scanId };
	const scanQuery = useQuery({
		...getScanOptions({ path }),
		refetchInterval: (query) =>
			query.state.data !== undefined && isActiveScan(query.state.data.status)
				? SCAN_POLL_MS
				: false,
	});
	const reportQuery = useQuery({
		...getScanReportOptions({ path }),
		refetchInterval: (query) =>
			query.state.data !== undefined && isActiveScan(query.state.data.scan.status)
				? SCAN_POLL_MS
				: false,
	});
	const reportStatus = reportQuery.data?.scan.status;
	const listsPoll = reportStatus !== undefined && isActiveScan(reportStatus) ? SCAN_POLL_MS : false;

	const problemsQuery = useInfiniteQuery({
		...listScanReportProblemsInfiniteOptions({
			path,
			query: { size: SCAN_REPORT_PAGE_SIZE, baselineState: filters.baseline },
		}),
		initialPageParam: { path, query: { cursor: undefined } },
		getNextPageParam: nextCursor,
		refetchInterval: listsPoll,
	});
	const itemsQuery = useInfiniteQuery({
		...listScanReportItemsInfiniteOptions({
			path,
			query: { size: SCAN_REPORT_PAGE_SIZE, status: filters.fileStatus },
		}),
		initialPageParam: { path, query: { cursor: undefined } },
		getNextPageParam: nextCursor,
		refetchInterval: listsPoll,
	});

	// Read lists once after the report becomes terminal; polling alone can stop before their final rows arrive.
	useInvalidateOnSettle(scanId, listsPoll !== false, [
		listScanReportItemsQueryKey({ path }),
		listScanReportProblemsQueryKey({ path }),
	]);

	const invalidateScan = () => {
		void queryClient.invalidateQueries({ queryKey: getScanReportQueryKey({ path }) });
		void queryClient.invalidateQueries({ queryKey: listScanReportItemsQueryKey({ path }) });
		void queryClient.invalidateQueries({ queryKey: listScanReportProblemsQueryKey({ path }) });
		void queryClient.invalidateQueries({
			queryKey: listScansQueryKey({ path: { workspaceSlug } }),
		});
	};

	const cancel = useMutation({
		...updateScanStatusMutation(),
		onSuccess: (detail) => {
			queryClient.setQueryData(getScanQueryKey({ path }), detail);
			invalidateScan();
		},
	});

	const download = useMutation({
		mutationFn: async () => {
			const { data, response } = await exportScanSarif({
				path,
				// Automatic parsing treats SARIF's +json media type as JSON; downloads require a Blob.
				parseAs: "blob",
				throwOnError: true,
			});
			const filename =
				sarifFilename(response.headers.get("Content-Disposition")) ??
				`hephaestus-scan-${scanId}.sarif`;
			const url = URL.createObjectURL(data);
			const link = document.createElement("a");
			link.href = url;
			link.download = filename;
			link.click();
			URL.revokeObjectURL(url);
		},
	});

	let state: ScanReportState;
	if (scanQuery.data !== undefined && reportQuery.data !== undefined) {
		state = { status: "ready", scan: scanQuery.data, report: reportQuery.data };
	} else if (scanQuery.isError || reportQuery.isError) {
		state = {
			status: "error",
			error: scanQuery.error ?? reportQuery.error,
			onRetry: () => {
				void scanQuery.refetch();
				void reportQuery.refetch();
			},
		};
	} else {
		state = { status: "loading" };
	}

	return {
		state,
		problems: listState(problemsQuery),
		files: listState(itemsQuery),
		cancel: {
			pending: cancel.isPending,
			error: cancel.error ?? undefined,
			onRun: () => cancel.mutate({ path, body: { status: "CANCELLED" } }),
		},
		exportSarif: {
			pending: download.isPending,
			error: download.error ?? undefined,
			onRun: () => download.mutate(),
		},
	};
}
