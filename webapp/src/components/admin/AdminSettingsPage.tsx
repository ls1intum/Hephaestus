import type { SlackMonitoredChannel } from "@/api/types.gen";
import {
	AdminFeaturesSettings,
	type FeatureKey,
	type FeatureValues,
} from "./AdminFeaturesSettings";
import { AdminLeagueSettings } from "./AdminLeagueSettings";
import { AdminOutlineSettings } from "./AdminOutlineSettings";
import { AdminRepositoriesSettings } from "./AdminRepositoriesSettings";
import {
	AdminSlackChannelsSettings,
	type SlackChannelCandidate,
	type SlackConsentState,
} from "./AdminSlackChannelsSettings";
import { AdminSlackNotificationSettings } from "./AdminSlackNotificationSettings";

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
	// Slack integration card props. The card owns connection state and the weekly digest
	// controls; the channel-monitoring section renders in either state (inert, with a pointer
	// back to this card, while Slack is not connected).
	workspaceSlug?: string;
	hasSlackConnection: boolean;
	slackConnectionId?: number;
	slackChannelId?: string;
	slackTeamLabel?: string;
	slackNotificationsEnabled: boolean;
	slackScheduleDay?: number;
	slackScheduleTime?: string;
	onSlackSaved: () => void;
	// Slack channel-monitoring section (rendered directly below the notifications card).
	slackChannels: SlackMonitoredChannel[];
	slackChannelCandidates: SlackChannelCandidate[];
	isLoadingSlackChannels: boolean;
	isSlackChannelsError?: boolean;
	onRetrySlackChannels?: () => void;
	onRegisterSlackChannel: (input: {
		slackChannelId: string;
		channelName?: string;
	}) => Promise<void> | void;
	onUpdateSlackChannelConsent: (input: {
		slackChannelId: string;
		consentState: SlackConsentState;
		reason?: string;
	}) => Promise<void> | void;
	onRemoveSlackChannel: (input: {
		slackChannelId: string;
		reason?: string;
	}) => Promise<void> | void;
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
	slackConnectionId,
	slackChannelId,
	slackTeamLabel,
	slackNotificationsEnabled,
	slackScheduleDay,
	slackScheduleTime,
	onSlackSaved,
	slackChannels,
	slackChannelCandidates,
	isLoadingSlackChannels,
	isSlackChannelsError = false,
	onRetrySlackChannels,
	onRegisterSlackChannel,
	onUpdateSlackChannelConsent,
	onRemoveSlackChannel,
}: AdminSettingsPageProps) {
	return (
		<div className="container mx-auto py-6 max-w-4xl">
			<h1 className="text-3xl font-bold mb-8">Workspace settings</h1>

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

				{workspaceSlug != null && (
					// key derived from the server snapshot: when a post-OAuth/save refetch lands,
					// the key changes and React remounts the form with fresh server truth instead
					// of leaning on prop→state sync effects.
					<AdminSlackNotificationSettings
						key={`slack:${slackConnectionId ?? "none"}:${slackChannelId ?? ""}:${slackNotificationsEnabled}:${slackScheduleDay ?? ""}:${slackScheduleTime ?? ""}:${slackTeamLabel ?? ""}`}
						workspaceSlug={workspaceSlug}
						hasSlackConnection={hasSlackConnection}
						slackConnectionId={slackConnectionId}
						channelId={slackChannelId}
						teamLabel={slackTeamLabel}
						enabled={slackNotificationsEnabled}
						scheduleDay={slackScheduleDay}
						scheduleTime={slackScheduleTime}
						channelCandidates={slackChannelCandidates}
						onSaved={onSlackSaved}
					/>
				)}

				{/* Rendered whether or not Slack is connected: an admin has to be able to discover what
				    channel monitoring is before deciding to install the app. Without a connection the
				    section explains itself and points back at the card above. */}
				{workspaceSlug != null && (
					<AdminSlackChannelsSettings
						workspaceSlug={workspaceSlug}
						hasSlackConnection={hasSlackConnection}
						channels={slackChannels}
						channelCandidates={slackChannelCandidates}
						isLoading={isLoadingSlackChannels}
						isError={isSlackChannelsError}
						onRetry={onRetrySlackChannels}
						onRegisterChannel={onRegisterSlackChannel}
						onUpdateConsent={onUpdateSlackChannelConsent}
						onRemoveChannel={onRemoveSlackChannel}
					/>
				)}

				{workspaceSlug != null && <AdminOutlineSettings workspaceSlug={workspaceSlug} />}
			</div>
		</div>
	);
}
