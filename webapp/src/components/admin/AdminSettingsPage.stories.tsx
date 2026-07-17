import type { Meta, StoryObj } from "@storybook/react";
import { HttpResponse, http } from "msw";
import { expect, fn, waitFor, within } from "storybook/test";
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

// Two sections on this page fetch for themselves, so the page's stories have to answer them: the
// danger zone reads the caller's role, and the Outline card reads the workspace's connections.
// Deleting either handler breaks the page silently — an unhandled request is not an error, it
// falls through to the dev server, which answers with the app's HTML. The role query then errors
// (the Default play below catches that) and the Outline card throws on a string it expects to be
// a list, after the play has already finished (nothing catches that).
const selfFetchedReads = [
	http.get("*/workspaces/:workspaceSlug/members/me", () =>
		HttpResponse.json({ role: "OWNER", userLogin: "ada" }),
	),
	http.get("*/workspaces/:workspaceSlug/connections", () => HttpResponse.json([])),
];

const meta = {
	component: AdminSettingsPage,
	parameters: { layout: "padded", msw: { handlers: selfFetchedReads } },
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

export const Default: Story = {
	play: async ({ canvasElement }) => {
		// Pins the danger zone to its real state: unmocked, the role query errors and the whole
		// page snapshots as "couldn't confirm your role".
		const canvas = within(canvasElement);
		const deleteButton = await canvas.findByRole("button", { name: /^delete workspace$/i });
		await waitFor(() => expect(deleteButton).toHaveAttribute("aria-disabled", "false"));
	},
};

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
