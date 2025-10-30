import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { toast } from "sonner";
import {
	deleteUserMutation,
	getUserSettingsOptions,
	getUserSettingsQueryKey,
	updateUserSettingsMutation,
} from "@/api/@tanstack/react-query.gen";
import { SettingsPage } from "@/components/settings/SettingsPage";
import { useAuth } from "@/integrations/auth/AuthContext";

export const Route = createFileRoute("/_authenticated/settings")({
	component: RouteComponent,
});

function RouteComponent() {
	const navigate = useNavigate();
	const queryClient = useQueryClient();
	const { logout } = useAuth();

	// Query for user settings
	const { data: settings, isLoading } = useQuery({
		...getUserSettingsOptions({}),
	});

	// Mutation for updating user settings
	const updateSettingsMutation = useMutation({
		...updateUserSettingsMutation(),
		onSuccess: () => {
			queryClient.invalidateQueries({
				queryKey: getUserSettingsQueryKey(),
			});
		},
		onError: (error) => {
			console.error("Failed to update user settings:", error);
			toast.error("Failed to update settings. Please try again later.");
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

	// Handle toggle change for notifications
	const handleNotificationToggle = (checked: boolean) => {
		if (!settings) {
			return;
		}
		updateSettingsMutation.mutate({
			body: {
				receiveNotifications: checked,
				participateInResearch: settings.participateInResearch,
			},
		});
	};

	const handleResearchToggle = (checked: boolean) => {
		if (!settings) {
			return;
		}
		updateSettingsMutation.mutate({
			body: {
				participateInResearch: checked,
				receiveNotifications: settings.receiveNotifications,
			},
		});
	};

	// Handle account deletion
	const handleDeleteAccount = () => {
		deleteAccountMutation.mutate({});
	};

	return (
		<SettingsPage
			isLoading={isLoading}
			notificationsProps={{
				receiveNotifications: settings?.receiveNotifications ?? false,
				onToggleNotifications: handleNotificationToggle,
				isLoading: updateSettingsMutation.isPending,
			}}
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
