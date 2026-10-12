import { skipToken, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef } from "react";
import { toast } from "sonner";

import {
	cancelAgentJobMutation,
	getAgentJobOptions,
	getAgentJobPracticeScriptsOptions,
	getAgentJobQueryKey,
	getAgentJobUsageOptions,
	getArtifactTraceOptions,
	listPracticeReviewDeliveryChecksOptions,
	listPracticeReviewsQueryKey,
	retryAgentJobDeliveryMutation,
} from "@/api/@tanstack/react-query.gen";
import type {
	AgentJob,
	AgentJobUsage,
	DeliveryPolicyTrace,
	ReviewPracticeScripts,
} from "@/api/types.gen";
import type { RunTraceState } from "@/components/admin/practice-reviews/review-run-sections";
import {
	ACTIVE_REVIEW_POLL_MS,
	PRACTICE_REVIEW_READS,
} from "@/components/admin/practice-reviews/review-search";
import { type PanelState, panelState } from "@/components/common/panel-state";
import { problemDetailOf, problemStatusOf } from "@/lib/problem-detail";
import { invalidateWorkspaceReads } from "@/runtime/tanstack-query/invalidate-workspace-reads";
import { retryUnlessNotFound } from "@/runtime/tanstack-query/query-defaults";

/**
 * Everything the review's sheet needs, already resolved: the review, how it answered each practice,
 * the delivery checks it ran for itself, what its practice scripts did, what it cost, and the two
 * actions an operator can take on it. Its
 * results and feedback are other records: the sheet does not read them.
 *
 * The screen is handed states, not queries. That a running review is re-asked for on a timer, and
 * that the timer stops by itself at a terminal status, is this module's business alone.
 */
export interface ReviewRunController {
	job: PanelState<{ job: AgentJob }>;
	trace: RunTraceState;
	deliveryChecks: PanelState<{ checks: DeliveryPolicyTrace[] }>;
	practiceScript: PanelState<{ scripts: ReviewPracticeScripts[] }>;
	usage: PanelState<{ usage: AgentJobUsage }>;
	onCancel: () => void;
	cancelPending: boolean;
	onRetryResultProcessing: () => void;
	retryResultProcessingPending: boolean;
}

