import type { VariantProps } from "class-variance-authority";
import { formatDistanceStrict } from "date-fns";
import { AlertCircleIcon, HistoryIcon } from "lucide-react";
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
	relativeTime,
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

/**
 * The header, shared verbatim by the loading and loaded states. Keeping the real `<thead>` mounted
 * while rows load is what stops the table from growing a header row on resolve.
 */
function JobsTableHeader() {
	return (
		<TableHeader>
			<TableRow>
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

/** Placeholder widths sized to the copy each column actually holds; the error slot promises nothing. */
const SKELETON_COLUMNS = ["w-16", "w-24", "w-16", "w-20", "w-12", "w-14", null];

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
						return (
							<TableRow key={job.id} data-job-id={job.id}>
								<TableCell>
									<Badge variant={STATUS_VARIANT[job.status]}>{JOB_STATUS_LABEL[job.status]}</Badge>
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
