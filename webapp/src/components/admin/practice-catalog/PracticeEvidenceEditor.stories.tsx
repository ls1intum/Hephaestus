import type { Meta, StoryObj } from "@storybook/react-vite";
import { useState } from "react";
import { expect, fn, within } from "storybook/test";
import { Button } from "@/components/ui/button";
import {
	mockConversationBinding,
	mockConversationWorkType,
	mockDocumentBinding,
	mockDocumentWorkType,
	mockPullRequestBinding,
	mockPullRequestWorkType,
} from "@/mocks/fixtures/practice";
import { PracticeEvidenceEditor } from "./PracticeEvidenceEditor";

const meta = {
	title: "Workspace admin/Practices/Occasion evidence",
	component: PracticeEvidenceEditor,
	args: {
		options: mockPullRequestWorkType,
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

/** Closed, the answer is the chips: three required sources out of the eleven a pull request offers. */
export const RecommendedEvidence: Story = {
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Code changes")).toBeVisible();
		await expect(canvas.getByText("Nothing")).toBeVisible();
	},
};

/**
 * A pull request offers eleven sources, and as one flat list they read as eleven equal questions.
 * Three headings turn that into three short decisions: what the change is, what it has to be read
 * against, and what this workspace already said to the person who wrote it.
 */
export const SourcesAreGrouped: Story = {
	play: async ({ canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("button", { name: "Choose sources" }));

		await expect(canvas.getByText("The work itself")).toBeVisible();
		await expect(canvas.getByText("Around the work")).toBeVisible();
		await expect(canvas.getByText("This person's history")).toBeVisible();
	},
};

/**
 * Every choice is a visible segment rather than a menu, and the one thing EXHAUSTIVE adds to REQUIRED
 * is offered as that claim rather than as a fourth segment nobody could tell apart from Required.
 */
export const EveryChoiceIsVisible: Story = {
	play: async ({ canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("button", { name: "Choose sources" }));

		const diff = within(
			canvas.getByRole("radiogroup", { name: "How Code changes is used, occasion 1" }),
		);
		await expect(diff.getByRole("radio", { name: "Required" })).toBeChecked();
		await expect(diff.getByRole("radio", { name: "Context" })).toBeVisible();
		await expect(diff.getByRole("radio", { name: "Off" })).toBeVisible();

		// Offered only where the contract can promise a whole capture. Linked work items never can, so
		// the control is absent rather than present-and-refused on save.
		await expect(
			canvas.getByRole("checkbox", { name: /missing from Code changes/ }),
		).not.toBeChecked();
		await expect(
			canvas.queryByRole("checkbox", { name: /missing from Linked work items/ }),
		).toBeNull();
	},
};

/** A source that is only optional context can never be the ground for saying something is not there. */
export const AbsenceClaimNeedsARequiredSource: Story = {
	render: (args) => <ControlledEvidence {...args} />,
	play: async ({ canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("button", { name: "Choose sources" }));
		const comments = within(
			canvas.getByRole("radiogroup", { name: "How Inline review comments is used, occasion 1" }),
		);
		await expect(
			canvas.getByRole("checkbox", { name: /missing from Inline review comments/ }),
		).toBeVisible();

		await userEvent.click(comments.getByRole("radio", { name: "Context" }));

		await expect(
			canvas.queryByRole("checkbox", { name: /missing from Inline review comments/ }),
		).toBeNull();
	},
};

export const ReadsASourceExhaustively: Story = {
	args: {
		needs: [
			{ sourceKind: "scm.pull-request.core", stance: "REQUIRED" },
			{ sourceKind: "scm.repository.tree", stance: "CONTEXTUAL" },
			{ sourceKind: "scm.review-threads", stance: "EXHAUSTIVE" },
		],
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByText("· whole")).toBeVisible();
		await expect(canvas.getByText("Repository files")).toBeVisible();
		await expect(canvas.getByRole("button", { name: "Use recommended evidence" })).toBeVisible();
	},
};

/**
 * A document offers three sources and no surroundings at all, so two of the three headings never
 * appear. The same component draws it — nothing here is special-cased per work type.
 */
export const ADocumentOffersLess: Story = {
	args: { options: mockDocumentWorkType, needs: mockDocumentBinding.needs },
	play: async ({ canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("button", { name: "Choose sources" }));

		await expect(canvas.getByText("The work itself")).toBeVisible();
		await expect(canvas.queryByText("Around the work")).toBeNull();
		await expect(
			canvas.getByRole("radiogroup", { name: "How Document under review is used, occasion 1" }),
		).toBeVisible();
	},
};

/** A conversation reads one thread, and the thread has to be captured whole or not read at all. */
export const AConversationReadsOneThread: Story = {
	args: { options: mockConversationWorkType, needs: mockConversationBinding.needs },
	play: async ({ canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("button", { name: "Choose sources" }));

		await expect(canvas.getByText("Must be captured whole")).toBeVisible();
	},
};

/** The state the form refuses to save, shown as the author would meet it. */
export const NothingRequiredYet: Story = {
	args: { needs: [], invalid: true },
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Nothing yet")).toBeVisible();
		// The invalid state opens the source list, because the fix is not reachable from the summary.
		await expect(
			canvas.getByRole("radiogroup", { name: "How Code changes is used, occasion 1" }),
		).toBeVisible();
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
		const sources = { name: "How Code changes is used, occasion 1" };
		await expect(canvas.queryByRole("radiogroup", sources)).toBeNull();

		await userEvent.click(canvas.getByRole("button", { name: "Submit the form" }));
		await expect(canvas.getByRole("radiogroup", sources)).toBeVisible();

		// Closed again by the author, and it stays closed while the error stands — the panel opens on
		// the transition into invalid, so re-opening it is the author's to undo.
		await userEvent.click(canvas.getByRole("button", { name: "Choose sources" }));
		await userEvent.click(canvas.getByRole("button", { name: "Type something else" }));
		await expect(canvas.queryByRole("radiogroup", sources)).toBeNull();
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
