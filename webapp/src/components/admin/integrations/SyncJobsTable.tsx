import type { VariantProps } from "class-variance-authority";
import { formatDistanceStrict } from "date-fns";
import { AlertCircleIcon, ChevronRightIcon, HistoryIcon } from "lucide-react";
import { Fragment, useState } from "react";
import type { SyncJob } from "@/api/types.gen";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { Badge, type badgeVariants } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
	Empty,
	EmptyDescription,
	EmptyHeader,
	EmptyMedia,
	EmptyTitle,
} from "@/components/ui/empty";
import {
	Pagination,
	PaginationContent,
	PaginationItem,
	PaginationNext,
	PaginationPrevious,
} from "@/components/ui/pagination";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import {
	Table,
	TableBody,
	TableCell,
	TableHead,
	TableHeader,
	TableRow,
} from "@/components/ui/table";
import {
	asDate,
	JOB_STATUS_LABEL,
	JOB_TRIGGER_LABEL,
	JOB_TYPE_LABEL,
	jobProgress,
	phaseLabel,
	relativeTime,
	type SyncJobProgress,
} from "./sync-format";
import { TableRowsSkeleton } from "./TableRowsSkeleton";

type BadgeVariant = NonNullable<VariantProps<typeof badgeVariants>["variant"]>;

const STATUS_VARIANT: Record<SyncJob["status"], BadgeVariant> = {
	PENDING: "secondary",
	RUNNING: "default",
	SUCCEEDED: "success",
	SUCCEEDED_WITH_WARNINGS: "warning",
	FAILED: "destructive",
	CANCELLED: "secondary",
};

export interface SyncJobsTableProps {
	jobs: SyncJob[];
	isLoading?: boolean;
	isError?: boolean;
	error?: unknown;
	onRetry?: () => void;
	page?: number;
	totalPages?: number;
	onPageChange?: (page: number) => void;
}

function formatDuration(job: SyncJob): string {
	const started = asDate(job.startedAt);
	const finished = asDate(job.finishedAt);
	if (started && finished) {
		return formatDistanceStrict(finished, started);
	}
	if (started && job.status === "RUNNING") {
		return "Running…";
	}
	return "–";
}

function formatItems(job: SyncJob): string {
	// Grouped, because a backfill's counts run to seven digits and "1234567/2000000" is a number the
	// reader has to decode rather than read.
	if (job.itemsTotal != null) {
		return `${(job.itemsProcessed ?? 0).toLocaleString()}/${job.itemsTotal.toLocaleString()}`;
	}
	if (job.itemsProcessed != null) {
		return job.itemsProcessed.toLocaleString();
	}
	return "–";
}

// The expander cell plus the seven data columns — the detail row spans all of them.
const COLUMN_COUNT = 8;

function hasProgressDetail(progress: SyncJobProgress): boolean {
	return (
		progress.phase != null ||
		progress.currentStep != null ||
		progress.currentRepository != null ||
		progress.unitsCompleted != null ||
		progress.unitsTotal != null
	);
}

/**
 * The last progress report the job persisted, as a labelled detail list.
 *
 * Deliberately absent: "Triggered by". `triggeredByUserId` ships on every manual job, but it is an
 * account id and this client has no endpoint that resolves one to a person — there is no account
 * lookup in the generated API at all. Rendering "Triggered by 42" would answer the accountability
 * question with a number the admin still has to go look up by hand, so the field stays unrendered
 * until the server either resolves it to a name or exposes a lookup. The Trigger column already says
 * *how* the job started; only *who* is missing.
 */
function JobProgressPanel({ progress }: { progress: SyncJobProgress }) {
	const { phase, currentStep, currentRepository, unitsCompleted, unitsTotal } = progress;
	const hasUnits = unitsCompleted != null || unitsTotal != null;

	return (
		<dl className="grid grid-cols-[8rem_1fr] gap-x-4 gap-y-1 bg-muted/30 px-2 py-2 text-sm">
			{phase && (
				<>
					<dt className="text-muted-foreground">Phase</dt>
					<dd>
						<Badge variant="secondary">{phaseLabel(phase)}</Badge>
					</dd>
				</>
			)}
			{currentStep && (
				<>
					<dt className="text-muted-foreground">Step</dt>
					{/* The step is a whole sentence and the point of the panel, so it wraps here rather than
					    truncating — the row above is where space is scarce, not this one. */}
					<dd className="wrap-anywhere">{currentStep}</dd>
				</>
			)}
			{currentRepository && (
				<>
					<dt className="text-muted-foreground">Resource</dt>
					<dd className="font-mono text-xs">{currentRepository}</dd>
				</>
			)}
			{hasUnits && (
				<>
					<dt className="text-muted-foreground">Units in phase</dt>
					<dd className="tabular-nums">
						{unitsCompleted?.toLocaleString() ?? "–"}
						{unitsTotal != null && ` / ${unitsTotal.toLocaleString()}`}
					</dd>
				</>
			)}
		</dl>
	);
}

/**
 * The header, shared verbatim by the loading and loaded states. Keeping the real `<thead>` mounted
 * while rows load is what stops the table from growing a header row on resolve.
 */
function JobsTableHeader() {
	return (
		<TableHeader>
			<TableRow>
				<TableHead className="w-0">
					<span className="sr-only">Expand details</span>
				</TableHead>
				<TableHead>Status</TableHead>
				<TableHead>Type</TableHead>
				<TableHead>Trigger</TableHead>
				<TableHead>Started</TableHead>
				<TableHead>Duration</TableHead>
				<TableHead className="text-right">Items</TableHead>
				<TableHead className="w-0 text-right">
					<span className="sr-only">Error</span>
				</TableHead>
			</TableRow>
		</TableHeader>
	);
}

