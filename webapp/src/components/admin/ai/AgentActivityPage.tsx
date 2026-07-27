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
import { filedUnder, usePendingMutationIds } from "@/hooks/use-pending-mutation-ids";
import { problemDetailOf } from "@/lib/problem-detail";
import { AgentJobDetailsPanel } from "./AgentJobDetailsPanel";
import { AgentJobsTable } from "./AgentJobsTable";
import type { JobStatus } from "./job-utils";

const PAGE_SIZE = 20;

/**
 * Filed per job, not observed per hook: a `useMutation`'s `isPending` belongs to the hook, so the
 * second run's Cancel would be disabled by the first run's request.
 */
const CANCEL_MUTATION_KEY = ["cancelAgentJob"];
const RETRY_DELIVERY_MUTATION_KEY = ["retryAgentJobDelivery"];

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

	// A prefix key, omitting `query`: invalidation matches partially, so this reaches every page and
	// status filter of *this* workspace's job list, and nothing outside it.
	const invalidateJobs = () => {
		queryClient.invalidateQueries({
			queryKey: listAgentJobsQueryKey({ path: { workspaceSlug } }),
		});
	};

	const cancelKey = [...CANCEL_MUTATION_KEY, workspaceSlug];
	const retryDeliveryKey = [...RETRY_DELIVERY_MUTATION_KEY, workspaceSlug];

	const cancelJob = useMutation({
		...filedUnder(cancelKey, cancelAgentJobMutation()),
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
		...filedUnder(retryDeliveryKey, retryAgentJobDeliveryMutation()),
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

	const cancellingJobIds = usePendingMutationIds<{ path: { jobId: string } }, string>(
		cancelKey,
		(variables) => variables.path.jobId,
	);
	const retryingJobIds = usePendingMutationIds<{ path: { jobId: string } }, string>(
		retryDeliveryKey,
		(variables) => variables.path.jobId,
	);

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
				isCancelling={selectedJob != null && cancellingJobIds.has(selectedJob.id)}
				isRetrying={selectedJob != null && retryingJobIds.has(selectedJob.id)}
				onCancel={(job) => cancelJob.mutate({ path: { workspaceSlug, jobId: job.id } })}
				onRetryDelivery={(job) => retryDelivery.mutate({ path: { workspaceSlug, jobId: job.id } })}
			/>
		</div>
	);
}
