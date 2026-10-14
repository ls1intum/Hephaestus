import type { Meta, StoryObj } from "@storybook/react-vite";
import { useState } from "react";
import { expect, fn, within } from "storybook/test";

import { Button } from "@/components/ui/button";
import { expectNoOverflowingElement } from "@/stories/reflow";
import { Stateful } from "@/stories/stateful";

import {
	definitionOf,
	mockDocumentItemType,
	mockPullRequestItemType,
	mockThreadItemType,
} from "./fixtures";
import { PracticeReadsEditor } from "./PracticeReadsEditor";

const meta = {
	component: PracticeReadsEditor,
	args: {
		readSources: mockPullRequestItemType.readSources,
		sources: definitionOf().reads.sources,
		idPrefix: "practice-review-on",
		onChange: fn(),
	},
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	render: (args) => (
		<Stateful initial={args.sources}>
			{(sources, setSources) => (
				<PracticeReadsEditor
					{...args}
					sources={sources}
					onChange={(next) => {
						args.onChange(next);
						setSources(next);
					}}
				/>
			)}
		</Stateful>
	),
} satisfies Meta<typeof PracticeReadsEditor>;

export default meta;
type Story = StoryObj<typeof meta>;

export const RequiredAndOptional: Story = {
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Code changes")).toBeVisible();
		await expect(canvas.getByText("Repository files and history")).toBeVisible();
	},
};

export const SourcesAreGrouped: Story = {
	play: async ({ canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("button", { name: "Choose sources" }));

		await expect(canvas.getByText("The work itself")).toBeVisible();
		await expect(canvas.getByText("Around the work")).toBeVisible();
		await expect(canvas.getByText("This developer’s history")).toBeVisible();
	},
};

export const EveryChoiceIsVisible: Story = {
	args: {
		sources: [
			{ source: "scm.linked-work-items", need: "REQUIRED" },
			{ source: "scm.pull-request.diff", need: "REQUIRED" },
		],
	},
	play: async ({ canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("button", { name: "Choose sources" }));

		const diff = within(canvas.getByRole("radiogroup", { name: "How Code changes is read" }));
		await expect(diff.getByRole("radio", { name: "Required" })).toBeChecked();
		await expect(diff.getByRole("radio", { name: "Optional" })).toBeVisible();
		await expect(diff.getByRole("radio", { name: "Off" })).toBeVisible();

		// Offered only where the contract can promise a whole capture. Linked work items never can, so
		// the control is absent rather than present-and-refused on save.
		await expect(canvas.getAllByRole("checkbox", { name: "Must be captured whole" })).toHaveLength(
			1,
		);
	},
};

/** Complete is Required plus a claim, so it disappears with the Required choice it extends. */
export const CompleteNeedsARequiredSource: Story = {
	play: async ({ canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("button", { name: "Choose sources" }));
		const core = within(
			canvas.getByRole("radiogroup", { name: "How Pull request details is read" }),
		);
		await expect(canvas.getAllByRole("checkbox", { name: "Must be captured whole" })).toHaveLength(
			2,
		);

		await userEvent.click(core.getByRole("radio", { name: "Optional" }));

		await expect(canvas.getAllByRole("checkbox", { name: "Must be captured whole" })).toHaveLength(
			1,
		);
	},
};

/** The cost of Complete arrives with the tick, not with the review that was withheld. */
export const CompleteStatesItsCost: Story = {
	play: async ({ canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("button", { name: "Choose sources" }));
		const [claim] = canvas.getAllByRole("checkbox", { name: "Must be captured whole" });
		if (!claim) {
			throw new Error("No source offers a whole capture");
		}
		await expect(claim).not.toHaveAccessibleDescription(/withholds the review/u);

		await userEvent.click(claim);

		await expect(claim).toHaveAccessibleDescription(/A partial capture then withholds the review/u);
		await expect(canvas.getByText("· captured whole")).toBeVisible();
	},
};

export const ReadsASourceWhole: Story = {
	args: {
		sources: [
			{ source: "scm.pull-request.core", need: "REQUIRED" },
			{ source: "scm.repository.tree", need: "OPTIONAL" },
			{ source: "scm.review-threads", need: "COMPLETE" },
		],
	},
	play: async ({ canvas }) => {
		// "captured whole", not "nothing is missing anywhere": the claim is about this capture of this
		// source, which is the only thing completeness is ever measured against.
		await expect(canvas.getByText("· captured whole")).toBeVisible();
		await expect(canvas.getByText("Repository files and history")).toBeVisible();
	},
};

export const ADocumentOffersLess: Story = {
	args: {
		readSources: mockDocumentItemType.readSources,
		sources: [{ source: "docs.document.core", need: "REQUIRED" }],
	},
	play: async ({ canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("button", { name: "Choose sources" }));

		await expect(canvas.getByText("The work itself")).toBeVisible();
		await expect(canvas.queryByText("Around the work")).toBeNull();
		await expect(
			canvas.getByRole("radiogroup", { name: "How Document under review is read" }),
		).toBeVisible();
	},
};

export const AConversationReadsOneThread: Story = {
	args: {
		readSources: mockThreadItemType.readSources,
		sources: [{ source: "slack.conversation.thread", need: "COMPLETE" }],
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Slack thread")).toBeVisible();
		await expect(canvas.getByText("· captured whole")).toBeVisible();
	},
};

export const NothingRequiredYet: Story = {
	args: { sources: [], invalid: true },
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Nothing yet")).toBeVisible();
		// The invalid state opens the source list, because the fix is not reachable from the summary.
		await expect(
			canvas.getByRole("radiogroup", { name: "How Code changes is read" }),
		).toBeVisible();
	},
};

/** Drives `invalid` the way a form does, including re-rendering without changing it. */
function SubmittedIntoInvalid(args: React.ComponentProps<typeof PracticeReadsEditor>) {
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
			<PracticeReadsEditor {...args} invalid={invalid} />
		</div>
	);
}

/**
 * `invalid` stays true while the author fixes it, so an editor keyed on the flag rather than on the
 * transition into it would re-open under the caret on every keystroke.
 */
export const SubmittingRevealsTheSources: Story = {
	args: { sources: [] },
	render: (args) => <SubmittedIntoInvalid {...args} />,
	play: async ({ canvas, userEvent }) => {
		const sources = { name: "How Code changes is read" };
		await expect(canvas.queryByRole("radiogroup", sources)).toBeNull();

		await userEvent.click(canvas.getByRole("button", { name: "Submit the form" }));
		await expect(canvas.getByRole("radiogroup", sources)).toBeVisible();

		// Closed again by the author, and it stays closed while the error stands.
		await userEvent.click(canvas.getByRole("button", { name: "Choose sources" }));
		await userEvent.click(canvas.getByRole("button", { name: "Type something else" }));
		await expect(canvas.queryByRole("radiogroup", sources)).toBeNull();
	},
};

export const Disabled: Story = {
	args: { disabled: true },
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("button", { name: "Choose sources" })).toBeDisabled();
	},
};

export const NarrowViewport: Story = {
	parameters: { viewport: { defaultViewport: "reflow" }, chromatic: { viewports: [320] } },
	play: async ({ canvasElement }) => {
		await expectNoOverflowingElement(canvasElement);
	},
};
