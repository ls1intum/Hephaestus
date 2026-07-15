import type { VariantProps } from "class-variance-authority";
import { formatDistanceStrict } from "date-fns";
import { AlertCircleIcon, ChevronLeftIcon, ChevronRightIcon, HistoryIcon } from "lucide-react";
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
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Skeleton } from "@/components/ui/skeleton";
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
	if (job.itemsTotal != null) {
		return `${job.itemsProcessed ?? 0}/${job.itemsTotal}`;
	}
	if (job.itemsProcessed != null) {
		return String(job.itemsProcessed);
	}
	return "–";
}

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
			<div className="space-y-2">
				<Skeleton className="h-10 w-full" />
				<Skeleton className="h-10 w-full" />
				<Skeleton className="h-10 w-full" />
			</div>
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

	return (
		<div className="space-y-3">
			<Table>
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
				<div className="flex items-center justify-end gap-2">
					<span className="text-muted-foreground text-sm">
						Page {page + 1} of {totalPages}
					</span>
					<Button
						variant="outline"
						size="icon-sm"
						aria-label="Previous page"
						disabled={page === 0}
						onClick={() => onPageChange(page - 1)}
					>
						<ChevronLeftIcon className="size-4" />
					</Button>
					<Button
						variant="outline"
						size="icon-sm"
						aria-label="Next page"
						disabled={page >= totalPages - 1}
						onClick={() => onPageChange(page + 1)}
					>
						<ChevronRightIcon className="size-4" />
					</Button>
				</div>
			)}
		</div>
	);
}
