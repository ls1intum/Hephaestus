import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { toast } from "sonner";
import {
	cancelAgentJobMutation,
	listAgentJobsOptions,
	listAgentJobsQueryKey,
	retryAgentJobDeliveryMutation,
} from "@/api/@tanstack/react-query.gen";
import type { AgentJob } from "@/api/types.gen";
import { TablePagination } from "@/components/common/TablePagination";
import { problemDetailOf } from "@/lib/problem-detail";
import { AgentJobDetailsPanel } from "./AgentJobDetailsPanel";
import { AgentJobsTable } from "./AgentJobsTable";
import type { JobStatus } from "./job-utils";

const PAGE_SIZE = 20;

interface AgentActivityPageProps {
	workspaceSlug: string;
}

export function AgentActivityPage({ workspaceSlug }: AgentActivityPageProps) {
	const queryClient = useQueryClient();
	const [statusFilter, setStatusFilter] = useState<JobStatus | "ALL">("ALL");
	const [page, setPage] = useState(0);
	const [selectedJob, setSelectedJob] = useState<AgentJob | null>(null);
	const [panelOpen, setPanelOpen] = useState(false);

	const jobsQuery = useQuery({
		...listAgentJobsOptions({
			path: { workspaceSlug },
			query: {
				status: statusFilter === "ALL" ? undefined : statusFilter,
				page,
				size: PAGE_SIZE,
			},
		}),
		enabled: Boolean(workspaceSlug),
	});

	// A key without the `query` part, which the generated helper then omits from the key entirely.
	// Invalidation matches keys partially, so this reaches every page and status filter of *this*
	// workspace's job list — and nothing outside it.
	const invalidateJobs = () => {
		queryClient.invalidateQueries({
			queryKey: listAgentJobsQueryKey({ path: { workspaceSlug } }),
		});
	};

	const cancelJob = useMutation({
		...cancelAgentJobMutation(),
		onSuccess: (updated) => {
			invalidateJobs();
			setSelectedJob(updated);
			toast.success("Run cancelled");
		},
		onError: (error) => {
			// The server's ProblemDetail says *why* (already running, already finished, not yours);
			// `error.message` would only ever say "Request failed".
			toast.error("Couldn't cancel the run", {
				description: problemDetailOf(error, "Try again in a moment."),
			});
		},
	});

	const retryDelivery = useMutation({
		...retryAgentJobDeliveryMutation(),
		onSuccess: (updated) => {
			invalidateJobs();
			setSelectedJob(updated);
			toast.success("Delivery retried");
		},
		onError: (error) => {
			toast.error("Couldn't retry the delivery", {
				description: problemDetailOf(error, "Try again in a moment."),
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
			</div>

			<AgentJobsTable
				jobs={jobs}
				isLoading={jobsQuery.isLoading || !workspaceSlug}
				isError={jobsQuery.isError}
				statusFilter={statusFilter}
				onStatusFilterChange={(value) => {
					setStatusFilter(value);
					setPage(0);
				}}
				onSelectJob={handleSelectJob}
				onRetry={() => jobsQuery.refetch()}
			/>

			<TablePagination
				className="mt-6"
				page={currentPage}
				totalPages={totalPages}
				onPageChange={setPage}
			/>

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
