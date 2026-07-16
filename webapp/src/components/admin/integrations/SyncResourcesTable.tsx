import { format } from "date-fns";
import { AlertCircleIcon, ChevronDownIcon, DatabaseIcon } from "lucide-react";
import type { SyncResourceCount, SyncResourceState } from "@/api/types.gen";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import {
	Empty,
	EmptyDescription,
	EmptyHeader,
	EmptyMedia,
	EmptyTitle,
} from "@/components/ui/empty";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Progress } from "@/components/ui/progress";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import {
	asDate,
	FRESHNESS_CLASS,
	formatCountBreakdown,
	freshnessTone,
	relativeTime,
	stateLabel,
} from "./sync-format";

const COLUMN_COUNT = 6;

/**
 * The header, shared verbatim by the loading and loaded states, so the `<thead>` doesn't appear out of
 * nowhere on resolve.
 */
function ResourcesTableHeader() {
	return (
		<TableHeader>
			<TableRow>
				<TableHead>Resource</TableHead>
				<TableHead>State</TableHead>
				<TableHead>Freshness</TableHead>
				<TableHead className="text-right">Items</TableHead>
				<TableHead>Synced through</TableHead>
				<TableHead className="w-0 text-right">
					<span className="sr-only">Error</span>
				</TableHead>
			</TableRow>
		</TableHeader>
	);
}

/**
 * The backfill horizon, folded in from what used to be its own column. Only GitHub ever supplies
 * `backfillPercent`, so a dedicated column rendered a dash for three of the four integrations; here
 * the bar appears only while a backfill is genuinely mid-flight, and a finished one states its horizon
 * as a date instead of a permanent 100%.
 */
function SyncedThrough({ resource }: { resource: SyncResourceState }) {
	const completedThrough = asDate(resource.backfillCompletedThrough);
	const percent = resource.backfillPercent;
	const isRunning = percent != null && percent < 100;

	if (isRunning) {
		return (
			<div className="flex items-center gap-2">
				<Progress
					value={percent}
					className="w-20"
					aria-label={`Backfill progress for ${resource.name}`}
				/>
				<span className="text-muted-foreground text-xs tabular-nums">{percent}%</span>
			</div>
		);
	}

	if (completedThrough) {
		return (
			<span className="text-muted-foreground" title={completedThrough.toISOString()}>
				{format(completedThrough, "d MMM yyyy")}
			</span>
		);
	}

	return <span className="text-muted-foreground">–</span>;
}

/**
 * One entity class inside the expanded row. `lastSyncedAt` is deliberately three-valued: a timestamp,
 * or "not tracked" when the integration keeps no per-class watermark — which is *not* the same claim
 * as "never synced" and must never render as one.
 */
function ResourceCountRow({
	count,
	syncIntervalSeconds,
}: {
	count: SyncResourceCount;
	syncIntervalSeconds?: number;
}) {
	const tone = freshnessTone(count.lastSyncedAt, syncIntervalSeconds);
	return (
		<div className="grid grid-cols-[1fr_auto_10rem] items-baseline gap-4 py-1">
			<span className="text-muted-foreground">{count.label}</span>
			<span className="text-right tabular-nums">{count.count.toLocaleString()}</span>
			{count.lastSyncedAt ? (
				<span className={FRESHNESS_CLASS[tone]}>{relativeTime(count.lastSyncedAt)}</span>
			) : (
				<span className="text-muted-foreground italic">not tracked</span>
			)}
		</div>
	);
}

/** The class breakdown an expanded row reveals: one line per class the integration mirrors. */
function ResourceCountsPanel({
	counts,
	syncIntervalSeconds,
}: {
	counts: SyncResourceCount[];
	syncIntervalSeconds?: number;
}) {
	return (
		<div className="bg-muted/30 px-2 py-2 text-sm">
			<div className="grid grid-cols-[1fr_auto_10rem] gap-4 pb-1 font-medium text-muted-foreground text-xs uppercase tracking-wide">
				<span>Class</span>
				<span className="text-right">Items</span>
				<span>Last synced</span>
			</div>
			{counts.map((count) => (
				<ResourceCountRow key={count.key} count={count} syncIntervalSeconds={syncIntervalSeconds} />
			))}
		</div>
	);
}

