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

		await expect(canvas.getAllByRole("checkbox", { name: "Must be captured whole" })).toHaveLength(
			1,
		);
	},
};

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
		await expect(
			canvas.getByRole("radiogroup", { name: "How Code changes is read" }),
		).toBeVisible();
	},
};

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

// Open on the transition to invalid; the flag remains true while typing, which must not reopen the editor.
export const SubmittingRevealsTheSources: Story = {
	args: { sources: [] },
	render: (args) => <SubmittedIntoInvalid {...args} />,
	play: async ({ canvas, userEvent }) => {
		const sources = { name: "How Code changes is read" };
		await expect(canvas.queryByRole("radiogroup", sources)).toBeNull();

		await userEvent.click(canvas.getByRole("button", { name: "Submit the form" }));
		await expect(canvas.getByRole("radiogroup", sources)).toBeVisible();

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
