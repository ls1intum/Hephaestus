import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { toast } from "sonner";
import {
	getAiSettingsOptions,
	getAiSettingsQueryKey,
	getBindingsOptions,
	getWorkspaceOptions,
	listWorkspacesQueryKey,
	updateFeaturesMutation,
	updatePracticeReviewSettingsMutation,
	workspaceListAvailableLlmModelsOptions,
} from "@/api/@tanstack/react-query.gen";
import type { UpdatePracticeReviewSettings, UpdateWorkspaceFeaturesRequest } from "@/api/types.gen";
import {
	PracticeDetectionPolicyCard,
	type PracticeReviewField,
} from "@/components/admin/ai/PracticeDetectionPolicyCard";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/practices/settings")({
	component: ReviewSettingsContainer,
});

function ReviewSettingsContainer() {
	const queryClient = useQueryClient();
	const { workspaceSlug } = useActiveWorkspaceSlug();
	const slug = workspaceSlug ?? "";

	const aiSettingsQuery = useQuery({
		...getAiSettingsOptions({ path: { workspaceSlug: slug } }),
		enabled: Boolean(workspaceSlug),
	});

	const bindingsQuery = useQuery({
		...getBindingsOptions({ path: { workspaceSlug: slug } }),
		enabled: Boolean(workspaceSlug),
	});

	const availableModelsQuery = useQuery({
		...workspaceListAvailableLlmModelsOptions({ path: { workspaceSlug: slug } }),
		enabled: Boolean(workspaceSlug),
	});

	const workspaceQuery = useQuery({
		...getWorkspaceOptions({ path: { workspaceSlug: slug } }),
		enabled: Boolean(workspaceSlug),
	});

	const invalidateAiSettings = () => {
		queryClient.invalidateQueries({
			queryKey: getAiSettingsQueryKey({ path: { workspaceSlug: slug } }),
		});
	};

	const updatePracticeReviewSettings = useMutation({
		...updatePracticeReviewSettingsMutation(),
		onSuccess: () => {
			invalidateAiSettings();
			toast.success("Review settings updated");
		},
		onError: (error) => {
			toast.error("Failed to update review settings", {
				description: error instanceof Error ? error.message : undefined,
			});
		},
	});

	const updateFeatures = useMutation({
		...updateFeaturesMutation(),
		onSuccess: () => {
			queryClient.invalidateQueries({
				queryKey: getWorkspaceOptions({ path: { workspaceSlug: slug } }).queryKey,
			});
			queryClient.invalidateQueries({ queryKey: listWorkspacesQueryKey() });
			invalidateAiSettings();
			toast.success("Trigger settings updated");
		},
		onError: (error) => {
			toast.error("Failed to update trigger settings", {
				description: error instanceof Error ? error.message : undefined,
			});
		},
	});

	const handleUpdateReviewSettings = (settings: UpdatePracticeReviewSettings) => {
		if (!workspaceSlug) return;
		updatePracticeReviewSettings.mutate({ path: { workspaceSlug }, body: settings });
	};

	const handleResetReviewField = (field: PracticeReviewField) => {
		if (!workspaceSlug) return;
		updatePracticeReviewSettings.mutate({ path: { workspaceSlug }, body: { reset: [field] } });
	};

	const handleUpdateFeatures = (features: UpdateWorkspaceFeaturesRequest) => {
		if (!workspaceSlug) return;
		updateFeatures.mutate({ path: { workspaceSlug }, body: features });
	};

	return (
		<div className="container mx-auto max-w-3xl space-y-6 py-6">
			<header>
				<h1 className="text-3xl font-bold tracking-tight">Review settings</h1>
				<p className="text-muted-foreground">
					Bind the model, choose triggers, and set the review policy for automated practice
					detection in this workspace.
				</p>
			</header>

			<PracticeDetectionPolicyCard
				settings={aiSettingsQuery.data}
				detectionBinding={bindingsQuery.data?.find(
					(binding) => binding.purpose === "PRACTICE_DETECTION",
				)}
				workspaceSlug={slug}
				availableModels={availableModelsQuery.data ?? []}
				autoTriggerEnabled={workspaceQuery.data?.practiceReviewAutoTriggerEnabled ?? true}
				manualTriggerEnabled={workspaceQuery.data?.practiceReviewManualTriggerEnabled ?? true}
				isLoading={
					aiSettingsQuery.isLoading ||
					bindingsQuery.isLoading ||
					availableModelsQuery.isLoading ||
					workspaceQuery.isLoading ||
					!workspaceSlug
				}
				isError={
					aiSettingsQuery.isError ||
					bindingsQuery.isError ||
					availableModelsQuery.isError ||
					workspaceQuery.isError
				}
				error={
					aiSettingsQuery.error ??
					bindingsQuery.error ??
					availableModelsQuery.error ??
					workspaceQuery.error
				}
				isSaving={updatePracticeReviewSettings.isPending || updateFeatures.isPending}
				onUpdateReviewSettings={handleUpdateReviewSettings}
				onUpdateFeatures={handleUpdateFeatures}
				onResetReviewField={handleResetReviewField}
				onRetry={() => {
					aiSettingsQuery.refetch();
					bindingsQuery.refetch();
					availableModelsQuery.refetch();
					workspaceQuery.refetch();
				}}
			/>
		</div>
	);
}
