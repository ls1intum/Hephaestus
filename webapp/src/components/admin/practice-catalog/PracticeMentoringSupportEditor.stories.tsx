import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn } from "storybook/test";
import { mockPracticeDefinitionOptions } from "@/mocks/fixtures/practice";
import { PracticeMentoringSupportEditor } from "./PracticeMentoringSupportEditor";

const pullRequests = mockPracticeDefinitionOptions.workTypes[0];

const meta = {
	title: "Workspace admin/Practices/AI mentoring",
	component: PracticeMentoringSupportEditor,
	args: {
		value: pullRequests.recommendedPolicy,
		recommended: pullRequests.recommendedPolicy,
		supportedAutomatedReviewModes: pullRequests.supportedAutomatedReviewModes,
		onChange: fn(),
	},
	parameters: { layout: "padded" },
	tags: ["autodocs"],
} satisfies Meta<typeof PracticeMentoringSupportEditor>;

export default meta;
type Story = StoryObj<typeof meta>;

export const AiSupported: Story = {};

/**
 * The reason a practice needs a human is its own field, not the first known limitation, so both are
 * editable at once and the limitation list is complete.
 */
export const HumanReviewReasonIsNotALimitation: Story = {
	args: {
		value: {
			...pullRequests.recommendedPolicy,
			automatedReview: {
				mode: "LANGUAGE_MODEL",
				evidenceSufficiency: "DECLARED_EVIDENCE_INSUFFICIENT",
			},
			insufficiencyReason: {
				code: "MENTOR_CONVERSATION_NOT_OBSERVED",
				description: "The trade-off was agreed in a conversation Hephaestus cannot read.",
			},
		},
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByLabelText(/Why is human review needed/)).toHaveValue(
			"The trade-off was agreed in a conversation Hephaestus cannot read.",
		);
		await expect(
			canvas.getByDisplayValue(
				"Repository evidence does not establish behavior in a deployed runtime.",
			),
		).toBeVisible();
	},
};

/** With no review to constrain, there is nothing for a limitation to be a limitation of. */
export const GuidanceOnly: Story = {
	args: {
		value: {
			...pullRequests.recommendedPolicy,
			automatedReview: { mode: "NONE", evidenceSufficiency: "NONE" },
			knownLimitations: [],
		},
	},
	play: async ({ canvas }) => {
		await expect(canvas.queryByRole("button", { name: "Add limitation" })).toBeNull();
	},
};

/** AI review unavailable on this instance: the choice is disabled, not silently reinterpreted. */
export const AiReviewUnavailable: Story = {
	args: { supportedAutomatedReviewModes: [] },
	play: async ({ canvas }) => {
		await expect(canvas.getByText(/no AI review available on this instance/)).toBeVisible();
	},
};

export const Invalid: Story = {
	args: {
		value: {
			...pullRequests.recommendedPolicy,
			automatedReview: {
				mode: "LANGUAGE_MODEL",
				evidenceSufficiency: "DECLARED_EVIDENCE_INSUFFICIENT",
			},
			insufficiencyReason: { code: "LIMITATION_00000000", description: "" },
			knownLimitations: [],
		},
		error: "Explain at least one limitation that requires additional context.",
	},
	play: async ({ canvas }) => {
		await expect(
			canvas.getByText("Explain at least one limitation that requires additional context."),
		).toBeVisible();
		await expect(
			canvas.getByText("Say what a person can see here that the connected work cannot show."),
		).toBeVisible();
	},
};
