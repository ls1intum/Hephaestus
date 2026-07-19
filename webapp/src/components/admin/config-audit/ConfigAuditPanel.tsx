import { useInfiniteQuery } from "@tanstack/react-query";
import {
	adminListConfigAuditEventsInfiniteOptions,
	listWorkspaceConfigAuditEventsInfiniteOptions,
} from "@/api/@tanstack/react-query.gen";
import type { ConfigAuditEntryView, PageConfigAuditEntryView } from "@/api/types.gen";
import { AuditDateFacet } from "@/components/admin/audit-shared/AuditDateFacet";
import {
	AuditFacetFilter,
	type AuditFacetOption,
} from "@/components/admin/audit-shared/AuditFacetFilter";
import { AuditToolbar } from "@/components/admin/audit-shared/AuditToolbar";
import {
	type AuditSearch,
	fromDateRange,
	narrowToEnum,
	nonEmpty,
	toDateRange,
} from "@/components/admin/audit-shared/auditSearch";
import { dayEndIso, dayStartIso } from "@/components/admin/audit-shared/dateFilter";
import { ConfigAuditTable } from "@/components/admin/config-audit/ConfigAuditTable";
import {
	ACTION_LABELS,
	ENTITY_TYPE_LABELS,
} from "@/components/admin/config-audit/configAuditFormat";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";

const PAGE_SIZE = 50;

type EntityType = NonNullable<ConfigAuditEntryView["entityType"]>;
type Action = NonNullable<ConfigAuditEntryView["action"]>;

// Facet options come from the shared label maps, so a new server enum value reaches both the filter
// and the table's labels in one edit. Object.entries keeps the maps' declaration order.
const ENTITY_TYPE_OPTIONS: AuditFacetOption[] = Object.entries(ENTITY_TYPE_LABELS).map(
	([value, label]) => ({ value, label }),
);
const ACTION_OPTIONS: AuditFacetOption[] = Object.entries(ACTION_LABELS).map(([value, label]) => ({
	value,
	label,
}));
const ENTITY_TYPES = Object.keys(ENTITY_TYPE_LABELS) as EntityType[];
const ACTIONS = Object.keys(ACTION_LABELS) as Action[];

/** The wire filter both scopes share, built from URL state. */
function toQuery(search: AuditSearch) {
	const dateRange = toDateRange(search);
	return {
		size: PAGE_SIZE,
		entityType: narrowToEnum(search.entityType, ENTITY_TYPES),
		action: narrowToEnum(search.action, ACTIONS),
		entityId: search.entityId,
		actorId: search.actorId,
		from: dateRange?.from ? dayStartIso(dateRange.from) : undefined,
		to: dateRange?.to ? dayEndIso(dateRange.to) : undefined,
	};
}

export interface ConfigAuditPanelProps {
	search: AuditSearch;
	onSearchChange: (patch: Partial<AuditSearch>) => void;
	resolveWorkspaceName?: (id: number) => string | undefined;
}

/** The instance-wide settings trail, across every workspace. */
export function AdminConfigAuditPanel({
	search,
	onSearchChange,
	resolveWorkspaceName,
}: ConfigAuditPanelProps) {
	const listQuery = useInfiniteQuery({
		...adminListConfigAuditEventsInfiniteOptions({ query: toQuery(search) }),
		initialPageParam: 0,
		getNextPageParam: (lastPage: PageConfigAuditEntryView) =>
			lastPage.last ? undefined : (lastPage.number ?? 0) + 1,
	});

	return (
		<ConfigAuditView
			search={search}
			onSearchChange={onSearchChange}
			listQuery={listQuery}
			showWorkspace
			resolveWorkspaceName={resolveWorkspaceName}
		/>
	);
}

/** One workspace's own settings trail, for its workspace admins. */
export function WorkspaceConfigAuditPanel({
	workspaceSlug,
	search,
	onSearchChange,
}: ConfigAuditPanelProps & { workspaceSlug: string }) {
	const listQuery = useInfiniteQuery({
		...listWorkspaceConfigAuditEventsInfiniteOptions({
			path: { workspaceSlug },
			query: toQuery(search),
		}),
		initialPageParam: 0,
		getNextPageParam: (lastPage: PageConfigAuditEntryView) =>
			lastPage.last ? undefined : (lastPage.number ?? 0) + 1,
	});

	return <ConfigAuditView search={search} onSearchChange={onSearchChange} listQuery={listQuery} />;
}

/** The shape both scopes' queries expose to the view — everything the toolbar and table consume. */
interface ConfigAuditListQuery {
	data?: { pages: PageConfigAuditEntryView[] };
	isLoading: boolean;
	isError: boolean;
	hasNextPage: boolean;
	isFetchingNextPage: boolean;
	fetchNextPage: () => void;
	refetch: () => void;
}

/**
 * Toolbar plus table for the settings-change trail. Shared verbatim by the instance-admin "Settings
 * changes" tab and the per-workspace audit page, so the two present the same trail with the same
 * controls and differ only in scope.
 */
function ConfigAuditView({
	search,
	onSearchChange,
	listQuery,
	showWorkspace = false,
	resolveWorkspaceName,
}: ConfigAuditPanelProps & {
	listQuery: ConfigAuditListQuery;
	showWorkspace?: boolean;
}) {
	const dateRange = toDateRange(search);
	const entries: ConfigAuditEntryView[] =
		listQuery.data?.pages.flatMap((p) => p.content ?? []) ?? [];
	const total = listQuery.data?.pages[0]?.totalElements;
	const hasFilter = Boolean(
		search.entityType?.length ||
			search.action?.length ||
			search.entityId ||
			search.actorId !== undefined ||
			search.from,
	);

	const reset = () =>
		onSearchChange({
			entityType: undefined,
			action: undefined,
			entityId: undefined,
			actorId: undefined,
			from: undefined,
			to: undefined,
		});

	return (
		<div className="space-y-4">
			<AuditToolbar hasFilter={hasFilter} onReset={reset}>
				<AuditFacetFilter
					title="What changed"
					options={ENTITY_TYPE_OPTIONS}
					selected={search.entityType ?? []}
					onChange={(values) =>
						onSearchChange({
							entityType: nonEmpty(values),
							// entityId only means something within a chosen kind; the server rejects it alone.
							entityId: values.length > 0 ? search.entityId : undefined,
						})
					}
				/>
				<AuditFacetFilter
					title="Change"
					options={ACTION_OPTIONS}
					selected={search.action ?? []}
					onChange={(values) => onSearchChange({ action: nonEmpty(values) })}
				/>
				<AuditDateFacet
					value={dateRange}
					onChange={(range) => onSearchChange(fromDateRange(range))}
				/>
				{search.actorId !== undefined && (
					<Button
						variant="secondary"
						size="sm"
						className="h-8"
						onClick={() => onSearchChange({ actorId: undefined })}
					>
						Actor #{search.actorId}
						<Badge variant="outline" className="rounded-sm px-1 font-normal">
							Clear
						</Badge>
					</Button>
				)}
			</AuditToolbar>

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
				hasNextPage={listQuery.hasNextPage}
				isFetchingNextPage={listQuery.isFetchingNextPage}
				onLoadMore={() => listQuery.fetchNextPage()}
				onRetry={() => listQuery.refetch()}
				onFilterActor={(actorId) => onSearchChange({ actorId })}
				showWorkspace={showWorkspace}
				resolveWorkspaceName={resolveWorkspaceName}
			/>
		</div>
	);
}
