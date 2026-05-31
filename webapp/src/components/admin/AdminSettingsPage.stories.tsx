import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import type { FeatureValues } from "./AdminFeaturesSettings";
import { AdminSettingsPage } from "./AdminSettingsPage";

const allOff: FeatureValues = {
	practicesEnabled: false,
	mentorEnabled: false,
	achievementsEnabled: false,
	leaderboardEnabled: false,
	progressionEnabled: false,
	leaguesEnabled: false,
	practiceReviewAutoTriggerEnabled: true,
	practiceReviewManualTriggerEnabled: true,
};

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
		isResettingLeagues: false,
		isAppInstallationWorkspace: false,
		onAddRepository: fn(),
		onRemoveRepository: fn(),
		onResetLeagues: fn(),
		features: allOff,
		isSavingFeatures: false,
		onToggleFeature: fn(),
		workspaceSlug: "demo",
		hasSlackConnection: false,
		slackNotificationsEnabled: false,
		onSlackSaved: fn(),
		apiOrigin: "https://hephaestus.example.com",
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

export const ResettingLeagues: Story = { args: { isResettingLeagues: true } };

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

/** Slack connected + configured — pins that the Slack digest card renders within the page. */
export const SlackConfigured: Story = {
	args: {
		hasSlackConnection: true,
		slackChannelId: "C0974LJBPBK",
		slackNotificationsEnabled: true,
		slackScheduleDay: 3,
		slackScheduleTime: "09:00",
	},
};
