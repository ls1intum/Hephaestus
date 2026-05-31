import {
	AdminFeaturesSettings,
	type FeatureKey,
	type FeatureValues,
} from "./AdminFeaturesSettings";
import { AdminLeagueSettings } from "./AdminLeagueSettings";
import { AdminRepositoriesSettings } from "./AdminRepositoriesSettings";
import { AdminSlackNotificationSettings } from "./AdminSlackNotificationSettings";
import { LoginProvidersSettings } from "./login-providers/LoginProvidersSettings";

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
	// Slack notification card props (rendered for any workspace with a slug — the weekly
	// digest is a Slack feature, independent of whether the leaderboard page is enabled).
	workspaceSlug?: string;
	hasSlackConnection: boolean;
	slackChannelId?: string;
	slackTeamLabel?: string;
	slackNotificationsEnabled: boolean;
	slackScheduleDay?: number;
	slackScheduleTime?: string;
	onSlackSaved: () => void;
	/** API origin used to build OAuth callback URLs for self-hosted login providers. */
	apiOrigin: string;
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
	workspaceSlug,
	hasSlackConnection,
	slackChannelId,
	slackTeamLabel,
	slackNotificationsEnabled,
	slackScheduleDay,
	slackScheduleTime,
	onSlackSaved,
	apiOrigin,
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

				{workspaceSlug != null && (
					<LoginProvidersSettings workspaceSlug={workspaceSlug} apiOrigin={apiOrigin} />
				)}

				{features.leaguesEnabled && (
					<AdminLeagueSettings isResetting={isResettingLeagues} onResetLeagues={onResetLeagues} />
				)}

				{workspaceSlug != null && (
					<AdminSlackNotificationSettings
						workspaceSlug={workspaceSlug}
						hasSlackConnection={hasSlackConnection}
						channelId={slackChannelId}
						teamLabel={slackTeamLabel}
						enabled={slackNotificationsEnabled}
						scheduleDay={slackScheduleDay}
						scheduleTime={slackScheduleTime}
						onSaved={onSlackSaved}
					/>
				)}
			</div>
		</div>
	);
}
