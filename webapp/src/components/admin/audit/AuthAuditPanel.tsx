import { useInfiniteQuery } from "@tanstack/react-query";
import { DownloadIcon } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import { adminListAuthEventsInfiniteOptions } from "@/api/@tanstack/react-query.gen";
import { adminExportAuthEvents } from "@/api/sdk.gen";
import type { AuthEventView } from "@/api/types.gen";
import { AdminAuditTable } from "@/components/admin/audit/AdminAuditTable";
import { type AuthEventType, EVENT_TYPE_LABELS } from "@/components/admin/audit/auditFormat";
import { AuditDateFacet } from "@/components/admin/audit-shared/AuditDateFacet";
import { AuditFacetFilter } from "@/components/admin/audit-shared/AuditFacetFilter";
import { AuditRefFilterPill } from "@/components/admin/audit-shared/AuditRefFilterPill";
import { AuditToolbar } from "@/components/admin/audit-shared/AuditToolbar";
import {
	type AuditSearch,
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
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";

const PAGE_SIZE = 50;

const EVENT_TYPE_OPTIONS = Object.entries(EVENT_TYPE_LABELS).map(([value, label]) => ({
	value,
	label,
}));

const OUTCOME_OPTIONS = [
	{ value: "SUCCESS", label: "Success" },
	{ value: "FAILURE", label: "Failure" },
];

const EVENT_TYPES = Object.keys(EVENT_TYPE_LABELS) as AuthEventType[];
const OUTCOMES: ("SUCCESS" | "FAILURE")[] = ["SUCCESS", "FAILURE"];

export interface AuthAuditPanelProps {
	search: AuditSearch;
	onSearchChange: (patch: Partial<AuditSearch>) => void;
	resolveWorkspaceName?: (id: number) => string | undefined;
}

/**
 * The sign-in and account trail: toolbar, CSV export, and table. Sibling of `ConfigAuditPanel` and
 * deliberately the same shape, so the two tabs of the audit log differ in content rather than in
 * how they are operated.
 */
export function AuthAuditPanel({
	search,
	onSearchChange,
	resolveWorkspaceName,
}: AuthAuditPanelProps) {
	const [exporting, setExporting] = useState(false);

	const dateRange = toDateRange(search);
	// One filter shape for the list and the export, so what you see is what you download.
	const filters = {
		eventType: narrowToEnum(search.eventType, EVENT_TYPES),
		result: narrowToEnum(search.outcome, OUTCOMES),
		accountId: search.accountId,
		actingAccountId: search.actorId,
		from: dateRange?.from ? dayStartIso(dateRange.from) : undefined,
		to: dateRange?.to ? dayEndIso(dateRange.to) : undefined,
	} as const;

	const listQuery = useInfiniteQuery({
		...adminListAuthEventsInfiniteOptions({ query: { size: PAGE_SIZE, ...filters } }),
		...springPageParams,
	});

	// Deduped: offset pages over an append-only log re-serve rows written between fetches.
	const events: AuthEventView[] = dedupeById(
		listQuery.data?.pages.flatMap((p) => p.content ?? []) ?? [],
	);
	const total = listQuery.data?.pages[0]?.totalElements;
	// From the NARROWED filter, not raw search: a stale link whose enum values no longer exist filters
	// nothing, so "Reset" and "match the current filters" would both be lying.
	const hasFilter = Boolean(
		filters.eventType ||
			filters.result ||
			filters.accountId !== undefined ||
			filters.actingAccountId !== undefined ||
			filters.from,
	);

	const reset = () =>
		onSearchChange({
			eventType: undefined,
			outcome: undefined,
			accountId: undefined,
			actorId: undefined,
			from: undefined,
			to: undefined,
		});

	const handleExport = async () => {
		setExporting(true);
		try {
			const { data, error } = await adminExportAuthEvents({ query: filters });
			if (error || typeof data !== "string") {
				throw new Error("export failed");
			}
			const blob = new Blob([data], { type: "text/csv;charset=utf-8;" });
			const url = URL.createObjectURL(blob);
			const anchor = document.createElement("a");
			anchor.href = url;
			anchor.download = `audit-log-${new Date().toISOString().slice(0, 10)}.csv`;
			document.body.appendChild(anchor);
			anchor.click();
			anchor.remove();
			URL.revokeObjectURL(url);
		} catch {
			toast.error("Could not export the audit log. Please try again.");
		} finally {
			setExporting(false);
		}
	};

	return (
		<div className="space-y-4">
			<AuditToolbar
				hasFilter={hasFilter}
				onReset={reset}
				actions={
					<Button
						variant="outline"
						size="sm"
						className="h-8"
						onClick={handleExport}
						disabled={exporting || events.length === 0}
					>
						{exporting ? <Spinner className="size-3.5" /> : <DownloadIcon aria-hidden />}
						Export CSV
					</Button>
				}
			>
				<AuditFacetFilter
					title="Event"
					options={EVENT_TYPE_OPTIONS}
					selected={search.eventType ?? []}
					onChange={(values) => onSearchChange({ eventType: nonEmpty(values) })}
				/>
				<AuditFacetFilter
					title="Result"
					options={OUTCOME_OPTIONS}
					selected={search.outcome ?? []}
					onChange={(values) => onSearchChange({ outcome: nonEmpty(values) })}
				/>
				<AuditDateFacet
					value={dateRange}
					onChange={(range) => onSearchChange(fromDateRange(range))}
				/>
				{search.accountId !== undefined && (
					<AuditRefFilterPill
						label="Account"
						id={search.accountId}
						name={nameForRef(events, search.accountId)}
						onClear={() => onSearchChange({ accountId: undefined })}
					/>
				)}
				{search.actorId !== undefined && (
					<AuditRefFilterPill
						label="Impersonated by"
						id={search.actorId}
						name={nameForRef(events, search.actorId)}
						onClear={() => onSearchChange({ actorId: undefined })}
					/>
				)}
			</AuditToolbar>

			{/* A persistent live region announces the count; the visible line is conditional, because an
			    empty <p> still collects the stack's margins and leaves a gap under the toolbar. */}
			<span role="status" aria-live="polite" className="sr-only">
				{total === undefined
					? ""
					: `${total.toLocaleString()} ${total === 1 ? "event" : "events"}${hasFilter ? " match your filters" : ""}.`}
			</span>
			{total !== undefined && (
				<p className="text-sm text-muted-foreground" aria-hidden>
					{total.toLocaleString()} {total === 1 ? "event" : "events"}
					{hasFilter ? " match your filters" : ""}.
				</p>
			)}

			<AdminAuditTable
				events={events}
				isLoading={listQuery.isLoading}
				isError={listQuery.isError}
				hasFilter={hasFilter}
				onResetFilters={reset}
				hasNextPage={Boolean(listQuery.hasNextPage)}
				isFetchingNextPage={listQuery.isFetchingNextPage}
				onLoadMore={() => listQuery.fetchNextPage()}
				onRetry={() => listQuery.refetch()}
				onFilterAccount={(accountId) => onSearchChange({ accountId })}
				onFilterActor={(actorId) => onSearchChange({ actorId })}
				resolveWorkspaceName={resolveWorkspaceName}
			/>
		</div>
	);
}
