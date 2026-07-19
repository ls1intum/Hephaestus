import { useInfiniteQuery, useQuery } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { History, X } from "lucide-react";
import { useState } from "react";
import {
	adminListConfigAuditEventsInfiniteOptions,
	adminListWorkspacesOptions,
} from "@/api/@tanstack/react-query.gen";
import type { ConfigAuditEntryView, PageConfigAuditEntryView } from "@/api/types.gen";
import { dayEndIso, dayStartIso } from "@/components/admin/audit-shared/dateFilter";
import {
	type ConfigAuditFilterState,
	ConfigAuditFilters,
} from "@/components/admin/config-audit/ConfigAuditFilters";
import { ConfigAuditTable } from "@/components/admin/config-audit/ConfigAuditTable";
import { Badge } from "@/components/ui/badge";

const PAGE_SIZE = 50;

export const Route = createFileRoute("/_authenticated/admin/config-audit")({
	component: AdminConfigAuditPage,
});

function AdminConfigAuditPage() {
	const [filter, setFilter] = useState<ConfigAuditFilterState>({});
	const [actorId, setActorId] = useState<number | undefined>(undefined);

	const query = {
		entityType: filter.entityType,
		action: filter.action,
		actorId,
		from: filter.dateRange?.from ? dayStartIso(filter.dateRange.from) : undefined,
		to: filter.dateRange?.to ? dayEndIso(filter.dateRange.to) : undefined,
	};

	const workspacesQuery = useQuery(adminListWorkspacesOptions());
	const workspaceNames = new Map(
		(workspacesQuery.data ?? []).map((w) => [w.id, w.displayName || w.workspaceSlug] as const),
	);

	const listQuery = useInfiniteQuery({
		...adminListConfigAuditEventsInfiniteOptions({ query: { size: PAGE_SIZE, ...query } }),
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
		filter.dateRange?.from !== undefined ||
		actorId !== undefined;

	const clearAll = () => {
		setFilter({});
		setActorId(undefined);
	};

	return (
		<div className="mx-auto w-full max-w-6xl space-y-6 py-6">
			<header className="space-y-1">
				<div className="flex items-center gap-2">
					<History className="size-6 text-muted-foreground" aria-hidden />
					<h1 className="text-2xl font-semibold">Configuration changes</h1>
				</div>
				<p className="text-sm text-muted-foreground">
					Who changed which setting, and when — review-practice settings, AI bindings, and agent
					configurations, across every workspace. Append-only. For sign-ins and account events see
					the Audit log.
				</p>
			</header>

			<ConfigAuditFilters
				idPrefix="config-audit"
				value={filter}
				onChange={setFilter}
				onClear={clearAll}
				hasFilter={hasFilter}
			/>

			{actorId !== undefined && (
				<Badge variant="secondary" className="gap-1">
					Actor #{actorId}
					<button
						type="button"
						aria-label="Clear actor filter"
						onClick={() => setActorId(undefined)}
						className="rounded-sm outline-none focus-visible:ring-2 focus-visible:ring-ring"
					>
						<X className="size-3" aria-hidden />
					</button>
				</Badge>
			)}

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
				onFilterActor={setActorId}
				showWorkspace
				resolveWorkspaceName={(id) => workspaceNames.get(id)}
			/>
		</div>
	);
}
