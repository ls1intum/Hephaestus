import {
	type InfiniteData,
	type UseInfiniteQueryResult,
	useInfiniteQuery,
} from "@tanstack/react-query";
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
import { AuditRefFilterPill } from "@/components/admin/audit-shared/AuditRefFilterPill";
import { AuditToolbar } from "@/components/admin/audit-shared/AuditToolbar";
import {
	type ConfigAuditSearch,
	dayEndIso,
	dayStartIso,
	fromDateRange,
	narrowToEnum,
	nonEmpty,
	toDateRange,
} from "@/components/admin/audit-shared/auditSearch";
import { dedupeById } from "@/components/admin/audit-shared/dedupeById";
import { nameForRef } from "@/components/admin/audit-shared/nameForRef";
import { springPageParams } from "@/components/admin/audit-shared/springPage";
import { ConfigAuditTable } from "@/components/admin/config-audit/ConfigAuditTable";
import {
	ACTION_LABELS,
	ENTITY_TYPE_LABELS,
} from "@/components/admin/config-audit/configAuditFormat";

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
function toQuery(search: ConfigAuditSearch) {
	const dateRange = toDateRange(search);
	return {
		size: PAGE_SIZE,
		entityType: narrowToEnum(search.entityType, ENTITY_TYPES),
		action: narrowToEnum(search.action, ACTIONS),
		actorId: search.actorId,
		from: dateRange?.from ? dayStartIso(dateRange.from) : undefined,
		to: dateRange?.to ? dayEndIso(dateRange.to) : undefined,
	};
}

export interface ConfigAuditPanelProps {
	search: ConfigAuditSearch;
	onSearchChange: (patch: Partial<ConfigAuditSearch>) => void;
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
		...springPageParams,
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
		...springPageParams,
	});

	return <ConfigAuditView search={search} onSearchChange={onSearchChange} listQuery={listQuery} />;
}

/** What both scopes' queries return; the library's own type, so the two cannot silently diverge. */
type ConfigAuditListQuery = UseInfiniteQueryResult<InfiniteData<PageConfigAuditEntryView>, Error>;

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
	// Deduped by id: pages are offsets over a DESC ordering, so a row written between two fetches
	// shifts everything down and page N+1 re-serves rows already rendered — duplicate React keys on
	// exactly the surface whose own subject matter is being written while you read it.
	const entries: ConfigAuditEntryView[] = dedupeById(
		listQuery.data?.pages.flatMap((p) => p.content ?? []) ?? [],
	);
	const total = listQuery.data?.pages[0]?.totalElements;
	// Derived from the NARROWED query, not from raw search state: a stale link whose enum values no
	// longer exist filters nothing, so offering "Reset" and claiming rows "match the current filters"
	// would both be lies.
	const query = toQuery(search);
	const hasFilter = Boolean(
		query.entityType || query.action || query.actorId !== undefined || query.from,
	);

	const reset = () =>
		onSearchChange({
			entityType: undefined,
			action: undefined,
			actorId: undefined,
			from: undefined,
			to: undefined,
		});

	return (
		<div className="space-y-4">
			<AuditToolbar hasFilter={hasFilter} onReset={reset}>
				<AuditFacetFilter
					title="Setting"
					options={ENTITY_TYPE_OPTIONS}
					selected={search.entityType ?? []}
					onChange={(values) => onSearchChange({ entityType: nonEmpty(values) })}
				/>
				<AuditFacetFilter
					title="Action"
					options={ACTION_OPTIONS}
					selected={search.action ?? []}
					onChange={(values) => onSearchChange({ action: nonEmpty(values) })}
				/>
				<AuditDateFacet
					value={dateRange}
					onChange={(range) => onSearchChange(fromDateRange(range))}
				/>
				{search.actorId !== undefined && (
					<AuditRefFilterPill
						label="Actor"
						id={search.actorId}
						name={nameForRef(entries, search.actorId)}
						onClear={() => onSearchChange({ actorId: undefined })}
					/>
				)}
			</AuditToolbar>

			{/* Mounted unconditionally and announced — see AuthAuditPanel. */}
			<p role="status" aria-live="polite" className="text-sm text-muted-foreground">
				{total === undefined
					? ""
					: `${total.toLocaleString()} ${total === 1 ? "change" : "changes"}${hasFilter ? " match the current filters" : ""}.`}
			</p>

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
