import {
	type DefaultError,
	useMutation,
	useQuery,
	useQueryClient,
} from "@tanstack/react-query";
import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { toast } from "sonner";
import {
	deleteUserMutation,
	disconnectSlackMutation,
	getSlackConnectionOptions,
	getSlackConnectionQueryKey,
	getUserSettingsOptions,
	getUserSettingsQueryKey,
	syncSlackConnectionMutation,
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

export const Route = createFileRoute("/_authenticated/settings")({
	component: RouteComponent,
});

function RouteComponent() {
	const navigate = useNavigate();
	const client = useQueryClient();
	const { logout } = useAuth();
	const settingsKey = getUserSettingsQueryKey();

	const { data: settings, isLoading } = useQuery({
		...getUserSettingsOptions({}),
		retry: 1,
	});

	const updateMutation = useMutation<
		UpdateUserSettingsResponse,
		DefaultError,
		Options<UpdateUserSettingsData>,
		{ previous?: UserSettings }
	>({
		...updateUserSettingsMutation(),
		onMutate: async (variables) => {
			await client.cancelQueries({ queryKey: settingsKey });
			const previous = client.getQueryData<UserSettings>(settingsKey);
			if (variables.body) {
				client.setQueryData(settingsKey, variables.body);
			}
			return { previous };
		},
		onError: (_error, _variables, context) => {
			if (context?.previous) {
				client.setQueryData(settingsKey, context.previous);
			}
			toast.error("Failed to update settings.");
		},
		onSuccess: (data) => client.setQueryData(settingsKey, data),
		onSettled: () => client.invalidateQueries({ queryKey: settingsKey }),
	});

	const deleteMutation = useMutation({
		...deleteUserMutation(),
		onSuccess: async () => {
			await logout();
			navigate({ to: "/" });
		},
		onError: () => toast.error("Failed to delete account."),
	});

	const handleNotificationToggle = (checked: boolean) => {
		if (!settings) return;
		updateMutation.mutate({
			body: {
				receiveNotifications: checked,
				participateInResearch: settings.participateInResearch,
			},
		});
	};

	const handleResearchToggle = (checked: boolean) => {
		if (!settings) return;
		updateMutation.mutate({
			body: {
				participateInResearch: checked,
				receiveNotifications: settings.receiveNotifications,
			},
		});
	};

	const { data: slack, isLoading: slackLoading } = useQuery({
		...getSlackConnectionOptions(),
	});

	const syncMutation = useMutation({
		...syncSlackConnectionMutation(),
		onSuccess: (data) => {
			client.setQueryData(getSlackConnectionQueryKey(), data);
			toast.success("Slack synced");
		},
		onError: () => toast.error("Failed to sync Slack."),
	});

	const disconnectMutation = useMutation({
		...disconnectSlackMutation(),
		onSuccess: (data) => {
			client.setQueryData(getSlackConnectionQueryKey(), data);
			toast.success("Slack disconnected");
		},
		onError: () => toast.error("Failed to disconnect Slack."),
	});

	return (
		<SettingsPage
			isLoading={isLoading}
			notificationsProps={{
				receiveNotifications: settings?.receiveNotifications ?? false,
				onToggleNotifications: handleNotificationToggle,
				isLoading: updateMutation.isPending,
			}}
			researchProps={{
				participateInResearch: settings?.participateInResearch ?? true,
				onToggleResearch: handleResearchToggle,
				isLoading: updateMutation.isPending,
			}}
			slackProps={{
				isConnected: slack?.connected ?? false,
				slackUserId: slack?.slackUserId,
				slackEnabled: slack?.slackEnabled ?? false,
				linkUrl: slack?.linkUrl,
				onDisconnect: () => disconnectMutation.mutate({}),
				onSync: () => syncMutation.mutate({}),
				isLoading:
					slackLoading ||
					syncMutation.isPending ||
					disconnectMutation.isPending,
			}}
			accountProps={{
				onDeleteAccount: () => deleteMutation.mutate({}),
				isDeleting: deleteMutation.isPending,
			}}
		/>
	);
}
