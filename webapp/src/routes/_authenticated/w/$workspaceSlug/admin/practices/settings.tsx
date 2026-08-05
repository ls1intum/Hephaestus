import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { SlidersHorizontal } from "lucide-react";
import type { ReactNode } from "react";
import { toast } from "sonner";
import {
	getPracticeReviewSettingsOptions,
	getPracticeReviewSettingsQueryKey,
	getWorkspaceOptions,
	listAgentsOptions,
	updatePracticeReviewSettingsMutation,
} from "@/api/@tanstack/react-query.gen";
import type { PracticeReviewSettings, UpdatePracticeReviewSettingsRequest } from "@/api/types.gen";
import {
	type PracticeReviewField,
	PracticeReviewSettings as PracticeReviewSettingsForm,
	type PracticeReviewWorkspaceUpdate,
} from "@/components/admin/practices/PracticeReviewSettings";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { PageHeader } from "@/components/core/PageHeader";
import { PageLayout } from "@/components/core/PageLayout";
import { Spinner } from "@/components/ui/spinner";
import { useUpdateWorkspaceFeatures } from "@/hooks/use-update-workspace-features";
import { workspaceAdminHead } from "@/lib/page-title";
import { problemDetailOf } from "@/lib/problem-detail";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/practices/settings")({
	head: workspaceAdminHead("Practice review settings"),
	component: ReviewSettingsContainer,
});

function ReviewSettingsContainer() {
	const queryClient = useQueryClient();
	const { workspaceSlug } = Route.useParams();

	const reviewSettingsQuery = useQuery({
		...getPracticeReviewSettingsOptions({ path: { workspaceSlug } }),
	});

	const bindingsQuery = useQuery({
		...listAgentsOptions({ path: { workspaceSlug } }),
	});

	const workspaceQuery = useQuery({
		...getWorkspaceOptions({ path: { workspaceSlug } }),
	});

	const reviewSettingsQueryKey = getPracticeReviewSettingsQueryKey({
		path: { workspaceSlug },
	});
	const reviewSettingsMutationKey = [
		"workspace",
		workspaceSlug,
		"practice-review-settings",
	] as const;

	const updatePracticeReviewSettings = useMutation({
		...updatePracticeReviewSettingsMutation(),
		mutationKey: reviewSettingsMutationKey,
		scope: { id: `workspace:${workspaceSlug}:practice-review-settings` },
		onMutate: async (variables) => {
			await queryClient.cancelQueries({ queryKey: reviewSettingsQueryKey });
			const previous = queryClient.getQueryData<PracticeReviewSettings>(reviewSettingsQueryKey);
			if (previous && !variables.body.reset?.length) {
				queryClient.setQueryData(
					reviewSettingsQueryKey,
					patchReviewSettings(previous, variables.body),
				);
			}
			return { previous };
		},
		onSuccess: (updated) => {
			queryClient.setQueryData(reviewSettingsQueryKey, updated);
			toast.success("Review settings updated");
		},
		onError: (error, _variables, context) => {
			if (context?.previous) {
				queryClient.setQueryData(reviewSettingsQueryKey, context.previous);
			}
			toast.error("Failed to update review settings", {
				description: problemDetailOf(error),
			});
		},
		onSettled: () => {
			if (queryClient.isMutating({ mutationKey: reviewSettingsMutationKey }) === 1) {
				void queryClient.invalidateQueries({
					queryKey: reviewSettingsQueryKey,
				});
			}
		},
	});

	const updateFeatures = useUpdateWorkspaceFeatures(workspaceSlug, {
		success: "Practice review settings updated",
		error: "Failed to update practice review settings",
	});

	const handleUpdateReviewSettings = (settings: UpdatePracticeReviewSettingsRequest) => {
		updatePracticeReviewSettings.mutate({ path: { workspaceSlug }, body: settings });
	};

	const handleResetReviewField = (field: PracticeReviewField) => {
		updatePracticeReviewSettings.mutate({ path: { workspaceSlug }, body: { reset: [field] } });
	};

	const handleUpdateWorkspaceSettings = (settings: PracticeReviewWorkspaceUpdate) => {
		updateFeatures.mutate({ path: { workspaceSlug }, body: settings });
	};

	const isLoading = reviewSettingsQuery.isPending || workspaceQuery.isPending;
	const error = reviewSettingsQuery.error ?? workspaceQuery.error;

	let content: ReactNode;
	if (isLoading) {
		content = (
			<div className="flex h-40 items-center justify-center">
				<Spinner className="size-6" />
			</div>
		);
	} else if (error || !reviewSettingsQuery.data || !workspaceQuery.data) {
		content = (
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
		content = (
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
					onUpdate: handleUpdateWorkspaceSettings,
				}}
				policy={{
					settings: reviewSettingsQuery.data,
					isSaving: updatePracticeReviewSettings.isPending,
					onUpdate: handleUpdateReviewSettings,
					onReset: handleResetReviewField,
				}}
			/>
		);
	}

	return (
		<PageLayout>
			<PageHeader
				icon={<SlidersHorizontal />}
				title="Review settings"
				description="Configure how Hephaestus reviews connected project work."
			/>
			<div className="max-w-3xl">{content}</div>
		</PageLayout>
	);
}

function patchReviewSettings(
	settings: PracticeReviewSettings,
	patch: UpdatePracticeReviewSettingsRequest,
): PracticeReviewSettings {
	return {
		...settings,
		...(patch.skipDrafts === undefined
			? {}
			: { skipDrafts: patch.skipDrafts, skipDraftsOverride: patch.skipDrafts }),
		...(patch.deliverToMerged === undefined
			? {}
			: {
					deliverToMerged: patch.deliverToMerged,
					deliverToMergedOverride: patch.deliverToMerged,
				}),
		...(patch.runForAllUsers === undefined
			? {}
			: {
					runForAllUsers: patch.runForAllUsers,
					runForAllUsersOverride: patch.runForAllUsers,
				}),
		...(patch.cooldownMinutes === undefined
			? {}
			: {
					cooldownMinutes: patch.cooldownMinutes,
					cooldownMinutesOverride: patch.cooldownMinutes,
				}),
	};
}
