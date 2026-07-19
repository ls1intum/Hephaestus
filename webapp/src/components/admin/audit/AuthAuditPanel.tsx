import { useInfiniteQuery } from "@tanstack/react-query";
import { DownloadIcon } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import { adminListAuthEventsInfiniteOptions } from "@/api/@tanstack/react-query.gen";
import { adminExportAuthEvents } from "@/api/sdk.gen";
import type { AuthEventView, PageAuthEventView } from "@/api/types.gen";
import { AdminAuditTable } from "@/components/admin/audit/AdminAuditTable";
import { AuditDateFacet } from "@/components/admin/audit-shared/AuditDateFacet";
import { AuditFacetFilter } from "@/components/admin/audit-shared/AuditFacetFilter";
import { AuditToolbar } from "@/components/admin/audit-shared/AuditToolbar";
import {
	type AuditSearch,
	fromDateRange,
	narrowToEnum,
	nonEmpty,
	toDateRange,
} from "@/components/admin/audit-shared/auditSearch";
import { dayEndIso, dayStartIso } from "@/components/admin/audit-shared/dateFilter";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";

const PAGE_SIZE = 50;

// Mirrors AuthEvent.EventType server-side. Labelled rather than raw so the facet reads as English;
// the value is still the wire enum.
const EVENT_TYPE_OPTIONS = [
	{ value: "LOGIN", label: "Sign-in" },
	{ value: "LOGIN_FAILED", label: "Failed sign-in" },
	{ value: "LOGOUT", label: "Sign-out" },
	{ value: "TOKEN_REFRESH", label: "Token refresh" },
	{ value: "JWT_REVOKED", label: "Sessions revoked" },
	{ value: "IDENTITY_LINKED", label: "Identity linked" },
	{ value: "IDENTITY_UNLINKED", label: "Identity unlinked" },
	{ value: "IMPERSONATION_BEGIN", label: "Impersonation started" },
	{ value: "IMPERSONATION_END", label: "Impersonation ended" },
	{ value: "ACCOUNT_DELETED", label: "Account deleted" },
	{ value: "EXPORT_REQUESTED", label: "Data export requested" },
	{ value: "APP_ROLE_CHANGED", label: "Instance role changed" },
	{ value: "RESEARCH_CONSENT_REVOKED", label: "Research consent revoked" },
] as const;

const OUTCOME_OPTIONS = [
	{ value: "SUCCESS", label: "Success" },
	{ value: "FAILURE", label: "Failure" },
] as const;

const EVENT_TYPES = EVENT_TYPE_OPTIONS.map((o) => o.value);
const OUTCOMES = OUTCOME_OPTIONS.map((o) => o.value);

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
		initialPageParam: 0,
		getNextPageParam: (lastPage: PageAuthEventView) =>
			lastPage.last ? undefined : (lastPage.number ?? 0) + 1,
	});

	const events: AuthEventView[] = listQuery.data?.pages.flatMap((p) => p.content ?? []) ?? [];
	const total = listQuery.data?.pages[0]?.totalElements;
	const hasFilter = Boolean(
		search.eventType?.length ||
			search.outcome?.length ||
			search.accountId !== undefined ||
			search.actorId !== undefined ||
			search.from,
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
					options={[...EVENT_TYPE_OPTIONS]}
					selected={search.eventType ?? []}
					onChange={(values) => onSearchChange({ eventType: nonEmpty(values) })}
				/>
				<AuditFacetFilter
					title="Outcome"
					options={[...OUTCOME_OPTIONS]}
					selected={search.outcome ?? []}
					onChange={(values) => onSearchChange({ outcome: nonEmpty(values) })}
				/>
				<AuditDateFacet
					value={dateRange}
					onChange={(range) => onSearchChange(fromDateRange(range))}
				/>
				{search.accountId !== undefined && (
					<Button
						variant="secondary"
						size="sm"
						className="h-8"
						onClick={() => onSearchChange({ accountId: undefined })}
					>
						Account #{search.accountId}
						<Badge variant="outline" className="rounded-sm px-1 font-normal">
							Clear
						</Badge>
					</Button>
				)}
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
					{total.toLocaleString()} event{total === 1 ? "" : "s"}
					{hasFilter ? " match the current filters" : ""}.
				</p>
			)}

			<AdminAuditTable
				events={events}
				isLoading={listQuery.isLoading}
				isError={listQuery.isError}
				hasFilter={hasFilter}
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