export function useReviewRunController(workspaceSlug: string, jobId: string): ReviewRunController {
	const queryClient = useQueryClient();
	const jobQuery = useQuery({
		...getAgentJobOptions({ path: { workspaceSlug, jobId } }),
		retry: retryUnlessNotFound,
		refetchInterval: (result) =>
			result.state.data?.status === "QUEUED" ||
			result.state.data?.status === "RUNNING" ||
			result.state.data?.deliveryStatus === "PENDING"
				? ACTIVE_REVIEW_POLL_MS
				: false,
	});
	const runIsActive = jobQuery.data?.status === "QUEUED" || jobQuery.data?.status === "RUNNING";
	const work = jobQuery.data?.target.reviewedWork;
	const traceOptions = getArtifactTraceOptions({
		path: {
			workspaceSlug,
			artifactKind: work?.kind ?? "",
			artifactId: Number(work?.id ?? 0),
		},
		// This review's answers, not the newest review's of the same work.
		query: { reviewId: jobId },
	});
	const traceQuery = useQuery({
		...traceOptions,
		queryFn: work === undefined ? skipToken : traceOptions.queryFn,
		// A 404 is work nothing was ever recorded about: asking again answers the same.
		retry: retryUnlessNotFound,
		refetchInterval: runIsActive ? ACTIVE_REVIEW_POLL_MS : false,
	});
	const checksOptions = listPracticeReviewDeliveryChecksOptions({
		path: { workspaceSlug, agentJobId: jobId },
	});
	const checksQuery = useQuery({ ...checksOptions, retry: retryUnlessNotFound });
	const usageOptions = getAgentJobUsageOptions({ path: { workspaceSlug, jobId } });
	const usageQuery = useQuery({ ...usageOptions, retry: retryUnlessNotFound });

	// The endpoint shows only the current attempt, and nothing until that attempt ends. Thus, its answer
	// is final once the job is terminal, and it is asked for only then. A retry starts a new attempt.
	// When that attempt ends, the query is enabled again, and it asks again only if its last answer is
	// older than the client's stale time (`QUERY_STALE_TIME_MS`).
	const practiceScriptQuery = useQuery({
		...getAgentJobPracticeScriptsOptions({ path: { workspaceSlug, jobId } }),
		enabled: jobQuery.data !== undefined && !runIsActive,
	});

	// The trace polls only while the review runs, the checks are written when its results are
	// processed and its cost when an attempt ends, and the review's row (its counts and developers,
	// which the sheet and the trace read) is written with its results: the review settling is what
	// asks all four once more.
	const settled =
		jobQuery.data !== undefined && !runIsActive && jobQuery.data.deliveryStatus !== "PENDING";
	// Undefined until the review is first read, so arriving at a settled review asks nothing again.
	const wasSettled = useRef<boolean | undefined>(undefined);
	useEffect(() => {
		const before = wasSettled.current;
		wasSettled.current = jobQuery.data === undefined ? undefined : settled;
		if (before === false && settled) {
			void queryClient.invalidateQueries({ queryKey: traceOptions.queryKey });
			void queryClient.invalidateQueries({ queryKey: checksOptions.queryKey });
			void queryClient.invalidateQueries({ queryKey: usageOptions.queryKey });
			// Any read of the review's row, whatever its page size.
			void queryClient.invalidateQueries({
				queryKey: listPracticeReviewsQueryKey({
					path: { workspaceSlug },
					query: { agentJobId: jobId },
				}),
			});
		}
	});

	/**
	 * Both actions answer with the job as it now stands, so it is written straight into the cache;
	 * the lists and counts it appears in are re-read, since one row's status cannot rebuild them.
	 */
	const updateJob = (job: AgentJob) => {
		queryClient.setQueryData(getAgentJobQueryKey({ path: { workspaceSlug, jobId } }), job);
		void invalidateWorkspaceReads(queryClient, workspaceSlug, PRACTICE_REVIEW_READS);
	};
	const cancelJob = useMutation({
		...cancelAgentJobMutation(),
		onSuccess: (job) => {
			updateJob(job);
			toast.success("Review canceled");
		},
		onError: (error) =>
			toast.error("We could not cancel the review", {
				description: problemDetailOf(error, "Try again in a moment."),
			}),
	});
	const retryResultProcessing = useMutation({
		...retryAgentJobDeliveryMutation(),
		onSuccess: (job) => {
			updateJob(job);
			toast.success("Result processing will run again");
		},
		onError: (error) =>
			toast.error("We could not run result processing again", {
				description: problemDetailOf(error, "Try again in a moment."),
			}),
	});

	return {
		job: panelState(jobQuery, (job) => ({ status: "ready" as const, job })),
		trace:
			work === undefined || problemStatusOf(traceQuery.error) === 404
				? { status: "none" }
				: panelState(traceQuery, (trace) => ({ status: "ready" as const, trace })),
		deliveryChecks: panelState(checksQuery, (checks) => ({ status: "ready" as const, checks })),
		practiceScript: panelState(practiceScriptQuery, (scripts) => ({
			status: "ready" as const,
			scripts,
		})),
		usage: panelState(usageQuery, (usage) => ({ status: "ready" as const, usage })),
		onCancel: () => cancelJob.mutate({ path: { workspaceSlug, jobId } }),
		cancelPending: cancelJob.isPending,
		onRetryResultProcessing: () => retryResultProcessing.mutate({ path: { workspaceSlug, jobId } }),
		retryResultProcessingPending: retryResultProcessing.isPending,
	};
}
