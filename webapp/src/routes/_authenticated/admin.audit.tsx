import { useInfiniteQuery, useQuery } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { endOfDay, format, formatISO, startOfDay } from "date-fns";
import { CalendarIcon, Download, ScrollText, X } from "lucide-react";
import { useState } from "react";
import type { DateRange } from "react-day-picker";
import { toast } from "sonner";
import {
	adminListAuthEventsInfiniteOptions,
	adminListWorkspacesOptions,
} from "@/api/@tanstack/react-query.gen";
import { adminExportAuthEvents } from "@/api/sdk.gen";
import type { AuthEventView, PageAuthEventView } from "@/api/types.gen";
import { AdminAuditTable } from "@/components/admin/audit/AdminAuditTable";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Calendar } from "@/components/ui/calendar";
import { Label } from "@/components/ui/label";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import {
	Select,
	SelectContent,
	SelectItem,
	SelectTrigger,
	SelectValue,
} from "@/components/ui/select";
import { Spinner } from "@/components/ui/spinner";
import { cn } from "@/lib/utils";

const PAGE_SIZE = 50;
const ALL = "ALL";

// Mirrors AuthEvent.EventType server-side; kept explicit so the filter stays a typed union.
const EVENT_TYPES = [
	"LOGIN",
	"LOGIN_FAILED",
	"LOGOUT",
	"TOKEN_REFRESH",
	"JWT_REVOKED",
	"IDENTITY_LINKED",
	"IDENTITY_UNLINKED",
	"IMPERSONATION_BEGIN",
	"IMPERSONATION_END",
	"ACCOUNT_DELETED",
	"EXPORT_REQUESTED",
	"APP_ROLE_CHANGED",
] as const;

type EventTypeFilter = (typeof EVENT_TYPES)[number];
type ResultFilter = "SUCCESS" | "FAILURE";

export const Route = createFileRoute("/_authenticated/admin/audit")({
	component: AdminAuditPage,
});

// The generated client types `from`/`to` as `Date`, but its query serializer mangles a real Date into
// a deepObject on the wire; the server expects an ISO-8601 instant (@DateTimeFormat). So we build the
// instant string the server wants and cast to satisfy the generated type. The bounds are the picked
// day in the USER's LOCAL timezone (matching the "shown in your local timezone" copy and the
// the profile TimeframeFilter) — `formatISO` carries the local offset, so "Jun 2" means the user's
// local Jun 2, not UTC. `to` is end-of-day so the picked day is inclusive (predicate is `occurred_at < :to`).
function dayStartIso(date: Date): Date {
	return formatISO(startOfDay(date)) as unknown as Date;
}
function dayEndIso(date: Date): Date {
	return formatISO(endOfDay(date)) as unknown as Date;
}

// Compact label for the date-range trigger, mirroring the the profile TimeframeFilter.
function formatRangeLabel(range: DateRange | undefined): string {
	if (!range?.from) {
		return "Any date";
	}
	if (!range.to) {
		return `From ${format(range.from, "MMM d, yyyy")}`;
	}
	return `${format(range.from, "MMM d, yyyy")} – ${format(range.to, "MMM d, yyyy")}`;
}

