import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { toast } from "sonner";
import {
	cancelJobMutation,
	getConfigsOptions,
	listJobsOptions,
	retryDeliveryMutation,
} from "@/api/@tanstack/react-query.gen";
import type { AgentJob } from "@/api/types.gen";
import {
	Pagination,
	PaginationContent,
	PaginationEllipsis,
	PaginationItem,
	PaginationLink,
	PaginationNext,
	PaginationPrevious,
} from "@/components/ui/pagination";
import { AgentJobDetailsPanel } from "./AgentJobDetailsPanel";
import { AgentJobsTable } from "./AgentJobsTable";
import type { JobStatus } from "./jobUtils";

const PAGE_SIZE = 20;

/** Windowed page tokens: first, last, current ±1, with "ellipsis" gaps between. */
function paginationItems(current: number, total: number): (number | "ellipsis")[] {
	if (total <= 7) {
		return Array.from({ length: total }, (_, i) => i);
	}
	const pages = new Set<number>([0, total - 1, current, current - 1, current + 1]);
	const sorted = [...pages].filter((p) => p >= 0 && p < total).sort((a, b) => a - b);
	const items: (number | "ellipsis")[] = [];
	let previous: number | undefined;
	for (const page of sorted) {
		if (previous !== undefined && page - previous > 1) {
			items.push("ellipsis");
		}
		items.push(page);
		previous = page;
	}
	return items;
}

interface AgentActivityPageProps {
	workspaceSlug: string;
}

export function AgentActivityPage({ workspaceSlug }: AgentActivityPageProps) {
	const queryClient = useQueryClient();
	const [statusFilter, setStatusFilter] = useState<JobStatus | "ALL">("ALL");
	const [configFilter, setConfigFilter] = useState<number | "ALL">("ALL");
	const [page, setPage] = useState(0);
	const [selectedJob, setSelectedJob] = useState<AgentJob | null>(null);
	const [panelOpen, setPanelOpen] = useState(false);

	const configsQuery = useQuery({
		...getConfigsOptions({ path: { workspaceSlug } }),
		enabled: Boolean(workspaceSlug),
	});

	const jobsQuery = useQuery({
		...listJobsOptions({
			path: { workspaceSlug },
			query: {
				status: statusFilter === "ALL" ? undefined : statusFilter,
				configId: configFilter === "ALL" ? undefined : configFilter,
				page,
				size: PAGE_SIZE,
			},
		}),
		enabled: Boolean(workspaceSlug),
	});

	// Prefix-invalidate every listJobs page so the current filter/page refetches
	// regardless of its exact query params.
	const invalidateJobs = () => {
		queryClient.invalidateQueries({
			predicate: (query) => {
				const first = query.queryKey[0] as { _id?: string } | undefined;
				return first?._id === "listJobs";
			},
		});
	};

	const cancelJob = useMutation({
		...cancelJobMutation(),
		onSuccess: (updated) => {
			invalidateJobs();
			setSelectedJob(updated);
			toast.success("Job cancelled");
		},
		onError: (error) => {
			toast.error("Failed to cancel job", {
				description: error instanceof Error ? error.message : undefined,
			});
		},
	});

	const retryDelivery = useMutation({
		...retryDeliveryMutation(),
		onSuccess: (updated) => {
			invalidateJobs();
			setSelectedJob(updated);
			toast.success("Delivery retried");
		},
		onError: (error) => {
			toast.error("Failed to retry delivery", {
				description: error instanceof Error ? error.message : undefined,
			});
		},
	});

	const handleSelectJob = (job: AgentJob) => {
		setSelectedJob(job);
		setPanelOpen(true);
	};

	const pageData = jobsQuery.data;
	const jobs = pageData?.content ?? [];
	const totalPages = pageData?.totalPages ?? 0;
	const currentPage = pageData?.number ?? page;

	return (
		<div className="container mx-auto max-w-6xl py-6">
			<div className="mb-6">
				<h1 className="text-3xl font-bold tracking-tight">Runs</h1>
				<p className="text-muted-foreground">
					Every practice review run — its model, token usage, cost, and delivery status.
				</p>
			</div>

			<AgentJobsTable
				jobs={jobs}
				configs={configsQuery.data ?? []}
				isLoading={jobsQuery.isLoading || !workspaceSlug}
				isError={jobsQuery.isError}
				statusFilter={statusFilter}
				configFilter={configFilter}
				onStatusFilterChange={(value) => {
					setStatusFilter(value);
					setPage(0);
				}}
				onConfigFilterChange={(value) => {
					setConfigFilter(value);
					setPage(0);
				}}
				onSelectJob={handleSelectJob}
				onRetry={() => jobsQuery.refetch()}
			/>

			{totalPages > 1 && (
				<Pagination className="mt-6">
					<PaginationContent>
						<PaginationItem>
							<PaginationPrevious
								onClick={() => setPage((p) => Math.max(0, p - 1))}
								className={currentPage <= 0 ? "pointer-events-none opacity-50" : undefined}
							/>
						</PaginationItem>
						{paginationItems(currentPage, totalPages).map((item, index) =>
							item === "ellipsis" ? (
								<PaginationItem key={`ellipsis-${index}`}>
									<PaginationEllipsis />
								</PaginationItem>
							) : (
								<PaginationItem key={item}>
									<PaginationLink isActive={item === currentPage} onClick={() => setPage(item)}>
										{item + 1}
									</PaginationLink>
								</PaginationItem>
							),
						)}
						<PaginationItem>
							<PaginationNext
								onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
								className={
									currentPage >= totalPages - 1 ? "pointer-events-none opacity-50" : undefined
								}
							/>
						</PaginationItem>
					</PaginationContent>
				</Pagination>
			)}

			<AgentJobDetailsPanel
				job={selectedJob}
				open={panelOpen}
				onOpenChange={setPanelOpen}
				isCancelling={cancelJob.isPending}
				isRetrying={retryDelivery.isPending}
				onCancel={(job) => cancelJob.mutate({ path: { workspaceSlug, jobId: job.id } })}
				onRetryDelivery={(job) => retryDelivery.mutate({ path: { workspaceSlug, jobId: job.id } })}
			/>
		</div>
	);
}