/** Placeholder widths sized to the copy each column actually holds; the expander/error slots promise nothing. */
const SKELETON_COLUMNS = [null, "w-16", "w-24", "w-16", "w-20", "w-12", "w-14", null];

export function SyncJobsTable({
	jobs,
	isLoading = false,
	isError = false,
	error,
	onRetry,
	page,
	totalPages,
	onPageChange,
}: SyncJobsTableProps) {
	const [expandedJobs, setExpandedJobs] = useState<ReadonlySet<number>>(new Set());

	const toggleExpanded = (jobId: number) => {
		setExpandedJobs((current) => {
			const next = new Set(current);
			if (!next.delete(jobId)) next.add(jobId);
			return next;
		});
	};

	if (isError) {
		return (
			<QueryErrorAlert error={error} title="We couldn't load the job history" onRetry={onRetry} />
		);
	}

	if (isLoading) {
		return (
			<Table>
				<JobsTableHeader />
				<TableRowsSkeleton columns={SKELETON_COLUMNS} rows={5} />
			</Table>
		);
	}

	if (jobs.length === 0) {
		return (
			<Empty className="border border-dashed">
				<EmptyHeader>
					<EmptyMedia variant="icon">
						<HistoryIcon />
					</EmptyMedia>
					<EmptyTitle>No sync jobs yet</EmptyTitle>
					<EmptyDescription>
						Initial syncs, reconciliations and manual triggers appear here as they run.
					</EmptyDescription>
				</EmptyHeader>
			</Empty>
		);
	}

	const showFooter = page != null && totalPages != null && onPageChange != null && totalPages > 1;
	const isFirstPage = page === 0;
	const isLastPage = totalPages != null && page != null && page >= totalPages - 1;

	return (
		<div className="space-y-3">
			<Table>
				<JobsTableHeader />
				<TableBody>
					{jobs.map((job) => {
						const started = asDate(job.startedAt) ?? asDate(job.createdAt);
						const progress = jobProgress(job);
						const canExpand = hasProgressDetail(progress);
						const isExpanded = expandedJobs.has(job.id);
						return (
							<Fragment key={job.id}>
								<TableRow data-job-id={job.id}>
									<TableCell className="w-0 pr-0">
										{canExpand && (
											<Button
												variant="ghost"
												size="icon-sm"
												aria-label={isExpanded ? "Hide job details" : "Show job details"}
												aria-expanded={isExpanded}
												onClick={() => toggleExpanded(job.id)}
											>
												<ChevronRightIcon
													className={`size-4 transition-transform ${isExpanded ? "rotate-90" : ""}`}
												/>
											</Button>
										)}
									</TableCell>
									<TableCell>
										<Badge variant={STATUS_VARIANT[job.status]}>
											{JOB_STATUS_LABEL[job.status]}
										</Badge>
									</TableCell>
									<TableCell>{JOB_TYPE_LABEL[job.type]}</TableCell>
									<TableCell className="text-muted-foreground">
										{JOB_TRIGGER_LABEL[job.trigger]}
									</TableCell>
									<TableCell className="text-muted-foreground">{relativeTime(started)}</TableCell>
									<TableCell className="text-muted-foreground">{formatDuration(job)}</TableCell>
									<TableCell className="text-right tabular-nums text-muted-foreground">
										{formatItems(job)}
									</TableCell>
									<TableCell className="text-right">
										{job.errorSummary && (
											<Popover>
												<PopoverTrigger
													render={
														<Button
															variant="ghost"
															size="icon-sm"
															aria-label={`Error for job ${job.id}`}
														>
															<AlertCircleIcon className="size-4 text-destructive" />
														</Button>
													}
												/>
												<PopoverContent>{job.errorSummary}</PopoverContent>
											</Popover>
										)}
									</TableCell>
								</TableRow>
								{canExpand && isExpanded && (
									<TableRow data-job-detail={job.id} className="hover:bg-transparent">
										<TableCell colSpan={COLUMN_COUNT} className="p-0">
											<JobProgressPanel progress={progress} />
										</TableCell>
									</TableRow>
								)}
							</Fragment>
						);
					})}
				</TableBody>
			</Table>

			{showFooter && (
				<Pagination className="justify-end">
					<PaginationContent>
						<PaginationItem>
							<PaginationPrevious
								aria-disabled={isFirstPage}
								className={isFirstPage ? "pointer-events-none opacity-50" : "cursor-pointer"}
								onClick={() => {
									// `pointer-events-none` is presentation, not a guard — keyboard activation still
									// reaches the handler, so the bound is enforced here too.
									if (!isFirstPage) onPageChange(page - 1);
								}}
							/>
						</PaginationItem>
						<PaginationItem>
							<span className="px-2 text-muted-foreground text-sm tabular-nums">
								Page {page + 1} of {totalPages}
							</span>
						</PaginationItem>
						<PaginationItem>
							<PaginationNext
								aria-disabled={isLastPage}
								className={isLastPage ? "pointer-events-none opacity-50" : "cursor-pointer"}
								onClick={() => {
									if (!isLastPage) onPageChange(page + 1);
								}}
							/>
						</PaginationItem>
					</PaginationContent>
				</Pagination>
			)}
		</div>
	);
}
