import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, userEvent, within } from "storybook/test";
import {
	mockConversationBinding,
	mockIssueBinding,
	mockMergeBinding,
	mockPracticeDefinitionOptions,
	mockPullRequestBinding,
} from "@/mocks/fixtures/practice";
import { ADD_BINDING_ID } from "./bindings";
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

/**
 * A submit sends focus to the control that has to change. The message has to travel with it: on its
 * own it is text somewhere else on a long form, and an author who lands in the group hears its name
 * and nothing about what is wrong.
 */
export const Invalid: Story = {
	args: {
		bindings: [{ signals: [], needs: [] }],
		error: "Choose when this occasion starts a review.",
		errorFocusId: "practice-binding-0-signals",
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByText("No moment chosen yet")).toBeVisible();
		await expect(
			canvas.getByRole("group", { name: "Starts a review when, occasion 1" }),
		).toHaveAccessibleDescription("Choose when this occasion starts a review.");
		// Only the faulted group carries it — describing every group with it would make the message
		// mean "something on this form is wrong" rather than "this is the thing to change".
		await expect(
			canvas.getByRole("group", { name: "What this review reads, occasion 1" }),
		).not.toHaveAccessibleDescription("Choose when this occasion starts a review.");
	},
};

/** With no occasion at all there is no group to fault, so the message rides the action that fixes it. */
export const NoOccasionAtAll: Story = {
	args: {
		bindings: [],
		error: "Add at least one occasion that starts a review.",
		errorFocusId: ADD_BINDING_ID,
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("button", { name: "Add occasion" })).toHaveAccessibleDescription(
			"Add at least one occasion that starts a review.",
		);
	},
};

/**
 * The write path, which nothing exercised: adding an occasion, choosing a moment for it, and removing
 * it again. Each reports the whole list back, because the editor holds no state of its own.
 */
export const AddingAnOccasion: Story = {
	play: async ({ args, canvas }) => {
		await userEvent.click(canvas.getByRole("button", { name: "Add occasion" }));

		// Every recommended moment already belongs to occasion 1, so the new one takes the first still
		// free rather than a moment the server would refuse for being bound twice.
		await expect(args.onChange).toHaveBeenCalledWith([
			mockPullRequestBinding,
			{ signals: ["scm.pull_request.reviewed"], needs: mockPullRequestBinding.needs },
		]);
	},
};

export const ChoosingAMoment: Story = {
	play: async ({ args, canvas }) => {
		const group = within(canvas.getByRole("group", { name: "Starts a review when, occasion 1" }));
		await userEvent.click(group.getByRole("checkbox", { name: "Review submitted" }));

		// Sorted on the way out, the way the server stores it: unsorted, reloading an untouched
		// practice and saving nothing would come back looking edited.
		await expect(args.onChange).toHaveBeenCalledWith([
			{
				signals: [
					"scm.pull_request.opened",
					"scm.pull_request.ready",
					"scm.pull_request.reviewed",
					"scm.pull_request.synchronized",
				],
				needs: mockPullRequestBinding.needs,
			},
		]);
	},
};

export const RemovingAnOccasion: Story = {
	args: { bindings: [mockPullRequestBinding, mockMergeBinding] },
	play: async ({ args, canvas }) => {
		await userEvent.click(canvas.getByRole("button", { name: "Remove occasion 2" }));

		await expect(args.onChange).toHaveBeenCalledWith([mockPullRequestBinding]);
	},
};