function AdminAuditPage() {
	const [eventType, setEventType] = useState<EventTypeFilter | undefined>(undefined);
	const [result, setResult] = useState<ResultFilter | undefined>(undefined);
	const [dateRange, setDateRange] = useState<DateRange | undefined>(undefined);
	const [accountId, setAccountId] = useState<number | undefined>(undefined);
	const [actingAccountId, setActingAccountId] = useState<number | undefined>(undefined);
	const [exporting, setExporting] = useState(false);

	// Shared filter shape for the list query + the CSV export, so both honour the same selection.
	const filters = {
		eventType,
		result,
		from: dateRange?.from ? dayStartIso(dateRange.from) : undefined,
		to: dateRange?.to ? dayEndIso(dateRange.to) : undefined,
		accountId,
		actingAccountId,
	};

	// Workspace names are resolved client-side from the admin workspace list (dozens at most) — keeps
	// the audit service from reaching into the workspace module just to label a row.
	const workspacesQuery = useQuery(adminListWorkspacesOptions());
	const workspaceNames = new Map(
		(workspacesQuery.data ?? []).map((w) => [w.id, w.displayName || w.workspaceSlug] as const),
	);

	const listQuery = useInfiniteQuery({
		...adminListAuthEventsInfiniteOptions({ query: { size: PAGE_SIZE, ...filters } }),
		initialPageParam: 0,
		// The endpoint returns a Spring Page; advance by page number until the last page.
		getNextPageParam: (lastPage: PageAuthEventView) =>
			lastPage.last ? undefined : (lastPage.number ?? 0) + 1,
	});

	const events: AuthEventView[] = listQuery.data?.pages.flatMap((p) => p.content ?? []) ?? [];
	const total = listQuery.data?.pages[0]?.totalElements;
	const hasFilter =
		eventType !== undefined ||
		result !== undefined ||
		dateRange?.from !== undefined ||
		accountId !== undefined ||
		actingAccountId !== undefined;

	const clearAll = () => {
		setEventType(undefined);
		setResult(undefined);
		setDateRange(undefined);
		setAccountId(undefined);
		setActingAccountId(undefined);
	};

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
		<div className="mx-auto w-full max-w-6xl space-y-6 py-6">
			<header className="space-y-1">
				<div className="flex items-center gap-2">
					<ScrollText className="size-6 text-muted-foreground" aria-hidden />
					<h1 className="text-2xl font-semibold">Audit log</h1>
				</div>
				<p className="text-sm text-muted-foreground">
					Read-only record of authentication and admin events (logins, impersonation, role changes,
					deletions). Append-only — entries can't be edited or removed. Times are shown in your
					local timezone (hover for the exact UTC instant).
				</p>
			</header>

			<div className="flex flex-wrap items-end gap-3">
				<div className="flex w-full flex-col gap-1.5 sm:w-48">
					<Label htmlFor="audit-event-type" className="text-sm">
						Event type
					</Label>
					<Select
						value={eventType ?? ALL}
						onValueChange={(value) =>
							setEventType(value === ALL ? undefined : (value as EventTypeFilter))
						}
					>
						<SelectTrigger id="audit-event-type">
							<SelectValue placeholder="All events" />
						</SelectTrigger>
						<SelectContent>
							<SelectItem value={ALL}>All events</SelectItem>
							{EVENT_TYPES.map((type) => (
								<SelectItem key={type} value={type}>
									{type}
								</SelectItem>
							))}
						</SelectContent>
					</Select>
				</div>

				<div className="flex w-full flex-col gap-1.5 sm:w-40">
					<Label htmlFor="audit-result" className="text-sm">
						Outcome
					</Label>
					<Select
						value={result ?? ALL}
						onValueChange={(value) =>
							setResult(value === ALL ? undefined : (value as ResultFilter))
						}
					>
						<SelectTrigger id="audit-result">
							<SelectValue placeholder="All outcomes" />
						</SelectTrigger>
						<SelectContent>
							<SelectItem value={ALL}>All outcomes</SelectItem>
							<SelectItem value="SUCCESS">Success</SelectItem>
							<SelectItem value="FAILURE">Failure</SelectItem>
						</SelectContent>
					</Select>
				</div>

				<div className="flex flex-col gap-1.5">
					<Label htmlFor="audit-date-range" className="text-sm">
						Date range
					</Label>
					<Popover>
						<PopoverTrigger
							render={
								<Button
									id="audit-date-range"
									variant="outline"
									className={cn(
										"w-64 justify-start text-left font-normal",
										!dateRange?.from && "text-muted-foreground",
									)}
								>
									<CalendarIcon className="mr-2 size-4" aria-hidden />
									{formatRangeLabel(dateRange)}
								</Button>
							}
						/>
						<PopoverContent className="w-auto p-0" align="start">
							<Calendar
								autoFocus
								mode="range"
								defaultMonth={dateRange?.from}
								selected={dateRange}
								onSelect={setDateRange}
								numberOfMonths={2}
							/>
						</PopoverContent>
					</Popover>
				</div>

				{hasFilter && (
					<Button variant="ghost" onClick={clearAll} className="mb-0.5">
						Clear filters
					</Button>
				)}

				<Button
					variant="outline"
					onClick={handleExport}
					disabled={exporting || events.length === 0}
					className="mb-0.5 ml-auto"
				>
					{exporting ? (
						<Spinner className="mr-2 size-3.5" />
					) : (
						<Download className="size-4" aria-hidden />
					)}
					Export CSV
				</Button>
			</div>

			{(accountId !== undefined || actingAccountId !== undefined) && (
				<div className="flex flex-wrap items-center gap-2">
					{accountId !== undefined && (
						<Badge variant="secondary" className="gap-1">
							Account #{accountId}
							<button
								type="button"
								aria-label="Clear account filter"
								onClick={() => setAccountId(undefined)}
								className="rounded-sm outline-none focus-visible:ring-2 focus-visible:ring-ring"
							>
								<X className="size-3" aria-hidden />
							</button>
						</Badge>
					)}
					{actingAccountId !== undefined && (
						<Badge variant="secondary" className="gap-1">
							Actor #{actingAccountId}
							<button
								type="button"
								aria-label="Clear actor filter"
								onClick={() => setActingAccountId(undefined)}
								className="rounded-sm outline-none focus-visible:ring-2 focus-visible:ring-ring"
							>
								<X className="size-3" aria-hidden />
							</button>
						</Badge>
					)}
				</div>
			)}

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
				onFilterAccount={setAccountId}
				onFilterActor={setActingAccountId}
				resolveWorkspaceName={(id) => workspaceNames.get(id)}
			/>
		</div>
	);
}
