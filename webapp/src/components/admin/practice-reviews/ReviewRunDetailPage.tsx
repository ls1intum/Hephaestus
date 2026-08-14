import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { WorkflowIcon } from "lucide-react";
import { toast } from "sonner";
import {
	cancelAgentJobMutation,
	getAgentJobOptions,
	getAgentJobQueryKey,
	listPracticeReviewFeedbackOptions,
	listPracticeReviewObservationsOptions,
	listPracticeReviewsQueryKey,
	retryAgentJobDeliveryMutation,
} from "@/api/@tanstack/react-query.gen";
import type { AgentJob } from "@/api/types.gen";
import {
	DELIVERY_STATUS_LABELS,
	deliveryBadgeVariant,
	holdReasonCopy,
	jobWait,
	STATUS_LABELS,
	statusBadgeVariant,
} from "@/components/admin/ai/job-utils";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { RelativeTime } from "@/components/common/RelativeTime";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import {
	Empty,
	EmptyDescription,
	EmptyHeader,
	EmptyMedia,
	EmptyTitle,
} from "@/components/ui/empty";
import { Spinner } from "@/components/ui/spinner";
import { problemDetailOf } from "@/lib/problem-detail";
import { ReviewArtifactLink } from "./ReviewArtifact";
import { ReviewBreadcrumbs } from "./ReviewBreadcrumbs";
import { ReviewOutputSections } from "./ReviewOutputSections";
import { ReviewRunActions } from "./ReviewRunActions";
import { ReviewRunTechnicalDetails } from "./ReviewRunTechnicalDetails";
import type { RunsSearch } from "./review-search";

const ACTIVE_REVIEW_POLL_MS = 5_000;

export interface ReviewRunDetailPageProps {
	workspaceSlug: string;
	jobId: string;
	search: RunsSearch;
}

