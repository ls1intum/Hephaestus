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

/** The report lists' page size: the server's default, named so a test can count on it. */
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

/** The file name the server gave the download, or `undefined` when its header names none. */
export function sarifFilename(disposition: string | null): string | undefined {
	const match = /filename="?(?<name>[^";]+)"?/iu.exec(disposition ?? "");
	return match?.groups?.name;
}

/**
 * One scan's report, as `ScanReportPage` takes it. The scan and its report are read again every
 * five seconds while the scan is active, each stopping on its own terminal answer. A refetch keeps
 * what is loaded on screen; only a first read that failed is an error.
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

	// The lists stop polling with the report, so the answer that made an active scan terminal is
	// followed by one more read of each list: without it they would keep the last page from while it
	// ran.
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
				parseAs: "blob",
				throwOnError: true,
			});
			// The generated type says `string`; with `parseAs: "blob"` the client hands back a Blob.
			const body: unknown = data;
			if (!(body instanceof Blob)) {
				throw new Error("The export did not return a SARIF file.");
			}
			const filename =
				sarifFilename(response.headers.get("Content-Disposition")) ??
				`hephaestus-scan-${scanId}.sarif`;
			const url = URL.createObjectURL(body);
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
