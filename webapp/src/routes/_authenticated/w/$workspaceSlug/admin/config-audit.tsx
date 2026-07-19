import { useInfiniteQuery } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { History } from "lucide-react";
import { useState } from "react";
import { listWorkspaceConfigAuditEventsInfiniteOptions } from "@/api/@tanstack/react-query.gen";
import type { ConfigAuditEntryView, PageConfigAuditEntryView } from "@/api/types.gen";
import { dayEndIso, dayStartIso } from "@/components/admin/audit-shared/dateFilter";
import {
	type ConfigAuditFilterState,
	ConfigAuditFilters,
} from "@/components/admin/config-audit/ConfigAuditFilters";
import { ConfigAuditTable } from "@/components/admin/config-audit/ConfigAuditTable";

const PAGE_SIZE = 50;

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/config-audit")({
	component: WorkspaceConfigAuditPage,
});

function WorkspaceConfigAuditPage() {
	// The slug is validated by the admin layout's beforeLoad, so it is always present here.
	const { workspaceSlug } = Route.useParams();
	const [filter, setFilter] = useState<ConfigAuditFilterState>({});

	const query = {
		entityType: filter.entityType,
		action: filter.action,
		from: filter.dateRange?.from ? dayStartIso(filter.dateRange.from) : undefined,
		to: filter.dateRange?.to ? dayEndIso(filter.dateRange.to) : undefined,
	};

	const listQuery = useInfiniteQuery({
		...listWorkspaceConfigAuditEventsInfiniteOptions({
			path: { workspaceSlug },
			query: { size: PAGE_SIZE, ...query },
		}),
		initialPageParam: 0,
		getNextPageParam: (lastPage: PageConfigAuditEntryView) =>
			lastPage.last ? undefined : (lastPage.number ?? 0) + 1,
	});

	const entries: ConfigAuditEntryView[] =
		listQuery.data?.pages.flatMap((p) => p.content ?? []) ?? [];
	const total = listQuery.data?.pages[0]?.totalElements;
	const hasFilter =
		filter.entityType !== undefined ||
		filter.action !== undefined ||
		filter.dateRange?.from !== undefined;

	return (
		<div className="mx-auto w-full max-w-6xl space-y-6 py-6">
			<header className="space-y-1">
				<div className="flex items-center gap-2">
					<History className="size-6 text-muted-foreground" aria-hidden />
					<h1 className="text-2xl font-semibold">Configuration changes</h1>
				</div>
				<p className="text-sm text-muted-foreground">
					Who changed which setting in this workspace, and when — review-practice settings, AI
					bindings, and agent configurations. Append-only.
				</p>
			</header>

			<ConfigAuditFilters
				idPrefix="ws-config-audit"
				value={filter}
				onChange={setFilter}
				onClear={() => setFilter({})}
				hasFilter={hasFilter}
			/>

			{total !== undefined && total > 0 && (
				<p className="text-xs text-muted-foreground">
					{total.toLocaleString()} change{total === 1 ? "" : "s"}
					{hasFilter ? " match the current filters" : ""}.
				</p>
			)}

			<ConfigAuditTable
				entries={entries}
				isLoading={listQuery.isLoading}
				isError={listQuery.isError}
				hasFilter={hasFilter}
				hasNextPage={Boolean(listQuery.hasNextPage)}
				isFetchingNextPage={listQuery.isFetchingNextPage}
				onLoadMore={() => listQuery.fetchNextPage()}
				onRetry={() => listQuery.refetch()}
			/>
		</div>
	);
}
