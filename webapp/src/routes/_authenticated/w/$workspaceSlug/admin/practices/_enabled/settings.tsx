import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { toast } from "sonner";
import {
	getPracticeReviewSettingsOptions,
	getPracticeReviewSettingsQueryKey,
	getWorkspaceOptions,
	listAgentsOptions,
	updatePracticeReviewSettingsMutation,
	workspaceListAvailableLlmModelsOptions,
} from "@/api/@tanstack/react-query.gen";
import type { PracticeReviewSettings, UpdatePracticeReviewSettingsRequest } from "@/api/types.gen";
import {
	PracticeDetectionPolicyCard,
	type PracticeReviewField,
	type PracticeReviewTriggerUpdate,
} from "@/components/admin/ai/PracticeDetectionPolicyCard";
import { useUpdateWorkspaceFeatures } from "@/hooks/use-update-workspace-features";
import { workspaceAdminHead } from "@/lib/page-title";

export const Route = createFileRoute(
	"/_authenticated/w/$workspaceSlug/admin/practices/_enabled/settings",
)({
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

	const availableModelsQuery = useQuery({
		...workspaceListAvailableLlmModelsOptions({ path: { workspaceSlug } }),
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
				description: error instanceof Error ? error.message : undefined,
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
		success: "Trigger settings updated",
		error: "Failed to update trigger settings",
	});

	const handleUpdateReviewSettings = (settings: UpdatePracticeReviewSettingsRequest) => {
		updatePracticeReviewSettings.mutate({ path: { workspaceSlug }, body: settings });
	};

	const handleResetReviewField = (field: PracticeReviewField) => {
		updatePracticeReviewSettings.mutate({ path: { workspaceSlug }, body: { reset: [field] } });
	};

	const handleUpdateTriggers = (triggers: PracticeReviewTriggerUpdate) => {
		updateFeatures.mutate({ path: { workspaceSlug }, body: triggers });
	};

	return (
		<div className="mx-auto w-full max-w-3xl space-y-6">
			<header className="space-y-1">
				<h1 className="text-3xl font-bold tracking-tight">Practice review settings</h1>
				<p className="max-w-2xl text-muted-foreground">
					Choose when practice reviews run and who receives them.
				</p>
			</header>

			<PracticeDetectionPolicyCard
				settings={reviewSettingsQuery.data}
				detectionBinding={bindingsQuery.data?.find(
					(binding) => binding.purpose === "PRACTICE_DETECTION",
				)}
				workspaceSlug={workspaceSlug}
				availableModels={availableModelsQuery.data ?? []}
				autoTriggerEnabled={workspaceQuery.data?.practiceReviewAutoTriggerEnabled ?? true}
				manualTriggerEnabled={workspaceQuery.data?.practiceReviewManualTriggerEnabled ?? true}
				isLoading={
					reviewSettingsQuery.isLoading ||
					bindingsQuery.isLoading ||
					availableModelsQuery.isLoading ||
					workspaceQuery.isLoading
				}
				isError={
					reviewSettingsQuery.isError ||
					bindingsQuery.isError ||
					availableModelsQuery.isError ||
					workspaceQuery.isError
				}
				error={
					reviewSettingsQuery.error ??
					bindingsQuery.error ??
					availableModelsQuery.error ??
					workspaceQuery.error
				}
				savingReviewSettings={updatePracticeReviewSettings.isPending}
				savingTriggers={updateFeatures.isPending}
				onUpdateReviewSettings={handleUpdateReviewSettings}
				onUpdateTriggers={handleUpdateTriggers}
				onResetReviewField={handleResetReviewField}
				onRetry={() => {
					reviewSettingsQuery.refetch();
					bindingsQuery.refetch();
					availableModelsQuery.refetch();
					workspaceQuery.refetch();
				}}
			/>
		</div>
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
