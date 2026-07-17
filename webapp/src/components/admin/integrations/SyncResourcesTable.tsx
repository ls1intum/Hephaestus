import { format } from "date-fns";
import { AlertCircleIcon, DatabaseIcon } from "lucide-react";
import type { SyncResourceCount, SyncResourceState } from "@/api/types.gen";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import {
	Empty,
	EmptyDescription,
	EmptyHeader,
	EmptyMedia,
	EmptyTitle,
} from "@/components/ui/empty";
import { HoverCard, HoverCardContent, HoverCardTrigger } from "@/components/ui/hover-card";
import { Progress } from "@/components/ui/progress";
import {
	Table,
	TableBody,
	TableCell,
	TableHead,
	TableHeader,
	TableRow,
} from "@/components/ui/table";
import { cn } from "@/lib/utils";
import { RelativeTime } from "./RelativeTime";
import { asDate, freshnessTone, stateLabel } from "./sync-format";
import { TableRowsSkeleton } from "./TableRowsSkeleton";

type ClassKey = SyncResourceCount["key"];

interface ClassColumn {
	key: string;
	label: string;
	/**
	 * The wire classes this column reports. More than one only where the split is an implementation
	 * detail of the mirror rather than a distinction an admin acts on.
	 */
	sourceKeys: ClassKey[];
}

/**
 * The matrix's columns, in reading order, and the fold from wire classes to them.
 *
 * Issue comments and review comments are one "Comments" column because no admin has ever needed to
 * know that one lags and the other doesn't — they are the same pipeline in the same repository, and
 * six data columns is what fits at `max-w-5xl`. The column reports the OLDER of the two watermarks,
 * which is the only merge that cannot hide a stalled half; both are still spelled out in the hover.
 */
const CLASS_COLUMNS: ClassColumn[] = [
	{ key: "issues", label: "Issues", sourceKeys: ["issues"] },
	{ key: "pullRequests", label: "PRs", sourceKeys: ["pullRequests"] },
	{ key: "reviews", label: "Reviews", sourceKeys: ["reviews"] },
	{ key: "comments", label: "Comments", sourceKeys: ["issueComments", "reviewComments"] },
	{ key: "commits", label: "Commits", sourceKeys: ["commits"] },
	{ key: "messages", label: "Messages", sourceKeys: ["messages"] },
	{ key: "documents", label: "Documents", sourceKeys: ["documents"] },
];

/** The classes an SCM repository mirrors — see {@link SyncResourcesTableProps.expectedClassKeys}. */
export const SCM_CLASS_KEYS: ClassKey[] = [
	"issues",
	"pullRequests",
	"reviews",
	"issueComments",
	"reviewComments",
	"commits",
];

function columnsFor(classKeys: Iterable<ClassKey>): ClassColumn[] {
	const present = new Set(classKeys);
	return CLASS_COLUMNS.filter((column) => column.sourceKeys.some((key) => present.has(key)));
}

function classKeysOf(resources: SyncResourceState[]): ClassKey[] {
	return resources.flatMap((resource) => resource.counts.map((count) => count.key));
}

/** The counts a column reports for one resource, in the column's own class order. */
function membersOf(resource: SyncResourceState, column: ClassColumn): SyncResourceCount[] {
	return column.sourceKeys.flatMap((key) => resource.counts.filter((count) => count.key === key));
}

/** The oldest watermark among the members — the freshness the column can honestly claim. */
function oldestWatermark(members: SyncResourceCount[]): Date | undefined {
	const dates = members
		.map((member) => asDate(member.lastSyncedAt))
		.filter((date): date is Date => date != null);
	if (dates.length === 0) return undefined;
	return dates.reduce((oldest, date) => (date < oldest ? date : oldest));
}

function absolute(date: Date): string {
	return format(date, "d MMM yyyy, HH:mm:ss");
}

/**
 * One class's freshness for one resource: a relative time, tinted by the judgement, or a dash when
 * there is no watermark to judge.
 *
 * The hover carries what the cell cannot: the count behind the reading, the exact instant, and — the
 * distinction the whole column set exists to preserve — whether a missing timestamp means "this class
 * has never synced" or "this integration keeps no watermark for it". Those are different claims and a
 * dash must never be read as the first one.
 */
