import { useEffect } from "react";
import { toast } from "sonner";
import { AdminLeagueSettings } from "./AdminLeagueSettings";
import { AdminRepositoriesSettings } from "./AdminRepositoriesSettings";
import { AdminSlackSettings } from "./AdminSlackSettings";

// Use the RepositoryItem type from the AdminRepositoriesSettings component
type RepositoryItem = {
	nameWithOwner: string;
};

export interface AdminSettingsPageProps {
	repositories: RepositoryItem[];
	isLoadingRepositories: boolean;
	repositoriesError: Error | null;
	addRepositoryError: Error | null;
	isAddingRepository: boolean;
	isRemovingRepository: boolean;
	isResettingLeagues: boolean;
	/** Whether repository management is disabled (for GitHub App Installation workspaces) */
	isAppInstallationWorkspace?: boolean;
	workspaceSlug: string;
	hasSlackToken: boolean;
	/** Status from Slack OAuth callback: 'success' | 'error' | 'cancelled' | 'invalid' | undefined */
	slackStatus?: string | null;
	onAddRepository: (nameWithOwner: string) => void;
	onRemoveRepository: (nameWithOwner: string) => void;
	onResetLeagues: () => void;
}

/**
 * Presentational component for the admin settings page
 */
export function AdminSettingsPage({
	repositories,
	isLoadingRepositories,
	repositoriesError,
	addRepositoryError,
	isAddingRepository,
	isRemovingRepository,
	isResettingLeagues,
	isAppInstallationWorkspace = false,
	workspaceSlug,
	hasSlackToken,
	slackStatus,
	onAddRepository,
	onRemoveRepository,
	onResetLeagues,
}: AdminSettingsPageProps) {
	// Show toast notification based on Slack OAuth callback status
	useEffect(() => {
		if (!slackStatus) return;

		switch (slackStatus) {
			case "success":
				toast.success("Slack Connected", {
					description:
						"The Hephaestus bot has been installed to your Slack workspace.",
				});
				break;
			case "error":
				toast.error("Slack Connection Failed", {
					description:
						"An error occurred while connecting to Slack. Please try again.",
				});
				break;
			case "cancelled":
				toast.info("Slack Connection Cancelled", {
					description:
						"You cancelled the Slack authorization. No changes were made.",
				});
				break;
			case "invalid":
				toast.error("Invalid Authorization", {
					description:
						"The Slack authorization link has expired or is invalid. Please try again.",
				});
				break;
		}
	}, [slackStatus]);

	return (
		<div className="container mx-auto max-w-4xl py-6">
			<h1 className="mb-8 font-bold text-3xl">Workspace Settings</h1>

			<div className="space-y-10">
				{/* Repositories Settings */}
				<AdminRepositoriesSettings
					repositories={repositories}
					isLoading={isLoadingRepositories}
					error={repositoriesError}
					addRepositoryError={addRepositoryError}
					isAddingRepository={isAddingRepository}
					isRemovingRepository={isRemovingRepository}
					isReadOnly={isAppInstallationWorkspace}
					onAddRepository={onAddRepository}
					onRemoveRepository={onRemoveRepository}
				/>

				{/* Slack Settings */}
				<AdminSlackSettings
					workspaceSlug={workspaceSlug}
					hasSlackToken={hasSlackToken}
				/>

				{/* Leagues Settings */}
				<AdminLeagueSettings
					isResetting={isResettingLeagues}
					onResetLeagues={onResetLeagues}
				/>
			</div>
		</div>
	);
}
