import type { SlackMonitoredChannel } from "@/api/types.gen";
import {
	AdminFeaturesSettings,
	type CohortVisibility,
	type FeatureKey,
	type FeatureValues,
} from "./AdminFeaturesSettings";
import { AdminRepositoriesSettings } from "./AdminRepositoriesSettings";
import { AdminReviewCycleSettings } from "./AdminReviewCycleSettings";
import {
	AdminSlackChannelsSettings,
	type SlackChannelCandidate,
	type SlackConsentState,
} from "./AdminSlackChannelsSettings";
import { AdminSlackConnectionSettings } from "./AdminSlackConnectionSettings";

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
	/** Whether repository management is disabled (for GitHub App Installation workspaces) */
	isAppInstallationWorkspace?: boolean;
	onAddRepository: (nameWithOwner: string) => void;
	onRemoveRepository: (nameWithOwner: string) => void;
	features: FeatureValues;
	cohortVisibility: CohortVisibility;
	isSavingFeatures: boolean;
	onToggleFeature: (feature: FeatureKey, enabled: boolean) => void;
	onCohortVisibilityChange: (visibility: CohortVisibility) => void;
	// Review cycle (weekly practice-review window)
	workspaceSlug?: string;
	reviewCycleDay?: number;
	reviewCycleTime?: string;
	// Slack connection card props (rendered for any workspace with a slug — the Slack connection is
	// independent of the review cycle).
	hasSlackConnection: boolean;
	slackConnectionId?: number;
	/** Refetch the workspace snapshot after any settings card saves (Slack, review cycle, …). */
	onWorkspaceRefetch: () => void;
	// Slack channel-monitoring section (rendered directly below the Slack card).
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
	isAppInstallationWorkspace = false,
	onAddRepository,
	onRemoveRepository,
	features,
	cohortVisibility,
	isSavingFeatures,
	onToggleFeature,
	onCohortVisibilityChange,
	workspaceSlug,
	reviewCycleDay,
	reviewCycleTime,
	hasSlackConnection,
	slackConnectionId,
	onWorkspaceRefetch,
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
			<h1 className="text-3xl font-bold mb-8">Workspace Settings</h1>

			<div className="space-y-10">
				<AdminFeaturesSettings
					values={features}
					cohortVisibility={cohortVisibility}
					isSaving={isSavingFeatures}
					onToggle={onToggleFeature}
					onCohortVisibilityChange={onCohortVisibilityChange}
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
					<AdminReviewCycleSettings
						key={`review-cycle:${reviewCycleDay ?? ""}:${reviewCycleTime ?? ""}`}
						workspaceSlug={workspaceSlug}
						day={reviewCycleDay}
						time={reviewCycleTime}
						onSaved={onWorkspaceRefetch}
					/>
				)}

				{workspaceSlug != null && (
					// key derived from the server snapshot: only OAuth connect/disconnect changes
					// slackConnectionId, so when the post-OAuth refetch lands the key changes and
					// React remounts the card with fresh server truth instead of leaning on
					// prop→state sync effects.
					<AdminSlackConnectionSettings
						key={`slack:${slackConnectionId ?? "none"}`}
						workspaceSlug={workspaceSlug}
						hasSlackConnection={hasSlackConnection}
						slackConnectionId={slackConnectionId}
						onSaved={onWorkspaceRefetch}
					/>
				)}

				{workspaceSlug != null && hasSlackConnection && (
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
			</div>
		</div>
	);
}