/** Name over external id — both upstream-controlled, so both truncate with the full value in `title`. */
function ResourceName({ resource }: { resource: SyncResourceState }) {
	return (
		<div className="min-w-0">
			<div className="max-w-[28ch] truncate font-medium" title={resource.name}>
				{resource.name}
			</div>
			<div
				className="max-w-[28ch] truncate text-muted-foreground font-mono text-xs"
				title={resource.externalId}
			>
				{resource.externalId}
			</div>
		</div>
	);
}

function ResourceErrorCell({ resource }: { resource: SyncResourceState }) {
	return (
		<TableCell className="text-right">
			{resource.lastError && (
				<Popover>
					<PopoverTrigger
						render={
							<Button variant="ghost" size="icon-sm" aria-label={`Error for ${resource.name}`}>
								<AlertCircleIcon className="size-4 text-destructive" />
							</Button>
						}
					/>
					<PopoverContent>{resource.lastError}</PopoverContent>
				</Popover>
			)}
		</TableCell>
	);
}

/**
 * The cells shared by the expandable and inline variants. `leading` is the chevron slot: an expandable
 * row puts its trigger there, a single-class row passes nothing so the column still lines up.
 */
function ResourceCells({
	resource,
	syncIntervalSeconds,
	leading,
}: {
	resource: SyncResourceState;
	syncIntervalSeconds?: number;
	leading?: React.ReactNode;
}) {
	const tone = freshnessTone(resource.lastSyncedAt, syncIntervalSeconds);
	const breakdown = formatCountBreakdown(resource.counts);

	return (
		<>
			<TableCell>
				<div className="flex items-start gap-1">
					{/* A fixed slot whether or not a chevron lands in it, so names stay on one left edge. */}
					<div className="w-6 shrink-0 pt-0.5">{leading}</div>
					<ResourceName resource={resource} />
				</div>
			</TableCell>
			<TableCell>
				<Badge variant="outline">{stateLabel(resource.state)}</Badge>
			</TableCell>
			<TableCell className={FRESHNESS_CLASS[tone]}>
				{tone === "never" ? "Never synced" : relativeTime(resource.lastSyncedAt)}
			</TableCell>
			<TableCell className="text-right">
				<div className="tabular-nums">
					{resource.itemCount != null ? resource.itemCount.toLocaleString() : "–"}
				</div>
				{breakdown && (
					<div
						className="ml-auto max-w-[36ch] truncate text-muted-foreground text-xs tabular-nums"
						title={breakdown}
					>
						{breakdown}
					</div>
				)}
			</TableCell>
			<TableCell>
				<SyncedThrough resource={resource} />
			</TableCell>
			<ResourceErrorCell resource={resource} />
		</>
	);
}

/**
 * A resource whose breakdown is worth expanding — an SCM repository mirrors six classes, and the
 * per-class watermarks are the only place "pull requests still sync but comments stopped" is visible.
 *
 * `Collapsible` renders as a `<tbody>` and its panel as a `<tr>`: a disclosure needs its trigger and
 * panel under one root, and the only element that can legally wrap two `<tr>`s is a `<tbody>` (a table
 * may hold many). That keeps the expansion inside the real table so its columns stay aligned with the
 * header, instead of hand-rolling `useState` + a manually toggled row and re-implementing the ARIA
 * wiring the primitive already ships.
 */
