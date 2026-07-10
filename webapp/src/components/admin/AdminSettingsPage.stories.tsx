import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import type { FeatureValues } from "./AdminFeaturesSettings";
import { AdminSettingsPage } from "./AdminSettingsPage";

const allOff: FeatureValues = {
	practicesEnabled: false,
	mentorEnabled: false,
	achievementsEnabled: false,
	practiceReviewAutoTriggerEnabled: true,
	practiceReviewManualTriggerEnabled: true,
};

/** Admin workspace settings page composing the feature, repository, team, and review-cycle editors. */
const meta = {
	component: AdminSettingsPage,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
		repositories: [
			{ nameWithOwner: "octocat/Hello-World" },
			{ nameWithOwner: "microsoft/vscode" },
			{ nameWithOwner: "facebook/react" },
		],
		isLoadingRepositories: false,
		repositoriesError: null,
		addRepositoryError: null,
		isAddingRepository: false,
		isRemovingRepository: false,
		isAppInstallationWorkspace: false,
		onAddRepository: fn(),
		onRemoveRepository: fn(),
		features: allOff,
		cohortVisibility: "MENTORS_ONLY",
		isSavingFeatures: false,
		onToggleFeature: fn(),
		onCohortVisibilityChange: fn(),
		workspaceSlug: "demo",
		reviewCycleDay: 1,
		reviewCycleTime: "09:00",
		hasSlackConnection: false,
		onWorkspaceRefetch: fn(),
		slackChannels: [],
		slackChannelCandidates: [],
		isLoadingSlackChannels: false,
		onRegisterSlackChannel: fn(),
		onUpdateSlackChannelConsent: fn(),
		onRemoveSlackChannel: fn(),
	},
} satisfies Meta<typeof AdminSettingsPage>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};

export const LoadingRepositories: Story = {
	args: { isLoadingRepositories: true, repositories: [] },
};

export const RepositoriesError: Story = {
	args: { repositoriesError: new Error("Failed to load repositories"), repositories: [] },
};

export const AddingRepository: Story = { args: { isAddingRepository: true } };

/** GitHub App Installation workspace — repository management is read-only. */
export const AppInstallationWorkspace: Story = { args: { isAppInstallationWorkspace: true } };

/** Practice Review on with auto-trigger only — exercises the nested sub-toggle layout. */
export const PracticeReviewWithSubToggles: Story = {
	args: {
		features: {
			...allOff,
			practicesEnabled: true,
			practiceReviewAutoTriggerEnabled: true,
			practiceReviewManualTriggerEnabled: false,
		},
	},
};

/** Slack connected — pins that the Slack connection card renders within the page. */
export const SlackConfigured: Story = {
	args: {
		hasSlackConnection: true,
		slackConnectionId: 7,
	},
};

/** Everyone — members can additionally see the anonymised cohort insights. */
export const EveryoneVisibility: Story = {
	args: { cohortVisibility: "EVERYONE" },
};
