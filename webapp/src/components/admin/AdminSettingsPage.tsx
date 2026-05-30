import {
	AdminFeaturesSettings,
	type FeatureKey,
	type FeatureValues,
} from "./AdminFeaturesSettings";
import { AdminLeagueSettings } from "./AdminLeagueSettings";
import { AdminRepositoriesSettings } from "./AdminRepositoriesSettings";
import { AdminSlackNotificationSettings } from "./AdminSlackNotificationSettings";

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
	onAddRepository: (nameWithOwner: string) => void;
	onRemoveRepository: (nameWithOwner: string) => void;
	onResetLeagues: () => void;
	features: FeatureValues;
	isSavingFeatures: boolean;
	onToggleFeature: (feature: FeatureKey, enabled: boolean) => void;
	// Slack notification card props (only rendered when leaderboard is enabled)
	workspaceId?: number;
	workspaceSlug?: string;
	hasSlackConnection: boolean;
	slackChannelId?: string;
	slackTeamLabel?: string;
	slackNotificationsEnabled: boolean;
	onSlackSaved: () => void;
}

export function AdminSettingsPage({
	repositories,
	isLoadingRepositories,
	repositoriesError,
	addRepositoryError,
	isAddingRepository,
	isRemovingRepository,
	isResettingLeagues,
	isAppInstallationWorkspace = false,
	onAddRepository,
	onRemoveRepository,
	onResetLeagues,
	features,
	isSavingFeatures,
	onToggleFeature,
	workspaceId,
	workspaceSlug,
	hasSlackConnection,
	slackChannelId,
	slackTeamLabel,
	slackNotificationsEnabled,
	onSlackSaved,
}: AdminSettingsPageProps) {
	return (
		<div className="container mx-auto py-6 max-w-4xl">
			<h1 className="text-3xl font-bold mb-8">Workspace Settings</h1>

			<div className="space-y-10">
				<AdminFeaturesSettings
					values={features}
					isSaving={isSavingFeatures}
					onToggle={onToggleFeature}
				/>

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

				{features.leaguesEnabled && (
					<AdminLeagueSettings isResetting={isResettingLeagues} onResetLeagues={onResetLeagues} />
				)}

				{features.leaderboardEnabled && workspaceId != null && workspaceSlug != null && (
					<AdminSlackNotificationSettings
						workspaceSlug={workspaceSlug}
						hasSlackConnection={hasSlackConnection}
						channelId={slackChannelId}
						teamLabel={slackTeamLabel}
						enabled={slackNotificationsEnabled}
						onSaved={onSlackSaved}
					/>
				)}
			</div>
		</div>
	);
}