function ExpandableResourceRow({
	resource,
	syncIntervalSeconds,
}: {
	resource: SyncResourceState;
	syncIntervalSeconds?: number;
}) {
	return (
		<Collapsible render={<tbody />}>
			<TableRow>
				<ResourceCells
					resource={resource}
					syncIntervalSeconds={syncIntervalSeconds}
					leading={
						<CollapsibleTrigger
							className="group"
							render={
								<Button
									variant="ghost"
									size="icon-sm"
									aria-label={`Show item breakdown for ${resource.name}`}
								>
									<ChevronDownIcon className="size-4 transition-transform group-data-[panel-open]:rotate-180" />
								</Button>
							}
						/>
					}
				/>
			</TableRow>
			<CollapsibleContent render={<tr />}>
				<td colSpan={COLUMN_COUNT} className="border-b p-0">
					<ResourceCountsPanel counts={resource.counts} syncIntervalSeconds={syncIntervalSeconds} />
				</td>
			</CollapsibleContent>
		</Collapsible>
	);
}

export interface SyncResourcesTableProps {
	resources: SyncResourceState[];
	isLoading?: boolean;
	isError?: boolean;
	error?: unknown;
	onRetry?: () => void;
	resourceNoun: string;
	resourceNounPlural: string;
	/**
	 * The connection's reconciliation cadence, from `ConnectionSyncStatus.syncIntervalSeconds`. Omit it
	 * and freshness is printed but never judged — an age is only stale relative to a schedule, and the
	 * server sends `null` rather than guess one for an irregular cron.
	 */
	syncIntervalSeconds?: number;
}

export function SyncResourcesTable({
	resources,
	isLoading = false,
	isError = false,
	error,
	onRetry,
	resourceNoun,
	resourceNounPlural,
	syncIntervalSeconds,
}: SyncResourcesTableProps) {
	if (isError) {
		return (
			<QueryErrorAlert
				error={error}
				title={`We couldn't load the ${resourceNoun} sync state`}
				onRetry={onRetry}
			/>
		);
	}

	if (isLoading) {
		return (
			<Table>
				<ResourcesTableHeader />
				<tbody>
					{Array.from({ length: 5 }, (_, rowIndex) => (
						<TableRow key={rowIndex}>
							{/* Name is two stacked lines in the loaded row (name over external id), so the
							    placeholder is too — one grey bar here would make every row shrink on resolve. */}
							<TableCell>
								<Skeleton className="h-4 w-40" />
								<Skeleton className="mt-1 h-3 w-24" />
							</TableCell>
							<TableCell>
								<Skeleton className="h-5 w-16 rounded-full" />
							</TableCell>
							<TableCell>
								<Skeleton className="h-4 w-24" />
							</TableCell>
							{/* Items is two lines once loaded too: the headline total over the class breakdown. */}
							<TableCell>
								<Skeleton className="ml-auto h-4 w-14" />
								<Skeleton className="mt-1 ml-auto h-3 w-32" />
							</TableCell>
							<TableCell>
								<Skeleton className="h-4 w-28" />
							</TableCell>
							<TableCell />
						</TableRow>
					))}
				</tbody>
			</Table>
		);
	}

	if (resources.length === 0) {
		return (
			<Empty className="border border-dashed">
				<EmptyHeader>
					<EmptyMedia variant="icon">
						<DatabaseIcon />
					</EmptyMedia>
					<EmptyTitle>No {resourceNounPlural} synced yet</EmptyTitle>
					<EmptyDescription>
						Synced {resourceNounPlural} and their state appear here once a sync job runs.
					</EmptyDescription>
				</EmptyHeader>
			</Empty>
		);
	}

	return (
		<Table>
			<ResourcesTableHeader />
			{resources.map((resource) =>
				/* One class (a Slack channel, an Outline collection) is already fully told by the summary
				   line — a chevron there would open a one-row panel restating it, so those rows stay flat.
				   A never-synced resource has no classes at all and likewise has nothing to expand. */
				resource.counts.length > 1 ? (
					<ExpandableResourceRow
						key={resource.id}
						resource={resource}
						syncIntervalSeconds={syncIntervalSeconds}
					/>
				) : (
					<tbody key={resource.id}>
						<TableRow>
							<ResourceCells resource={resource} syncIntervalSeconds={syncIntervalSeconds} />
						</TableRow>
					</tbody>
				),
			)}
		</Table>
	);
}
