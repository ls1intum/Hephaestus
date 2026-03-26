import { type DefaultError, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { toast } from "sonner";
import {
	deleteUserMutation,
	getUserSettingsOptions,
	getUserSettingsQueryKey,
	updateUserSettingsMutation,
} from "@/api/@tanstack/react-query.gen";
import type { Options } from "@/api/sdk.gen";
import type {
	UpdateUserSettingsData,
	UpdateUserSettingsResponse,
	UserSettings,
} from "@/api/types.gen";
import { SettingsPage } from "@/components/settings/SettingsPage";
import { useAuth } from "@/integrations/auth/AuthContext";
import { isPosthogEnabled } from "@/integrations/posthog/config";

export const Route = createFileRoute("/_authenticated/settings")({
	component: RouteComponent,
});

function RouteComponent() {
	const navigate = useNavigate();
	const queryClient = useQueryClient();
	const { logout, hasRole } = useAuth();
	const userSettingsQueryKey = getUserSettingsQueryKey();

	// Feature flag: AI review section visible only for users with the practice review role
	const showAiReviewSection = hasRole("run_practice_review");

	// Query for user settings
	const { data: settings, isLoading } = useQuery({
		...getUserSettingsOptions({}),
		retry: 1,
	});

	// Mutation for updating user settings
	const updateSettingsMutation = useMutation<
		UpdateUserSettingsResponse,
		DefaultError,
		Options<UpdateUserSettingsData>,
		{ previousSettings?: UserSettings }
	>({
		...updateUserSettingsMutation(),
		onMutate: async (variables) => {
			await queryClient.cancelQueries({
				queryKey: userSettingsQueryKey,
			});
			const previousSettings = queryClient.getQueryData<UserSettings>(userSettingsQueryKey);
			if (variables.body) {
				queryClient.setQueryData(userSettingsQueryKey, variables.body);
			}
			return { previousSettings };
		},
		onError: (error, _variables, context) => {
			if (context?.previousSettings) {
				queryClient.setQueryData(userSettingsQueryKey, context.previousSettings);
			}
			console.error("Failed to update user settings:", error);
			toast.error("Failed to update settings. Please try again later.");
		},
		onSuccess: (data) => {
			queryClient.setQueryData(userSettingsQueryKey, data);
		},
		onSettled: () => {
			queryClient.invalidateQueries({
				queryKey: userSettingsQueryKey,
			});
		},
	});

	// Mutation for deleting account
	const deleteAccountMutation = useMutation({
		...deleteUserMutation(),
		onSuccess: async () => {
			await logout();
			navigate({ to: "/" });
		},
		onError: (error) => {
			console.error("Failed to delete user account:", error);
			toast.error("Failed to delete account. Please try again later.");
		},
	});

	// Spread-based helper: reads latest cache to avoid stale-closure race under rapid toggling
	const updateSetting = (patch: Partial<UserSettings>) => {
		const current = queryClient.getQueryData<UserSettings>(userSettingsQueryKey);
		if (!current) return;
		updateSettingsMutation.mutate({
			body: { ...current, ...patch },
		});
	};

	const handleAiReviewToggle = (checked: boolean) => updateSetting({ aiReviewEnabled: checked });

	const handleResearchToggle = (checked: boolean) =>
		updateSetting({ participateInResearch: checked });

	// Handle account deletion
	const handleDeleteAccount = () => {
		deleteAccountMutation.mutate({});
	};

	return (
		<SettingsPage
			isLoading={isLoading}
			aiReviewProps={{
				aiReviewEnabled: settings?.aiReviewEnabled ?? true,
				onToggleAiReview: handleAiReviewToggle,
				isLoading: updateSettingsMutation.isPending,
			}}
			showAiReviewSection={showAiReviewSection}
			showResearchSection={isPosthogEnabled}
			researchProps={{
				participateInResearch: settings?.participateInResearch ?? true,
				onToggleResearch: handleResearchToggle,
				isLoading: updateSettingsMutation.isPending,
			}}
			accountProps={{
				onDeleteAccount: handleDeleteAccount,
				isDeleting: deleteAccountMutation.isPending,
			}}
		/>
	);
}
