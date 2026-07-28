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

// The danger zone fetches its own role check, so this page's stories have to answer it. Deleting
// the handler breaks the page silently — an unhandled request is not an error, it falls through to
// the dev server, which answers with the app's HTML, and the section renders "couldn't confirm your
// role" instead. The Default play below is what turns that silence into a failure.
const membershipRead = [
	http.get("*/workspaces/:workspaceSlug/members/me", () =>
		HttpResponse.json({ role: "OWNER", userLogin: "ada" }),
	),
];

const meta = {
	component: AdminSettingsPage,
	parameters: { layout: "padded", msw: { handlers: membershipRead } },
	tags: ["autodocs"],
	args: {
		isResettingLeagues: false,
		onResetLeagues: fn(),
		features: allOff,
		isSavingFeatures: false,
		onToggleFeature: fn(),
		workspaceSlug: "ase",
	},
} satisfies Meta<typeof AdminSettingsPage>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Every feature off — the Features section still renders; only the league card is conditional. */
export const Default: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByRole("heading", { name: /^features$/i })).toBeInTheDocument();
		await expect(canvas.queryByText(/reset and recalculate leagues/i)).not.toBeInTheDocument();
		// Pins the danger zone to its owner state: unmocked, the role query errors and the section
		// snapshots as "couldn't confirm your role" with the button permanently unavailable.
		const deleteButton = await canvas.findByRole("button", { name: /^delete workspace$/i });
		await waitFor(() => expect(deleteButton).toHaveAttribute("aria-disabled", "false"));
	},
};

export const ResettingLeagues: Story = {
	args: { isResettingLeagues: true, features: { ...allOff, leaguesEnabled: true } },
};

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

export const LeaguesEnabled: Story = {
	args: { features: { ...allOff, leaguesEnabled: true } },
};

/** Active workspace still resolving — the danger zone stays out rather than guessing a slug. */
export const WorkspaceUnresolved: Story = {
	args: { workspaceSlug: undefined },
};
