import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, userEvent, within } from "storybook/test";
import {
	mockConversationBinding,
	mockConversationWorkType,
	mockDocumentBinding,
	mockDocumentWorkType,
	mockIssueBinding,
	mockIssueWorkType,
	mockMergeBinding,
	mockPullRequestBinding,
	mockPullRequestWorkType,
} from "@/mocks/fixtures/practice";
import { Stateful } from "@/stories/stateful";
import { ADD_BINDING_ID } from "./bindings";
import { PracticeBindingsEditor } from "./PracticeBindingsEditor";
import { outcome } from "./story-mock-data";

const meta = {
	title: "Workspace admin/Practices/Occasions",
	component: PracticeBindingsEditor,
	args: {
		options: mockPullRequestWorkType,
		bindings: [mockPullRequestBinding],
		onChange: fn(),
	},
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	render: (args) => (
		<Stateful initial={args.bindings}>
			{(bindings, setBindings) => (
				<PracticeBindingsEditor
					{...args}
					bindings={bindings}
					onChange={(next) => {
						args.onChange(next);
						setBindings(next);
					}}
				/>
			)}
		</Stateful>
	),
} satisfies Meta<typeof PracticeBindingsEditor>;

export default meta;
type Story = StoryObj<typeof meta>;

export const PullRequestLifecycle: Story = {
	play: async ({ canvas }) => {
		const strip = within(canvas.getByRole("group", { name: "Reviews when, occasion 1" }));
		await expect(strip.getByRole("checkbox", { name: /^Opened/ })).toBeChecked();
		await expect(strip.getByRole("checkbox", { name: /^Merged/ })).not.toBeChecked();
		await expect(
			strip.getByRole("checkbox", { name: "New commits pushed every time" }),
		).toBeVisible();
		// Nothing to remove when there is only one: a practice with no occasion cannot be saved.
		await expect(canvas.queryByRole("button", { name: "Remove occasion 1" })).toBeNull();
	},
};

export const IssueLifecycle: Story = {
	args: { options: mockIssueWorkType, bindings: [mockIssueBinding] },
	play: async ({ canvas }) => {
		const strip = within(canvas.getByRole("group", { name: "Reviews when, occasion 1" }));
		await expect(strip.getByRole("checkbox", { name: "Labeled every time" })).toBeChecked();
		// An issue is never a draft, so the question is not asked.
		await expect(canvas.queryByRole("switch", { name: /^Include drafts/ })).toBeNull();
	},
};

export const DocumentLifecycle: Story = {
	args: { options: mockDocumentWorkType, bindings: [mockDocumentBinding] },
	play: async ({ canvas }) => {
		const strip = within(canvas.getByRole("group", { name: "Reviews when, occasion 1" }));
		await expect(strip.getByRole("checkbox", { name: /^Published/ })).toBeChecked();
		await expect(strip.getByRole("checkbox", { name: "Content changed every time" })).toBeChecked();
		await expect(strip.getByRole("checkbox", { name: /^Archived/ })).not.toBeChecked();
	},
};

/** A second occasion is refused because there is no moment left for it to start on. */
export const ConversationHasOneMoment: Story = {
	args: { options: mockConversationWorkType, bindings: [mockConversationBinding] },
	play: async ({ canvas }) => {
		const strip = within(canvas.getByRole("group", { name: "Reviews when, occasion 1" }));
		await expect(strip.getAllByRole("checkbox")).toHaveLength(1);
		await expect(canvas.queryByText("Along the way")).toBeNull();
		await expect(canvas.getByRole("button", { name: "Add occasion" })).toBeDisabled();
		await expect(canvas.getByText(/already claimed by an occasion/)).toBeVisible();
	},
};

/**
 * Only the review at the merge reads the threads whole, which is what licenses it to say nobody ever
 * resolved one.
 */
export const TwoOccasionsReadingDifferentThings: Story = {
	args: { bindings: [mockPullRequestBinding, mockMergeBinding] },
	play: async ({ canvas }) => {
		const second = within(canvas.getByRole("group", { name: "Reviews when, occasion 2" }));
		await expect(second.getByRole("checkbox", { name: /^Merged/ })).toBeChecked();
		await expect(
			within(canvas.getByRole("group", { name: "What this review reads, occasion 2" })).getByText(
				/whole/,
			),
		).toBeVisible();
	},
};

