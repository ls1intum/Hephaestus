import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { toast } from "sonner";
import {
	getAiSettingsOptions,
	getAiSettingsQueryKey,
	getConfigsOptions,
	getWorkspaceOptions,
	listWorkspacesQueryKey,
	updateFeaturesMutation,
	updatePracticeConfigMutation,
	updatePracticeReviewSettingsMutation,
} from "@/api/@tanstack/react-query.gen";
import type { UpdatePracticeReviewSettings, UpdateWorkspaceFeaturesRequest } from "@/api/types.gen";
import {
	PracticeDetectionPolicyCard,
	type PracticeReviewField,
} from "@/components/admin/ai/PracticeDetectionPolicyCard";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";

export const Route = createFileRoute(
	"/_authenticated/w/$workspaceSlug/admin/_admin/ai/practice-detection/",
)({
	component: PolicyContainer,
});

function PolicyContainer() {
	const queryClient = useQueryClient();
	const { workspaceSlug } = useActiveWorkspaceSlug();
	const slug = workspaceSlug ?? "";

	const aiSettingsQuery = useQuery({
		...getAiSettingsOptions({ path: { workspaceSlug: slug } }),
		enabled: Boolean(workspaceSlug),
	});

	const configsQuery = useQuery({
		...getConfigsOptions({ path: { workspaceSlug: slug } }),
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

	const updatePracticeConfig = useMutation({
		...updatePracticeConfigMutation(),
		onSuccess: () => {
			invalidateAiSettings();
			toast.success("Practice runtime updated");
		},
		onError: (error) => {
			toast.error("Failed to update practice runtime", {
				description: error instanceof Error ? error.message : undefined,
			});
		},
	});

	const updatePracticeReviewSettings = useMutation({
		...updatePracticeReviewSettingsMutation(),
		onSuccess: () => {
			invalidateAiSettings();
			toast.success("Review policy updated");
		},
		onError: (error) => {
			toast.error("Failed to update review policy", {
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

	const handleBindConfig = (configId: number | null) => {
		if (!workspaceSlug) return;
		updatePracticeConfig.mutate({
			path: { workspaceSlug },
			body: { configId: configId ?? undefined },
		});
	};

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
		<PracticeDetectionPolicyCard
			settings={aiSettingsQuery.data}
			configs={configsQuery.data ?? []}
			autoTriggerEnabled={workspaceQuery.data?.practiceReviewAutoTriggerEnabled ?? true}
			manualTriggerEnabled={workspaceQuery.data?.practiceReviewManualTriggerEnabled ?? true}
			isLoading={
				aiSettingsQuery.isLoading ||
				configsQuery.isLoading ||
				workspaceQuery.isLoading ||
				!workspaceSlug
			}
			isError={aiSettingsQuery.isError || configsQuery.isError || workspaceQuery.isError}
			isSaving={
				updatePracticeConfig.isPending ||
				updatePracticeReviewSettings.isPending ||
				updateFeatures.isPending
			}
			onBindConfig={handleBindConfig}
			onUpdateReviewSettings={handleUpdateReviewSettings}
			onUpdateFeatures={handleUpdateFeatures}
			onResetReviewField={handleResetReviewField}
			onRetry={() => {
				aiSettingsQuery.refetch();
				configsQuery.refetch();
				workspaceQuery.refetch();
			}}
		/>
	);
}