function ClassCell({
	resource,
	column,
	resourceNoun,
	syncIntervalSeconds,
}: {
	resource: SyncResourceState;
	column: ClassColumn;
	resourceNoun: string;
	syncIntervalSeconds?: number;
}) {
	const members = membersOf(resource, column);
	const watermark = oldestWatermark(members);
	const total = members.reduce((sum, member) => sum + member.count, 0);
	const tone = freshnessTone(watermark, syncIntervalSeconds);

	return (
		<TableCell>
			<HoverCard>
				{/* A real button, so the hover's contents are reachable by keyboard too — the counts and the
				    not-tracked distinction live nowhere else on the page. `tooltip={false}` because this
				    hover already states the absolute time; two popups for one cell is worse than one. */}
				<HoverCardTrigger
					render={
						<button
							type="button"
							className="cursor-help rounded-sm text-xs tabular-nums outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50"
						/>
					}
				>
					{watermark ? (
						<RelativeTime value={watermark} tone={tone} tooltip={false} />
					) : (
						<span className="text-muted-foreground">—</span>
					)}
				</HoverCardTrigger>
				<HoverCardContent className="w-72 space-y-1.5">
					<p className="font-medium">{column.label}</p>
					{members.length === 0 ? (
						<p className="text-muted-foreground">
							{resource.counts.length === 0
								? `This ${resourceNoun} has not synced yet, so it reports no classes.`
								: `Not tracked — this ${resourceNoun} does not mirror ${column.label.toLowerCase()}.`}
						</p>
					) : (
						<ul className="space-y-1">
							{members.map((member) => {
								const memberDate = asDate(member.lastSyncedAt);
								return (
									<li key={member.key} className="text-muted-foreground">
										<span className="text-foreground tabular-nums">
											{member.count.toLocaleString()}
										</span>{" "}
										{member.label.toLowerCase()} ·{" "}
										{memberDate ? (
											<span className="tabular-nums">{absolute(memberDate)}</span>
										) : (
											<span className="italic">not tracked</span>
										)}
									</li>
								);
							})}
						</ul>
					)}
					{members.length > 1 && watermark && (
						<p className="text-muted-foreground text-xs">
							The column shows the oldest of these: {total.toLocaleString()} items through{" "}
							{absolute(watermark)}.
						</p>
					)}
				</HoverCardContent>
			</HoverCard>
		</TableCell>
	);
}

/**
 * The row's status marker, replacing what used to be a `State` badge column.
 *
 * The badge was a column's worth of width spent on nothing: `SYNCED` is what a fresh timestamp in
 * every cell to its right already says, and `ERROR` never appeared without the error icon at the end
 * of the same row. Only the two states that qualify the row's numbers get a mark, and the word itself
 * stays available to a screen reader, which cannot see a coloured dot.
 */
function StatusDot({ state }: { state: string }) {
	const normalized = state.toUpperCase();
	const tone =
		normalized === "ERROR"
			? "bg-destructive"
			: normalized === "PENDING"
				? "bg-muted-foreground"
				: undefined;
	if (!tone) return null;
	return (
		<>
			<span className={cn("size-1.5 shrink-0 rounded-full", tone)} aria-hidden />
			<span className="sr-only">{stateLabel(state)}</span>
		</>
	);
}

/**
 * Name, external id and — while one is running — the backfill.
 *
 * The backfill used to be a "Synced through" column that meant a percentage during a run and a date
 * afterwards, which is two facts wearing one header and scannable as neither. The run is the part
 * that changes under the admin's eye, so it goes where the eye already is; the horizon it finished at
 * is a fact about the past and moves into the hover with the rest of them.
 */
function ResourceNameCell({
	resource,
	resourceNoun,
}: {
	resource: SyncResourceState;
	resourceNoun: string;
}) {
	const percent = resource.backfillPercent;
	const isBackfilling = percent != null && percent < 100;
	const completedThrough = asDate(resource.backfillCompletedThrough);

	return (
		<TableCell>
			<HoverCard>
				{/* Only the name is the trigger: a `Progress` inside a `button` is not valid phrasing
				    content, and the bar is a reading rather than a thing to reveal anything about. */}
				<HoverCardTrigger
					render={
						<button
							type="button"
							className="block min-w-0 cursor-help rounded-sm text-left outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50"
						/>
					}
				>
					<span className="flex items-center gap-1.5">
						<StatusDot state={resource.state} />
						<span className="max-w-[26ch] truncate font-medium">{resource.name}</span>
					</span>
					<span className="block max-w-[26ch] truncate text-muted-foreground font-mono text-xs">
						{resource.externalId}
					</span>
				</HoverCardTrigger>
				<HoverCardContent className="w-80 space-y-1.5">
					<p className="font-medium wrap-anywhere">{resource.name}</p>
					<p className="text-muted-foreground font-mono text-xs wrap-anywhere">
						{resource.externalId}
					</p>
					<dl className="grid grid-cols-[7rem_1fr] gap-x-3 gap-y-1 text-muted-foreground">
						<dt>State</dt>
						<dd className="text-foreground">{stateLabel(resource.state)}</dd>
						{resource.upstreamCount != null && (
							<>
								<dt>Upstream</dt>
								<dd className="text-foreground tabular-nums">
									{resource.upstreamCount.toLocaleString()} items
								</dd>
							</>
						)}
						{completedThrough && (
							<>
								<dt>Backfilled to</dt>
								<dd className="text-foreground tabular-nums">{absolute(completedThrough)}</dd>
							</>
						)}
					</dl>
					{!completedThrough && !isBackfilling && (
						<p className="text-muted-foreground text-xs">
							No backfill has run for this {resourceNoun}.
						</p>
					)}
				</HoverCardContent>
			</HoverCard>
			{isBackfilling && (
				<div className="mt-1 flex items-center gap-2">
					<Progress
						value={percent}
						className="h-1 w-24"
						aria-label={`Backfill progress for ${resource.name}`}
					/>
					<span className="text-muted-foreground text-xs tabular-nums">
						Backfilling · {percent}%
					</span>
				</div>
			)}
		</TableCell>
	);
}

