import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, within } from "storybook/test";
import {
	mockConversationBinding,
	mockIssueBinding,
	mockMergeBinding,
	mockPracticeDefinitionOptions,
	mockPullRequestBinding,
} from "@/mocks/fixtures/practice";
import { PracticeBindingsEditor } from "./PracticeBindingsEditor";

const pullRequests = mockPracticeDefinitionOptions.workTypes[0];
const issues = mockPracticeDefinitionOptions.workTypes[1];
const conversations = mockPracticeDefinitionOptions.workTypes[2];

const meta = {
	title: "Workspace admin/Practices/Occasions",
	component: PracticeBindingsEditor,
	args: {
		options: pullRequests,
		bindings: [mockPullRequestBinding],
		onChange: fn(),
	},
	parameters: { layout: "padded" },
	tags: ["autodocs"],
} satisfies Meta<typeof PracticeBindingsEditor>;

export default meta;
type Story = StoryObj<typeof meta>;

/** What a new practice starts as: one occasion, on the moments work arrives to look at. */
export const OneOccasion: Story = {
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Occasion 1")).toBeVisible();
		// Nothing to remove when there is only one: a practice with no occasion cannot be saved.
		await expect(canvas.queryByRole("button", { name: "Remove occasion 1" })).toBeNull();
	},
};

/**
 * The shape the refactor exists to express. The same habit, reviewed twice: once as the work arrives,
 * and once at the merge — where reading the review threads whole is what licenses the review to say
 * nobody ever resolved one.
 */
export const TwoOccasionsReadingDifferentThings: Story = {
	args: { bindings: [mockPullRequestBinding, mockMergeBinding] },
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Occasion 2")).toBeVisible();
		await expect(
			within(canvas.getByRole("group", { name: "Starts a review when, occasion 2" })).getByRole(
				"checkbox",
				{ name: "Merged" },
			),
		).toBeChecked();
		await expect(
			within(canvas.getByRole("group", { name: "What this review reads, occasion 2" })).getByText(
				/and nothing missing from it/,
			),
		).toBeVisible();
	},
};

/**
 * A moment belongs to one occasion. The server rejects a signal bound twice, and the refusal would
 * otherwise arrive as a failed save with nothing on screen explaining it.
 */
export const AMomentBelongsToOneOccasion: Story = {
	args: { bindings: [mockPullRequestBinding, mockMergeBinding] },
	play: async ({ canvas }) => {
		const second = within(canvas.getByRole("group", { name: "Starts a review when, occasion 2" }));
		await expect(second.getByRole("checkbox", { name: /^Opened/ })).toHaveAttribute(
			"aria-disabled",
			"true",
		);
		await expect(second.getAllByText(/used by another occasion/)).toHaveLength(3);
	},
};

/** Every moment claimed: adding another occasion would have nothing left to start it. */
export const EveryMomentClaimed: Story = {
	args: {
		options: conversations,
		bindings: [mockConversationBinding],
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("button", { name: "Add occasion" })).toBeDisabled();
		await expect(canvas.getByText(/already claimed by an occasion/)).toBeVisible();
	},
};

/** An issue is never a draft, so the question is not asked. */
export const IssuesHaveNoDrafts: Story = {
	args: { options: issues, bindings: [mockIssueBinding] },
	play: async ({ canvas }) => {
		await expect(
			canvas.queryByRole("checkbox", { name: /Also while it is still a draft/ }),
		).toBeNull();
	},
};

/** Pull requests do, and it is per occasion rather than a switch across the whole fleet. */
export const DraftsAreAPropertyOfTheOccasion: Story = {
	args: {
		bindings: [{ ...mockPullRequestBinding, onDrafts: true }, mockMergeBinding],
	},
	play: async ({ canvas }) => {
		const drafts = canvas.getAllByRole("checkbox", { name: /Also while it is still a draft/ });
		await expect(drafts[0]).toBeChecked();
		await expect(drafts[1]).not.toBeChecked();
	},
};

/** With no review running, an occasion reads nothing — and saying so is clearer than an empty list. */
export const GuidanceOnly: Story = {
	args: {
		guidanceOnly: true,
		bindings: [{ ...mockPullRequestBinding, needs: [] }],
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByText(/reads nothing, because no review runs/)).toBeVisible();
	},
};

/** How the requirements have actually fared, shown once for the practice rather than per occasion. */
export const WithRecentOutcomes: Story = {
	args: {
		outcome: {
			practiceSlug: "clear-pr-description",
			consideredReviews: 12,
			reviewedCount: 7,
			blockersObserved: [
				{ sourceKind: "scm.pull-request.diff", reasonCode: "SOURCE_EMPTY", reviewsAffected: 4 },
				{
					sourceKind: "scm.pull-request.comments",
					reasonCode: "SOURCE_INCOMPLETE",
					reviewsAffected: 1,
				},
			],
		},
	},
	play: async ({ canvas }) => {
		await expect(
			canvas.getByText(/skipped this practice in 5 of the last 12 reviews/),
		).toBeVisible();
	},
};

export const Invalid: Story = {
	args: {
		bindings: [{ signals: [], needs: [] }],
		error: "Choose when this occasion starts a review.",
		errorFocusId: "practice-binding-0-signals",
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByText("No moment chosen yet")).toBeVisible();
		await expect(canvas.getByRole("alert")).toHaveTextContent(
			"Choose when this occasion starts a review.",
		);
	},
};
