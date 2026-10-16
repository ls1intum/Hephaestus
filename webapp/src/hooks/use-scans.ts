import { keepPreviousData, useQuery } from "@tanstack/react-query";

import { listScansOptions } from "@/api/@tanstack/react-query.gen";
import { isActiveScan, SCAN_POLL_MS } from "@/components/admin/practice-scans/scan-display";
import type { ScansListState } from "@/components/admin/practice-scans/ScansPage";

export const SCANS_PAGE_SIZE = 20;

export function useScanList(
	workspaceSlug: string,
	page: number,
	onPageChange: (page: number) => void,
): ScansListState {
	const query = useQuery({
		...listScansOptions({ path: { workspaceSlug }, query: { page, size: SCANS_PAGE_SIZE } }),
		placeholderData: keepPreviousData,
		refetchInterval: (current) =>
			(current.state.data?.content ?? []).some((scan) => isActiveScan(scan.status))
				? SCAN_POLL_MS
				: false,
	});
	if (query.data !== undefined) {
		return {
			status: "ready",
			scans: query.data.content,
			page,
			totalPages: query.data.totalPages,
			onPageChange,
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