/** The last error, as a read-only peek: it reveals, it does not act, and it traps no focus. */
function ResourceErrorCell({ resource }: { resource: SyncResourceState }) {
	return (
		<TableCell className="text-right">
			{resource.lastError && (
				<HoverCard>
					<HoverCardTrigger
						render={
							<button
								type="button"
								aria-label={`Error for ${resource.name}`}
								className="inline-flex cursor-help rounded-sm outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50"
							/>
						}
					>
						<AlertCircleIcon className="size-4 text-destructive" aria-hidden />
					</HoverCardTrigger>
					<HoverCardContent className="w-80 wrap-anywhere">{resource.lastError}</HoverCardContent>
				</HoverCard>
			)}
		</TableCell>
	);
}

/**
 * The header, shared verbatim by the loading and loaded states, so the `<thead>` doesn't appear out of
 * nowhere on resolve.
 */
function ResourcesTableHeader({
	columns,
	resourceNoun,
}: {
	columns: ClassColumn[];
	resourceNoun: string;
}) {
	return (
		<TableHeader>
			<TableRow>
				<TableHead className="capitalize">{resourceNoun}</TableHead>
				{columns.map((column) => (
					<TableHead key={column.key}>{column.label}</TableHead>
				))}
				<TableHead className="text-right">Items</TableHead>
				<TableHead className="w-0 text-right">
					<span className="sr-only">Error</span>
				</TableHead>
			</TableRow>
		</TableHeader>
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
	/**
	 * The classes this connection mirrors, as the caller knows them.
	 *
	 * The matrix's columns are the union of this and what the data actually reports, which matters at
	 * both ends of the load: while the rows are skeletons there is no data to derive columns from, and
	 * a header that promises two columns and resolves into seven is a shift rather than a placeholder;
	 * and a connection whose resources have *all* never synced reports no classes at all, which would
	 * otherwise collapse the matrix to nothing at exactly the moment an admin is asking why. A column
	 * with no data is a row of dashes, and a dash is a fact — the hover says which kind.
	 */
	expectedClassKeys?: ClassKey[];
}

/**
 * Per-resource, per-class freshness: which repositories are current, and — the question the old table
 * could not answer without a click per row — which *classes* within them have stopped.
 *
 * A repository is not "21,300 items synced 4 minutes ago". It is issues, PRs, reviews, comments and
 * commits, each with its own watermark, and the failure this surface exists to catch is the one where
 * five of them are current and the sixth quietly stopped a week ago. That fact used to live behind a
 * disclosure — one row at a time, never comparable across repositories — while the columns it should
 * have occupied were spent on a state badge that repeated the timestamps and a "Synced through" that
 * changed meaning mid-column. It is now the table.
 */
export function SyncResourcesTable({
	resources,
	isLoading = false,
	isError = false,
	error,
	onRetry,
	resourceNoun,
	resourceNounPlural,
	syncIntervalSeconds,
	expectedClassKeys,
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
		const columns = columnsFor(expectedClassKeys ?? []);
		return (
			<Table>
				<ResourcesTableHeader columns={columns} resourceNoun={resourceNoun} />
				<TableRowsSkeleton
					columns={["w-40", ...columns.map(() => "w-16"), "w-14", null]}
					rows={5}
				/>
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
						Synced {resourceNounPlural} and their per-class freshness appear here once a sync job
						runs.
					</EmptyDescription>
				</EmptyHeader>
			</Empty>
		);
	}

	const columns = columnsFor([...(expectedClassKeys ?? []), ...classKeysOf(resources)]);

	return (
		<Table>
			<ResourcesTableHeader columns={columns} resourceNoun={resourceNoun} />
			<TableBody>
				{resources.map((resource) => (
					<TableRow key={resource.id}>
						<ResourceNameCell resource={resource} resourceNoun={resourceNoun} />
						{columns.map((column) => (
							<ClassCell
								key={column.key}
								resource={resource}
								column={column}
								resourceNoun={resourceNoun}
								syncIntervalSeconds={syncIntervalSeconds}
							/>
						))}
						<TableCell className="text-right tabular-nums">
							{resource.itemCount != null ? resource.itemCount.toLocaleString() : "–"}
						</TableCell>
						<ResourceErrorCell resource={resource} />
					</TableRow>
				))}
			</TableBody>
		</Table>
	);
}