export function ReviewRunDetailPage({ workspaceSlug, jobId, search }: ReviewRunDetailPageProps) {
	const queryClient = useQueryClient();
	const jobQuery = useQuery({
		...getAgentJobOptions({ path: { workspaceSlug, jobId } }),
		refetchInterval: (result) =>
			result.state.data?.status === "QUEUED" || result.state.data?.status === "RUNNING"
				? ACTIVE_REVIEW_POLL_MS
				: false,
	});
	const runIsActive = jobQuery.data?.status === "QUEUED" || jobQuery.data?.status === "RUNNING";
	const findingsQuery = useQuery({
		...listPracticeReviewObservationsOptions({
			path: { workspaceSlug },
			query: { agentJobId: jobId, sort: "ACTIONABILITY", size: 5 },
		}),
		refetchInterval: runIsActive ? ACTIVE_REVIEW_POLL_MS : false,
	});
	const feedbackQuery = useQuery({
		...listPracticeReviewFeedbackOptions({
			path: { workspaceSlug },
			query: { agentJobId: jobId, size: 5 },
		}),
		refetchInterval: runIsActive ? ACTIVE_REVIEW_POLL_MS : false,
	});
	const updateJob = (job: AgentJob) => {
		queryClient.setQueryData(getAgentJobQueryKey({ path: { workspaceSlug, jobId } }), job);
		queryClient.invalidateQueries({
			queryKey: listPracticeReviewsQueryKey({ path: { workspaceSlug } }),
		});
	};
	const cancelJob = useMutation({
		...cancelAgentJobMutation(),
		onSuccess: (job) => {
			updateJob(job);
			toast.success("Review cancelled");
		},
		onError: (error) =>
			toast.error("Couldn't cancel the review", {
				description: problemDetailOf(error, "Try again in a moment."),
			}),
	});
	const retryDelivery = useMutation({
		...retryAgentJobDeliveryMutation(),
		onSuccess: (job) => {
			updateJob(job);
			toast.success("Summary comment queued for retry");
		},
		onError: (error) =>
			toast.error("Couldn't retry the summary comment", {
				description: problemDetailOf(error, "Try again in a moment."),
			}),
	});
	const breadcrumbs = (
		<ReviewBreadcrumbs
			workspaceSlug={workspaceSlug}
			section={{
				label: "Reviews",
				link: (
					<Link
						to="/w/$workspaceSlug/admin/practices/reviews"
						params={{ workspaceSlug }}
						search={{ page: search.page, status: search.status }}
					/>
				),
			}}
			current="Review"
		/>
	);

	if (jobQuery.isLoading)
		return (
			<article className="min-w-0 max-w-4xl space-y-8">
				{breadcrumbs}
				<div className="flex min-h-64 items-center justify-center">
					<Spinner className="size-7" />
				</div>
			</article>
		);
	if (jobQuery.isError || !jobQuery.data) {
		return (
			<article className="min-w-0 max-w-4xl space-y-8">
				{breadcrumbs}
				<QueryErrorAlert
					error={jobQuery.error}
					title="Couldn't load this review"
					onRetry={() => jobQuery.refetch()}
				/>
			</article>
		);
	}
	const job = jobQuery.data;
	const wait = jobWait(job);
	const hold = wait?.kind === "hold" ? holdReasonCopy(wait.reason) : undefined;
	const findings = findingsQuery.data?.content ?? [];
	const feedback = feedbackQuery.data?.content ?? [];
	const reviewEndedEarly =
		job.status === "FAILED" || job.status === "TIMED_OUT" || job.status === "CANCELLED";
	const endedWithoutOutput =
		reviewEndedEarly &&
		!findingsQuery.isLoading &&
		!feedbackQuery.isLoading &&
		!findingsQuery.isError &&
		!feedbackQuery.isError &&
		findings.length === 0 &&
		feedback.length === 0;

	return (
		<article className="min-w-0 max-w-4xl space-y-8">
			{breadcrumbs}
			<header className="space-y-4">
				<div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
					<div className="min-w-0 space-y-2">
						<div className="flex flex-wrap items-center gap-2">
							<Badge variant={statusBadgeVariant(job.status)}>{STATUS_LABELS[job.status]}</Badge>
							{job.deliveryStatus && (
								<Badge variant={deliveryBadgeVariant(job.deliveryStatus)}>
									Summary comment: {DELIVERY_STATUS_LABELS[job.deliveryStatus]}
								</Badge>
							)}
						</div>
						<ReviewArtifactLink artifact={job.target} variant="label" display="full" />
						<h2 className="break-words text-2xl font-semibold tracking-tight">
							{job.target.title}
						</h2>
						<p className="text-sm text-muted-foreground">
							{job.startedAt ? "Started " : "Created "}
							<RelativeTime value={job.startedAt ?? job.createdAt} />
						</p>
					</div>
					<ReviewRunActions
						job={job}
						isCancelling={cancelJob.isPending}
						isRetrying={retryDelivery.isPending}
						onCancel={() => cancelJob.mutate({ path: { workspaceSlug, jobId } })}
						onRetry={() => retryDelivery.mutate({ path: { workspaceSlug, jobId } })}
					/>
				</div>
				{reviewEndedEarly && !endedWithoutOutput && (
					<Alert variant={job.status === "FAILED" ? "destructive" : "default"}>
						<AlertTitle>Review output may be incomplete</AlertTitle>
						<AlertDescription>The review ended before it completed.</AlertDescription>
					</Alert>
				)}
				{hold && (
					<Alert variant="warning">
						<AlertTitle>{hold.label}</AlertTitle>
						<AlertDescription>{hold.detail}</AlertDescription>
					</Alert>
				)}
			</header>

			{endedWithoutOutput ? (
				<Empty className="border">
					<EmptyHeader>
						<EmptyMedia variant="icon">
							<WorkflowIcon />
						</EmptyMedia>
						<EmptyTitle>Review couldn't be completed</EmptyTitle>
						<EmptyDescription>
							This review ended before it produced observations or feedback.
						</EmptyDescription>
					</EmptyHeader>
				</Empty>
			) : (
				<ReviewOutputSections
					workspaceSlug={workspaceSlug}
					scope={{ agentJobId: jobId }}
					context="review"
					outcome={job.reviewOutcome}
					feedback={
						feedbackQuery.isLoading
							? { status: "loading" }
							: feedbackQuery.isError
								? {
										status: "error",
										error: feedbackQuery.error,
										onRetry: () => void feedbackQuery.refetch(),
									}
								: runIsActive && feedback.length === 0
									? { status: "pending" }
									: {
											status: "ready",
											items: feedback,
											total: feedbackQuery.data?.page?.totalElements ?? 0,
										}
					}
					findings={
						findingsQuery.isLoading
							? { status: "loading" }
							: findingsQuery.isError
								? {
										status: "error",
										error: findingsQuery.error,
										onRetry: () => void findingsQuery.refetch(),
									}
								: runIsActive && findings.length === 0
									? { status: "pending" }
									: {
											status: "ready",
											items: findings,
											total: findingsQuery.data?.page?.totalElements ?? 0,
										}
					}
				/>
			)}

			<ReviewRunTechnicalDetails job={job} />
		</article>
	);
}
