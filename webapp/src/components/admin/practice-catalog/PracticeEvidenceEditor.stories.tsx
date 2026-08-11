import type { Meta, StoryObj } from "@storybook/react-vite";
import { useState } from "react";
import { expect, fn, within } from "storybook/test";
import { Button } from "@/components/ui/button";
import { mockPracticeDefinitionOptions, mockPullRequestBinding } from "@/mocks/fixtures/practice";
import { PracticeEvidenceEditor } from "./PracticeEvidenceEditor";

const pullRequests = mockPracticeDefinitionOptions.workTypes[0];

const meta = {
	title: "Workspace admin/Practices/Occasion evidence",
	component: PracticeEvidenceEditor,
	args: {
		options: pullRequests,
		needs: mockPullRequestBinding.needs,
		idPrefix: "practice-binding-0",
		occasionLabel: "occasion 1",
		onChange: fn(),
	},
	parameters: { layout: "padded" },
	tags: ["autodocs"],
} satisfies Meta<typeof PracticeEvidenceEditor>;

export default meta;
type Story = StoryObj<typeof meta>;

function ControlledEvidence(args: React.ComponentProps<typeof PracticeEvidenceEditor>) {
	const [needs, setNeeds] = useState(args.needs);
	return <PracticeEvidenceEditor {...args} needs={needs} onChange={setNeeds} />;
}

export const RecommendedEvidence: Story = {};

/**
 * Every choice is a visible control rather than a menu, and the one thing EXHAUSTIVE adds to REQUIRED
 * is offered as that claim rather than as a role nobody could tell apart from REQUIRED.
 */
export const EveryChoiceIsVisible: Story = {
	play: async ({ canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("button", { name: "Choose sources" }));

		const diff = canvas.getByRole("radiogroup", { name: "Use Code changes in occasion 1" });
		await expect(within(diff).getByRole("radio", { name: "Required" })).toBeChecked();
		await expect(within(diff).getByRole("radio", { name: "Optional context" })).toBeVisible();
		await expect(within(diff).getByRole("radio", { name: "Not used" })).toBeVisible();

		// Offered only where the contract can promise a whole capture. The linked issues never can, so
		// the control is absent rather than present-and-refused on save.
		await expect(
			canvas.getByRole("checkbox", { name: /says what is missing from Code changes/ }),
		).not.toBeChecked();
		await expect(
			canvas.queryByRole("checkbox", { name: /says what is missing from Linked issues/ }),
		).toBeNull();
	},
};

export const AbsenceClaimNeedsARequiredSource: Story = {
	render: (args) => <ControlledEvidence {...args} />,
	play: async ({ canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("button", { name: "Choose sources" }));
		const comments = canvas.getByRole("radiogroup", {
			name: "Use Inline review comments in occasion 1",
		});
		await expect(
			canvas.getByRole("checkbox", { name: /says what is missing from Inline review comments/ }),
		).toBeVisible();

		await userEvent.click(within(comments).getByRole("radio", { name: "Optional context" }));

		await expect(
			canvas.queryByRole("checkbox", { name: /says what is missing from Inline review comments/ }),
		).toBeNull();
	},
};

export const ReadsASourceExhaustively: Story = {
	args: {
		needs: [
			{ sourceKind: "scm.pull-request.core", stance: "REQUIRED" },
			{ sourceKind: "scm.review-threads", stance: "EXHAUSTIVE" },
		],
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByText(/and nothing missing from it/)).toBeVisible();
		await expect(canvas.getByRole("button", { name: "Use recommended evidence" })).toBeVisible();
	},
};

/** The state the form refuses to save, shown as the author would meet it. */
export const NothingRequiredYet: Story = {
	args: { needs: [], invalid: true },
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Nothing yet")).toBeVisible();
		// The invalid state opens the source list, because the fix is not reachable from the summary.
		await expect(canvas.getByRole("radiogroup", { name: /Use Code changes/ })).toBeVisible();
	},
};

/**
 * Drives `invalid` from outside the way a form does — false while the author is writing, true from
 * the submit onwards — and can re-render without changing it, as every later keystroke does.
 */
function SubmittedIntoInvalid(args: React.ComponentProps<typeof PracticeEvidenceEditor>) {
	const [invalid, setInvalid] = useState(false);
	const [renders, setRenders] = useState(0);
	return (
		<div className="space-y-3">
			<Button type="button" onClick={() => setInvalid(true)}>
				Submit the form
			</Button>
			<Button type="button" variant="outline" onClick={() => setRenders(renders + 1)}>
				Type something else
			</Button>
			<PracticeEvidenceEditor {...args} invalid={invalid} />
		</div>
	);
}

/**
 * Submitting a form already on screen has to open the panel on the *transition* rather than on the
 * flag: the flag stays true while the author fixes it, and an editor re-opening under the caret on
 * every keystroke would be unusable.
 */
export const SubmittingRevealsTheSources: Story = {
	args: { needs: [] },
	render: (args) => <SubmittedIntoInvalid {...args} />,
	play: async ({ canvas, userEvent }) => {
		await expect(canvas.queryByRole("radiogroup", { name: /Use Code changes/ })).toBeNull();

		await userEvent.click(canvas.getByRole("button", { name: "Submit the form" }));
		await expect(canvas.getByRole("radiogroup", { name: /Use Code changes/ })).toBeVisible();

		// Closed again by the author, and it stays closed while the error stands — the panel opens on
		// the transition into invalid, so re-opening it is the author's to undo.
		await userEvent.click(canvas.getByRole("button", { name: "Choose sources" }));
		await userEvent.click(canvas.getByRole("button", { name: "Type something else" }));
		await expect(canvas.queryByRole("radiogroup", { name: /Use Code changes/ })).toBeNull();
	},
};

/** Under "Human review needed" the evidence is still authored, but nothing checks it. */
export const RecordedButNotReviewed: Story = {
	args: { canAttemptReview: false },
	play: async ({ canvas }) => {
		await expect(canvas.getByText(/nothing is reviewed while the practice asks/)).toBeVisible();
	},
};

export const Disabled: Story = {
	args: { disabled: true },
};
