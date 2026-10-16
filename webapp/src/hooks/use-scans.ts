import { useQuery } from "@tanstack/react-query";

import { listScansOptions } from "@/api/@tanstack/react-query.gen";
import { isActiveScan, SCAN_POLL_MS } from "@/components/admin/practice-scans/scan-display";
import type { ScansListState } from "@/components/admin/practice-scans/ScansPage";

import { queryErrorIsRetryable } from "@/components/common/QueryErrorAlert";
import { keepSameSubject } from "@/runtime/tanstack-query/keep-same-subject";
import { useQueryReadRefusal } from "@/runtime/tanstack-query/use-query-read-refusal";

export const SCANS_PAGE_SIZE = 20;

export function useScanList(
	workspaceSlug: string,
	page: number,
	onPageChange: (page: number) => void,
): ScansListState {
	const options = listScansOptions({
		path: { workspaceSlug },
		query: { page, size: SCANS_PAGE_SIZE },
	});
	const query = useQuery({
		...options,
		placeholderData: keepSameSubject({ workspaceSlug }),
		refetchInterval: (current) =>
			(current.state.data?.content ?? []).some((scan) => isActiveScan(scan.status))
				? SCAN_POLL_MS
				: false,
	});
	useQueryReadRefusal(options.queryKey, query.isError, query.error);
	const retained = query.isRefetchError && queryErrorIsRetryable(query.error);
	if (query.isError && !retained) {
		return {
			status: "error",
			error: query.error,
			onRetry: () => {
				void query.refetch();
			},
		};
	}
	if (query.data !== undefined) {
		return {
			status: "ready",
			scans: query.data.content,
			page,
			totalPages: query.data.totalPages,
			onPageChange,
			refreshFailure: retained
				? {
						error: query.error,
						pending: query.isFetching,
						onRetry: () => {
							void query.refetch();
						},
					}
				: undefined,
		};
	}
	return { status: "loading" };
}
