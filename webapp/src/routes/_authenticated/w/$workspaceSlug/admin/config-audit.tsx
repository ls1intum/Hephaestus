import { useInfiniteQuery } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { endOfDay, format, formatISO, startOfDay } from "date-fns";
import { CalendarIcon, History } from "lucide-react";
import { useState } from "react";
import type { DateRange } from "react-day-picker";
import { listWorkspaceConfigAuditEventsInfiniteOptions } from "@/api/@tanstack/react-query.gen";
import type { ConfigAuditEntryView, PageConfigAuditEntryView } from "@/api/types.gen";
import { ConfigAuditTable } from "@/components/admin/config-audit/ConfigAuditTable";
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
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";
import { cn } from "@/lib/utils";

const PAGE_SIZE = 50;
const ALL = "ALL";

const ENTITY_TYPES = [
	{ value: "PRACTICE_REVIEW_SETTINGS", label: "Review settings" },
	{ value: "AI_CONFIG_BINDING", label: "AI binding" },
	{ value: "AGENT_CONFIG", label: "Agent config" },
] as const;
const ACTIONS = [
	{ value: "CREATED", label: "Created" },
	{ value: "UPDATED", label: "Updated" },
	{ value: "DELETED", label: "Deleted" },
] as const;

type EntityTypeFilter = (typeof ENTITY_TYPES)[number]["value"];
type ActionFilter = (typeof ACTIONS)[number]["value"];

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/config-audit")({
	component: WorkspaceConfigAuditPage,
});

// See the instance-admin page for the Date→ISO serializer note; the bounds are the picked local day.
function dayStartIso(date: Date): Date {
	return formatISO(startOfDay(date)) as unknown as Date;
}
function dayEndIso(date: Date): Date {
	return formatISO(endOfDay(date)) as unknown as Date;
}

function formatRangeLabel(range: DateRange | undefined): string {
	if (!range?.from) return "Any date";
	if (!range.to) return `From ${format(range.from, "MMM d, yyyy")}`;
	return `${format(range.from, "MMM d, yyyy")} – ${format(range.to, "MMM d, yyyy")}`;
}

function WorkspaceConfigAuditPage() {
	const { workspaceSlug } = useActiveWorkspaceSlug();
	const [entityType, setEntityType] = useState<EntityTypeFilter | undefined>(undefined);
	const [action, setAction] = useState<ActionFilter | undefined>(undefined);
	const [dateRange, setDateRange] = useState<DateRange | undefined>(undefined);

	const filters = {
		entityType,
		action,
		from: dateRange?.from ? dayStartIso(dateRange.from) : undefined,
		to: dateRange?.to ? dayEndIso(dateRange.to) : undefined,
	};

	const listQuery = useInfiniteQuery({
		...listWorkspaceConfigAuditEventsInfiniteOptions({
			path: { workspaceSlug: workspaceSlug ?? "" },
			query: { size: PAGE_SIZE, ...filters },
		}),
		initialPageParam: 0,
		getNextPageParam: (lastPage: PageConfigAuditEntryView) =>
			lastPage.last ? undefined : (lastPage.number ?? 0) + 1,
		enabled: Boolean(workspaceSlug),
	});

	const entries: ConfigAuditEntryView[] =
		listQuery.data?.pages.flatMap((p) => p.content ?? []) ?? [];
	const total = listQuery.data?.pages[0]?.totalElements;
	const hasFilter =
		entityType !== undefined || action !== undefined || dateRange?.from !== undefined;

	const clearAll = () => {
		setEntityType(undefined);
		setAction(undefined);
		setDateRange(undefined);
	};

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

			<div className="flex flex-wrap items-end gap-3">
				<div className="flex w-full flex-col gap-1.5 sm:w-48">
					<Label htmlFor="ws-config-audit-entity-type" className="text-sm">
						Resource
					</Label>
					<Select
						value={entityType ?? ALL}
						onValueChange={(value) =>
							setEntityType(value === ALL ? undefined : (value as EntityTypeFilter))
						}
					>
						<SelectTrigger id="ws-config-audit-entity-type">
							<SelectValue placeholder="All resources" />
						</SelectTrigger>
						<SelectContent>
							<SelectItem value={ALL}>All resources</SelectItem>
							{ENTITY_TYPES.map((t) => (
								<SelectItem key={t.value} value={t.value}>
									{t.label}
								</SelectItem>
							))}
						</SelectContent>
					</Select>
				</div>

				<div className="flex w-full flex-col gap-1.5 sm:w-40">
					<Label htmlFor="ws-config-audit-action" className="text-sm">
						Action
					</Label>
					<Select
						value={action ?? ALL}
						onValueChange={(value) =>
							setAction(value === ALL ? undefined : (value as ActionFilter))
						}
					>
						<SelectTrigger id="ws-config-audit-action">
							<SelectValue placeholder="All actions" />
						</SelectTrigger>
						<SelectContent>
							<SelectItem value={ALL}>All actions</SelectItem>
							{ACTIONS.map((a) => (
								<SelectItem key={a.value} value={a.value}>
									{a.label}
								</SelectItem>
							))}
						</SelectContent>
					</Select>
				</div>

				<div className="flex flex-col gap-1.5">
					<Label htmlFor="ws-config-audit-date-range" className="text-sm">
						Date range
					</Label>
					<Popover>
						<PopoverTrigger
							render={
								<Button
									id="ws-config-audit-date-range"
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
			</div>

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
