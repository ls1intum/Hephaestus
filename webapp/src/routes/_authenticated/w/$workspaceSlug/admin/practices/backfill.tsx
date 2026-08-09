import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { History } from "lucide-react";
import { toast } from "sonner";
import {
	createSweepScheduleMutation,
	deleteSweepScheduleMutation,
	listBackfillRunsOptions,
	listBackfillRunsQueryKey,
	listSweepSchedulesOptions,
	listSweepSchedulesQueryKey,
	preflightBackfillRunMutation,
	replaceSweepScheduleMutation,
	updateBackfillRunStatusMutation,
} from "@/api/@tanstack/react-query.gen";
import type {
	CreateReviewBackfillRunRequest,
	CreateReviewSweepScheduleRequest,
	UpdateReviewSweepScheduleRequest,
} from "@/api/types.gen";
import { PracticeReviewBackfill } from "@/components/admin/practices/PracticeReviewBackfill";
import { PracticeReviewSweepSchedule } from "@/components/admin/practices/PracticeReviewSweepSchedule";
import { PageHeader } from "@/components/core/PageHeader";
import { PageLayout } from "@/components/core/PageLayout";
import { workspaceAdminHead } from "@/lib/page-title";
import { problemDetailOf } from "@/lib/problem-detail";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/practices/backfill")({
	head: workspaceAdminHead("Review past work"),
	component: BackfillContainer,
});

/** Polled only while a campaign is live: a settled list is not worth refetching forever. */
const ACTIVE_POLL_MS = 15_000;

function BackfillContainer() {
	const queryClient = useQueryClient();
	const { workspaceSlug } = Route.useParams();
	const runsQueryKey = listBackfillRunsQueryKey({ path: { workspaceSlug } });

	const runsQuery = useQuery({
		...listBackfillRunsOptions({ path: { workspaceSlug } }),
		refetchInterval: (query) =>
			query.state.data?.some((run) => run.status === "RUNNING" || run.status === "PAUSED")
				? ACTIVE_POLL_MS
				: false,
	});

	const invalidate = () => queryClient.invalidateQueries({ queryKey: runsQueryKey });

	const preflight = useMutation({
		...preflightBackfillRunMutation(),
		onSuccess: () => {
			void invalidate();
		},
		onError: (error) => {
			toast.error("Couldn't estimate this backfill", { description: problemDetailOf(error) });
		},
	});

	const schedulesQueryKey = listSweepSchedulesQueryKey({ path: { workspaceSlug } });
	const schedulesQuery = useQuery(listSweepSchedulesOptions({ path: { workspaceSlug } }));
	const invalidateSchedules = () => queryClient.invalidateQueries({ queryKey: schedulesQueryKey });

	const scheduleError = (verb: string) => (error: unknown) =>
		toast.error(`Couldn't ${verb} this recurring check`, { description: problemDetailOf(error) });

	const createSchedule = useMutation({
		...createSweepScheduleMutation(),
		onSuccess: () => {
			void invalidateSchedules();
			toast.success("Recurring check started");
		},
		onError: scheduleError("start"),
	});

	const replaceSchedule = useMutation({
		...replaceSweepScheduleMutation(),
		onSuccess: () => void invalidateSchedules(),
		onError: scheduleError("update"),
	});

	const deleteSchedule = useMutation({
		...deleteSweepScheduleMutation(),
		onSuccess: () => {
			void invalidateSchedules();
			toast.success("Recurring check removed");
		},
		onError: scheduleError("remove"),
	});

	const updateStatus = useMutation({
		...updateBackfillRunStatusMutation(),
		onSuccess: (run) => {
			void invalidate();
			if (run.status === "RUNNING") {
				toast.success("Backfill started");
			} else if (run.status === "CANCELLED") {
				toast.success("Backfill stopped");
			}
		},
		onError: (error) => {
			toast.error("Couldn't update this backfill", { description: problemDetailOf(error) });
		},
	});

	return (
		<PageLayout>
			<PageHeader
				icon={<History />}
				title="Review past work"
				description="Measure work that existed before practice reviews were switched on, and keep checking for work nothing announced."
			/>
			<div className="max-w-3xl space-y-6">
				<PracticeReviewSweepSchedule
					schedules={schedulesQuery.data ?? []}
					isLoading={schedulesQuery.isLoading}
					isError={schedulesQuery.isError}
					onRetry={() => void schedulesQuery.refetch()}
					isSaving={
						createSchedule.isPending || replaceSchedule.isPending || deleteSchedule.isPending
					}
					onCreate={(request: CreateReviewSweepScheduleRequest) =>
						createSchedule.mutate({ path: { workspaceSlug }, body: request })
					}
					onReplace={(scheduleId: string, request: UpdateReviewSweepScheduleRequest) =>
						replaceSchedule.mutate({ path: { workspaceSlug, scheduleId }, body: request })
					}
					onDelete={(scheduleId: string) =>
						deleteSchedule.mutate({ path: { workspaceSlug, scheduleId } })
					}
				/>
				<PracticeReviewBackfill
					runs={runsQuery.data ?? []}
					isLoading={runsQuery.isLoading}
					isError={runsQuery.isError}
					onRetry={() => void runsQuery.refetch()}
					isEstimating={preflight.isPending}
					onEstimate={(request: CreateReviewBackfillRunRequest) =>
						preflight.mutate({ path: { workspaceSlug }, body: request })
					}
					isUpdating={updateStatus.isPending}
					onConfirm={(runId) =>
						updateStatus.mutate({ path: { workspaceSlug, runId }, body: { status: "RUNNING" } })
					}
					onCancel={(runId) =>
						updateStatus.mutate({ path: { workspaceSlug, runId }, body: { status: "CANCELLED" } })
					}
				/>
			</div>
		</PageLayout>
	);
}
