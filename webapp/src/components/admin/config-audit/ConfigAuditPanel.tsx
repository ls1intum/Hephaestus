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
} from "@/components/admin/audit-shared/audit-search";
import { dedupeById } from "@/components/admin/audit-shared/dedupe-by-id";
import { nameForRef } from "@/components/admin/audit-shared/name-for-ref";
import { springPageParams } from "@/components/admin/audit-shared/spring-page";
import { ConfigAuditTable } from "@/components/admin/config-audit/ConfigAuditTable";
import {
	ACTION_LABELS,
	ENTITY_TYPE_LABELS,
} from "@/components/admin/config-audit/config-audit-format";
import { FacetMultiSelect, type FacetOption } from "@/components/common/FacetMultiSelect";

const PAGE_SIZE = 50;

type EntityType = NonNullable<ConfigAuditEntryView["entityType"]>;
type Action = NonNullable<ConfigAuditEntryView["action"]>;

const ENTITY_TYPE_OPTIONS: FacetOption[] = Object.entries(ENTITY_TYPE_LABELS).map(
	([value, label]) => ({ value, label }),
);
const ACTION_OPTIONS: FacetOption[] = Object.entries(ACTION_LABELS).map(([value, label]) => ({
	value,
	label,
}));
const ENTITY_TYPES = Object.keys(ENTITY_TYPE_LABELS) as EntityType[];
const ACTIONS = Object.keys(ACTION_LABELS) as Action[];

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

export function WorkspaceConfigAuditPanel({
	workspaceSlug,
	search,
	onSearchChange,
}: Omit<ConfigAuditPanelProps, "resolveWorkspaceName"> & { workspaceSlug: string }) {
	const listQuery = useInfiniteQuery({
		...listWorkspaceConfigAuditEventsInfiniteOptions({
			path: { workspaceSlug },
			query: toQuery(search),
		}),
		...springPageParams,
	});

	return <ConfigAuditView search={search} onSearchChange={onSearchChange} listQuery={listQuery} />;
}

type ConfigAuditListQuery = UseInfiniteQueryResult<InfiniteData<PageConfigAuditEntryView>, Error>;

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
	const entries: ConfigAuditEntryView[] = dedupeById(
		listQuery.data?.pages.flatMap((p) => p.content ?? []) ?? [],
	);
	const total = listQuery.data?.pages[0]?.totalElements;
	// From the narrowed query, not raw search: unrecognised enum values filter nothing, so they must
	// not count as an active filter.
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
				<FacetMultiSelect
					title="Setting"
					options={ENTITY_TYPE_OPTIONS}
					selected={search.entityType ?? []}
					onChange={(values) => onSearchChange({ entityType: nonEmpty(values) })}
				/>
				<FacetMultiSelect
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

			{/* The live region is always mounted, so a count that arrives later is announced. */}
			<span role="status" aria-live="polite" className="sr-only">
				{total === undefined
					? ""
					: `${total.toLocaleString()} ${total === 1 ? "change" : "changes"}${hasFilter ? " match your filters" : ""}.`}
			</span>
			{total !== undefined && (
				<p className="text-sm text-muted-foreground" aria-hidden>
					{total.toLocaleString()} {total === 1 ? "change" : "changes"}
					{hasFilter ? " match your filters" : ""}.
				</p>
			)}

			<ConfigAuditTable
				entries={entries}
				isLoading={listQuery.isLoading}
				isError={listQuery.isError}
				hasFilter={hasFilter}
				onResetFilters={reset}
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
