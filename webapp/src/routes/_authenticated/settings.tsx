import { type DefaultError, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { toast } from "sonner";
import {
	getCurrentUserQueryKey,
	getSlackUserPreferencesOptions,
	getSlackUserPreferencesQueryKey,
	getUserSettingsOptions,
	getUserSettingsQueryKey,
	listIdentityProvidersOptions,
	listLinkedIdentitiesOptions,
	listLinkedIdentitiesQueryKey,
	unlinkIdentityMutation,
	updateSlackUserPreferencesMutation,
	updateUserSettingsMutation,
} from "@/api/@tanstack/react-query.gen";
import type { Options } from "@/api/sdk.gen";
import type {
	SlackUserPreferences,
	UpdateUserSettingsData,
	UpdateUserSettingsResponse,
	UserSettings,
} from "@/api/types.gen";
import type { LinkedAccountsSectionProps } from "@/components/settings/LinkedAccountsSection";
import { SettingsPage } from "@/components/settings/SettingsPage";
import type { SlackPreferencesSectionProps } from "@/components/settings/SlackPreferencesSection";
import { useAuth } from "@/integrations/auth/AuthContext";
import { analyticsConfigured } from "@/integrations/consent";
import { problemDetailOf } from "@/lib/problem-detail";

export const Route = createFileRoute("/_authenticated/settings")({
	component: RouteComponent,
});

function RouteComponent() {
	const queryClient = useQueryClient();
	const { logout, linkAccount, hasRole } = useAuth();
	const userSettingsQueryKey = getUserSettingsQueryKey();

	// Feature flag: AI review section visible only for users with the practice review role
	const showAiReviewSection = hasRole("run_practice_review");

	const {
		data: settings,
		isLoading,
		isError: settingsError,
		refetch: refetchSettings,
	} = useQuery({
		...getUserSettingsOptions({}),
		retry: 1,
	});

	const linkedIdentitiesQuery = useQuery({
		...listLinkedIdentitiesOptions({}),
	});

	const identityProvidersQuery = useQuery({
		...listIdentityProvidersOptions({}),
	});

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

	// After deletion: end the session. `logout()` performs a full reload to "/",
	// so no further navigation is needed here.
	const handleAccountDeleted = async () => {
		await logout();
	};

	// Disconnect a federated identity. The server enforces the lockout guard (409 on the last
	// identity) and ownership; the UI also disables that case, so this path is the happy one.
	const unlinkMutation = useMutation({
		...unlinkIdentityMutation(),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: listLinkedIdentitiesQueryKey({}) });
			// The primary identity (avatar, username) the app shows may have been the one removed.
			queryClient.invalidateQueries({ queryKey: getCurrentUserQueryKey() });
			toast.success("Account disconnected.");
		},
		onError: (error: DefaultError) => {
			console.error("Failed to disconnect account:", error);
			toast.error(problemDetailOf(error, "Couldn't disconnect that account. Please try again."));
		},
	});

	const slackPreferencesQueryKey = getSlackUserPreferencesQueryKey({});
	const updateSlackPreferencesMutation = useMutation({
		...updateSlackUserPreferencesMutation(),
		onSuccess: (updatedWorkspace) => {
			queryClient.setQueryData<SlackUserPreferences>(slackPreferencesQueryKey, (current) => {
				const workspaces = current?.workspaces ?? [];
				const hasWorkspace = workspaces.some(
					(workspace) => workspace.workspaceSlug === updatedWorkspace.workspaceSlug,
				);
				return {
					workspaces: hasWorkspace
						? workspaces.map((workspace) =>
								workspace.workspaceSlug === updatedWorkspace.workspaceSlug
									? updatedWorkspace
									: workspace,
							)
						: [...workspaces, updatedWorkspace],
				};
			});
			queryClient.invalidateQueries({ queryKey: slackPreferencesQueryKey });
			toast.success(
				updatedWorkspace.channelMessagesAllowed
					? "Slack channel-message use is on."
					: "Slack channel-message use is off.",
			);
		},
		onError: (error: DefaultError) => {
			console.error("Failed to update Slack preferences:", error);
			toast.error(problemDetailOf(error, "Couldn't update Slack preferences. Please try again."));
		},
	});

	const linkedAccountsProps: LinkedAccountsSectionProps = {
		identities: linkedIdentitiesQuery.data ?? [],
		providers: identityProvidersQuery.data ?? [],
		onLink: (registrationId) => linkAccount(registrationId, "/settings"),
		// Guard against a double-submit: the trigger uses aria-disabled (kept focusable for the
		// busy announcement), which does not block clicks, so a mid-flight re-confirm would fire a
		// second DELETE against the already-removed row.
		onUnlink: (id) => {
			if (!unlinkMutation.isPending) {
				unlinkMutation.mutate({ path: { id } });
			}
		},
		unlinkingId: unlinkMutation.isPending ? (unlinkMutation.variables?.path?.id ?? null) : null,
		isLoading: linkedIdentitiesQuery.isLoading || identityProvidersQuery.isLoading,
		isError: linkedIdentitiesQuery.isError || identityProvidersQuery.isError,
	};

	const slackProvider = identityProvidersQuery.data?.find(
		(provider) => provider.providerType?.toUpperCase() === "SLACK",
	);
	const slackIdentity = linkedIdentitiesQuery.data?.find(
		(identity) => identity.providerType?.toUpperCase() === "SLACK",
	);
	const slackAvailable = Boolean(slackProvider?.registrationId || slackIdentity);

	const slackPreferencesQuery = useQuery({
		...getSlackUserPreferencesOptions({}),
		enabled: slackAvailable,
		retry: 1,
	});

	const slackPreferencesProps: SlackPreferencesSectionProps = {
		workspaces: slackPreferencesQuery.data?.workspaces ?? [],
		isSlackLinked: Boolean(slackIdentity),
		canConnectSlack: Boolean(slackProvider?.registrationId),
		onConnectSlack: () => {
			if (slackProvider?.registrationId) {
				linkAccount(slackProvider.registrationId, "/settings");
			}
		},
		onToggleChannelMessages: (workspaceSlug, channelMessagesAllowed) => {
			updateSlackPreferencesMutation.mutate({
				path: { workspaceSlug },
				body: { channelMessagesAllowed },
			});
		},
		updatingWorkspaceSlug: updateSlackPreferencesMutation.isPending
			? (updateSlackPreferencesMutation.variables?.path.workspaceSlug ?? null)
			: null,
		isLoading:
			linkedIdentitiesQuery.isLoading ||
			identityProvidersQuery.isLoading ||
			(slackAvailable && slackPreferencesQuery.isLoading),
		isError: slackAvailable && slackPreferencesQuery.isError,
		onRetry: () => slackPreferencesQuery.refetch(),
	};

	return (
		<SettingsPage
			isLoading={isLoading}
			settingsError={settingsError}
			onRetrySettings={() => refetchSettings()}
			aiReviewProps={{
				aiReviewEnabled: settings?.aiReviewEnabled ?? true,
				onToggleAiReview: handleAiReviewToggle,
				isLoading: updateSettingsMutation.isPending,
			}}
			showAiReviewSection={showAiReviewSection}
			showResearchSection={analyticsConfigured}
			researchProps={{
				participateInResearch: settings?.participateInResearch ?? true,
				onToggleResearch: handleResearchToggle,
				isLoading: updateSettingsMutation.isPending,
			}}
			linkedAccountsProps={linkedAccountsProps}
			showSlackPreferencesSection={slackAvailable}
			slackPreferencesProps={slackPreferencesProps}
			onAccountDeleted={handleAccountDeleted}
		/>
	);
}
