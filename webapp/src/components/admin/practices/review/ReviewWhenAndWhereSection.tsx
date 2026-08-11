import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { toast } from "sonner";
import {
	createSweepScheduleMutation,
	deleteSweepScheduleMutation,
	getPracticeReviewSettingsOptions,
	getWorkspaceOptions,
	listAgentsOptions,
	listSweepSchedulesOptions,
	listSweepSchedulesQueryKey,
	replaceSweepScheduleMutation,
} from "@/api/@tanstack/react-query.gen";
import type {
	CreateReviewSweepScheduleRequest,
	UpdatePracticeReviewSettingsRequest,
	UpdateReviewSweepScheduleRequest,
} from "@/api/types.gen";
import {
	type PracticeReviewField,
	PracticeReviewSettings as PracticeReviewSettingsForm,
	type PracticeReviewWorkspaceUpdate,
} from "@/components/admin/practices/PracticeReviewSettings";
import { PracticeReviewSweepSchedule } from "@/components/admin/practices/PracticeReviewSweepSchedule";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { Spinner } from "@/components/ui/spinner";
import { usePracticeReviewSettingsMutation } from "@/hooks/use-practice-review-settings";
import { useUpdateWorkspaceFeatures } from "@/hooks/use-update-workspace-features";
import { problemDetailOf } from "@/lib/problem-detail";

export interface ReviewWhenAndWhereSectionProps {
	workspaceSlug: string;
}

/**
 * What starts a review, how often, and over which repositories and branches.
 *
 * <p>The recurring check lives here rather than with the backfill it used to sit beside. It is not a
 * campaign over history: it is a standing policy about *recent* work, the safety net for a change a
 * missed webhook never told us about — which makes it one of the triggers, and it belongs next to
 * them. Filing it under "past work" put a permanent setting behind a heading that reads as one-off.
 */
export function ReviewWhenAndWhereSection({ workspaceSlug }: ReviewWhenAndWhereSectionProps) {
	const queryClient = useQueryClient();

	const reviewSettingsQuery = useQuery({
		...getPracticeReviewSettingsOptions({ path: { workspaceSlug } }),
	});
	const bindingsQuery = useQuery({ ...listAgentsOptions({ path: { workspaceSlug } }) });
	const workspaceQuery = useQuery({ ...getWorkspaceOptions({ path: { workspaceSlug } }) });
	const schedulesQuery = useQuery(listSweepSchedulesOptions({ path: { workspaceSlug } }));

	const updatePracticeReviewSettings = usePracticeReviewSettingsMutation(workspaceSlug, {
		success: "Review settings updated",
		error: "Failed to update review settings",
	});
	const updateFeatures = useUpdateWorkspaceFeatures(workspaceSlug, {
		success: "Practice review settings updated",
		error: "Failed to update practice review settings",
	});

	const invalidateSchedules = () =>
		queryClient.invalidateQueries({
			queryKey: listSweepSchedulesQueryKey({ path: { workspaceSlug } }),
		});
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

	// The bindings query is deliberately not part of this gate. Its state is passed into the form,
	// which renders model readiness as one card among four — blocking the whole section on it would
	// hide four working settings behind one slow request.
	const isLoading = reviewSettingsQuery.isPending || workspaceQuery.isPending;
	const error = reviewSettingsQuery.error ?? workspaceQuery.error;

	let settingsForm: ReactNode;
	if (isLoading) {
		settingsForm = (
			<div className="flex h-40 items-center justify-center">
				<Spinner className="size-6" />
			</div>
		);
	} else if (error || !reviewSettingsQuery.data || !workspaceQuery.data) {
		settingsForm = (
			<QueryErrorAlert
				error={error}
				title="Couldn't load the review settings"
				onRetry={() => {
					reviewSettingsQuery.refetch();
					workspaceQuery.refetch();
				}}
			/>
		);
	} else {
		settingsForm = (
			<PracticeReviewSettingsForm
				workspaceSlug={workspaceSlug}
				model={{
					binding: bindingsQuery.data?.find((binding) => binding.purpose === "PRACTICE_REVIEW"),
					isLoading: bindingsQuery.isLoading,
					isError: bindingsQuery.isError,
					onRetry: () => bindingsQuery.refetch(),
				}}
				workspace={{
					enabled: workspaceQuery.data.practicesEnabled,
					autoTriggerEnabled: workspaceQuery.data.practiceReviewAutoTriggerEnabled,
					manualTriggerEnabled: workspaceQuery.data.practiceReviewManualTriggerEnabled,
					isSaving: updateFeatures.isPending,
					onUpdate: (settings: PracticeReviewWorkspaceUpdate) =>
						updateFeatures.mutate({ path: { workspaceSlug }, body: settings }),
				}}
				policy={{
					settings: reviewSettingsQuery.data,
					isSaving: updatePracticeReviewSettings.isPending,
					onUpdate: (settings: UpdatePracticeReviewSettingsRequest) =>
						updatePracticeReviewSettings.mutate({ path: { workspaceSlug }, body: settings }),
					onReset: (field: PracticeReviewField) =>
						updatePracticeReviewSettings.mutate({
							path: { workspaceSlug },
							body: { reset: [field] },
						}),
				}}
			/>
		);
	}

	return (
		<div className="max-w-3xl space-y-6">
			{settingsForm}
			{/* Outside the gate above, and deliberately. The recurring check is a separate resource with
			    its own request and its own error handling, and it is a standing authorisation to spend —
			    so a failed review-settings load must not be what stops an admin pausing a runaway one.
			    It rendered unconditionally on the page it came from; the move must not cost it that. */}
			<PracticeReviewSweepSchedule
				schedules={schedulesQuery.data ?? []}
				isLoading={schedulesQuery.isLoading}
				isError={schedulesQuery.isError}
				onRetry={() => void schedulesQuery.refetch()}
				isSaving={createSchedule.isPending || replaceSchedule.isPending || deleteSchedule.isPending}
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
		</div>
	);
}