/**
 * The server rejects a moment bound twice, and the refusal would otherwise arrive as a failed save
 * with nothing on screen explaining it.
 */
export const AMomentBelongsToOneOccasion: Story = {
	args: { bindings: [mockPullRequestBinding, mockMergeBinding] },
	play: async ({ canvas }) => {
		const second = within(canvas.getByRole("group", { name: "Reviews when, occasion 2" }));
		await expect(second.getByRole("checkbox", { name: /^Opened/ })).toHaveAttribute(
			"aria-disabled",
			"true",
		);
		await expect(second.getAllByText("in occasion 1")).toHaveLength(3);
	},
};

export const AskingByHandIsNotAMoment: Story = {
	play: async ({ canvas }) => {
		await expect(canvas.queryByRole("checkbox", { name: /Review requested by hand/ })).toBeNull();
		await expect(canvas.getByText(/Anyone can also ask for a review by hand/)).toBeVisible();
	},
};

export const DraftsAreAPropertyOfTheOccasion: Story = {
	args: {
		bindings: [{ ...mockPullRequestBinding, onDrafts: true }, mockMergeBinding],
	},
	play: async ({ canvas }) => {
		const drafts = canvas.getAllByRole("switch", { name: /^Include drafts/ });
		await expect(drafts[0]).toBeChecked();
		await expect(drafts[1]).not.toBeChecked();
	},
};

export const RecordedButNotReviewed: Story = {
	args: { canAttemptReview: false },
	play: async ({ canvas }) => {
		await expect(canvas.getByText(/nothing is reviewed while the practice asks/)).toBeVisible();
	},
};

export const GuidanceOnly: Story = {
	args: {
		guidanceOnly: true,
		bindings: [{ ...mockPullRequestBinding, needs: [] }],
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByText(/reads nothing, because no review runs/)).toBeVisible();
	},
};

export const WithRecentOutcomes: Story = {
	args: {
		outcome: outcome({
			practiceSlug: "clear-pr-description",
			considered: 12,
			skipped: 5,
			blockers: [
				{ sourceKind: "scm.pull-request.diff", reasonCode: "SOURCE_EMPTY", reviewsAffected: 4 },
				{
					sourceKind: "scm.pull-request.comments",
					reasonCode: "SOURCE_INCOMPLETE",
					reviewsAffected: 1,
				},
			],
		}),
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByText("7 of 12 reviews ran")).toBeVisible();
	},
};

/**
 * A submit sends focus to the control that has to change, and the message has to travel with it: on
 * its own it is text somewhere else on a long form.
 */
export const Invalid: Story = {
	args: {
		bindings: [{ signals: [], needs: [] }],
		error: "Choose when this occasion starts a review.",
		errorFocusId: "practice-binding-0-signals",
	},
	play: async ({ canvas }) => {
		await expect(
			canvas.getByRole("group", { name: "Reviews when, occasion 1" }),
		).toHaveAccessibleDescription("Choose when this occasion starts a review.");
		// Describing every group would make the message mean "something on this form is wrong".
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
 * The shared fixture aliases the work type's recommended evidence to the first occasion's, which
 * would let seeding from either satisfy the assertion below, so this gives the work type its own.
 */
export const AddingAnOccasion: Story = {
	args: {
		options: {
			...mockPullRequestWorkType,
			recommendedNeeds: [{ sourceKind: "scm.pull-request.core", stance: "REQUIRED" }],
		},
	},
	play: async ({ args, canvas }) => {
		await userEvent.click(canvas.getByRole("button", { name: "Add occasion" }));

		// Every recommended moment already belongs to occasion 1, so the new one takes the first free.
		await expect(args.onChange).toHaveBeenCalledWith([
			mockPullRequestBinding,
			{
				signals: ["scm.pull_request.reviewed"],
				needs: [{ sourceKind: "scm.pull-request.core", stance: "REQUIRED" }],
			},
		]);
	},
};

export const ChoosingAMoment: Story = {
	play: async ({ args, canvas }) => {
		const strip = within(canvas.getByRole("group", { name: "Reviews when, occasion 1" }));
		await userEvent.click(strip.getByRole("checkbox", { name: /^Review submitted/ }));

		// Sorted on the way out, so an untouched practice does not come back looking edited.
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
