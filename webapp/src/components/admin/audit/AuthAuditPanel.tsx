import { useInfiniteQuery } from "@tanstack/react-query";
import { DownloadIcon } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import { adminListAuthEventsInfiniteOptions } from "@/api/@tanstack/react-query.gen";
import { adminExportAuthEvents } from "@/api/sdk.gen";
import type { AuthEventView } from "@/api/types.gen";
import { AdminAuditTable } from "@/components/admin/audit/AdminAuditTable";
import { type AuthEventType, EVENT_TYPE_LABELS } from "@/components/admin/audit/audit-format";
import {
	type AuditSearch,
	dayEndIso,
	dayStartIso,
	fromDateRange,
	toDateRange,
} from "@/components/admin/audit-shared/audit-search";
import { nameForRef } from "@/components/admin/audit-shared/name-for-ref";
import { DateRangeFacet } from "@/components/common/DateRangeFacet";
import { FacetMultiSelect, toFacetOptions } from "@/components/common/FacetMultiSelect";
import { FilterToolbar } from "@/components/common/FilterToolbar";
import { ReferenceFilterPill } from "@/components/common/ReferenceFilterPill";
import { ResultCount } from "@/components/common/ResultCount";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { springPageParams } from "@/integrations/tanstack-query/spring-page";
import { dedupeById } from "@/lib/dedupe-by-id";
import { narrowToEnum, nonEmpty } from "@/lib/search-params";

const PAGE_SIZE = 50;

const EVENT_TYPE_OPTIONS = toFacetOptions(EVENT_TYPE_LABELS);

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

	const events: AuthEventView[] = dedupeById(
		listQuery.data?.pages.flatMap((p) => p.content ?? []) ?? [],
	);
	const total = listQuery.data?.pages[0]?.totalElements;
	// From the narrowed filter, not raw search: unrecognised enum values filter nothing, so they must
	// not count as an active filter.
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
			<FilterToolbar
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
				<FacetMultiSelect
					title="Event"
					options={EVENT_TYPE_OPTIONS}
					selected={search.eventType ?? []}
					onChange={(values) => onSearchChange({ eventType: nonEmpty(values) })}
				/>
				<FacetMultiSelect
					title="Result"
					options={OUTCOME_OPTIONS}
					selected={search.outcome ?? []}
					onChange={(values) => onSearchChange({ outcome: nonEmpty(values) })}
				/>
				<DateRangeFacet
					value={dateRange}
					onChange={(range) => onSearchChange(fromDateRange(range))}
				/>
				{search.accountId !== undefined && (
					<ReferenceFilterPill
						label="Account"
						id={search.accountId}
						name={nameForRef(events, search.accountId)}
						onClear={() => onSearchChange({ accountId: undefined })}
					/>
				)}
				{search.actorId !== undefined && (
					<ReferenceFilterPill
						label="Impersonated by"
						id={search.actorId}
						name={nameForRef(events, search.actorId)}
						onClear={() => onSearchChange({ actorId: undefined })}
					/>
				)}
			</FilterToolbar>

			<ResultCount total={total} noun={["event", "events"]} hasFilter={hasFilter} />

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
