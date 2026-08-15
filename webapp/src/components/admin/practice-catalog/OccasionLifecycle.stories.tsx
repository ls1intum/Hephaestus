import type { Meta, StoryObj } from "@storybook/react-vite";
import { useState } from "react";
import { expect, fn, within } from "storybook/test";
import type { PracticeWorkTypeDefinitionOptions } from "@/api/types.gen";
import { artifactKindLabel } from "@/lib/artifact-kinds";
import {
	mockConversationBinding,
	mockConversationWorkType,
	mockDocumentBinding,
	mockDocumentWorkType,
	mockIssueBinding,
	mockIssueWorkType,
	mockPullRequestBinding,
	mockPullRequestWorkType,
} from "@/mocks/fixtures/practice";
import { OccasionLifecycle } from "./OccasionLifecycle";

function ControlledLifecycle(args: React.ComponentProps<typeof OccasionLifecycle>) {
	const [selected, setSelected] = useState<string[]>([...args.selected]);
	const [onDrafts, setOnDrafts] = useState(args.onDrafts);
	return (
		<OccasionLifecycle
			{...args}
			selected={selected}
			onDrafts={onDrafts}
			onDraftsChange={setOnDrafts}
			onToggle={(signal, chosen) =>
				setSelected((current) =>
					chosen ? [...current, signal] : current.filter((value) => value !== signal),
				)
			}
		/>
	);
}

const meta = {
	title: "Workspace admin/Practices/Occasion lifecycle",
	component: OccasionLifecycle,
	args: {
		workType: mockPullRequestWorkType,
		selected: mockPullRequestBinding.signals,
		onToggle: fn(),
		onDrafts: false,
		onDraftsChange: fn(),
		occasion: { index: 0 },
	},
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	render: (args) => <ControlledLifecycle {...args} />,
} satisfies Meta<typeof OccasionLifecycle>;

export default meta;
type Story = StoryObj<typeof meta>;

export const PullRequest: Story = {
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Starts")).toBeVisible();
		await expect(canvas.getByText("Along the way")).toBeVisible();
		await expect(canvas.getByText("Ends")).toBeVisible();
	},
};

export const Conversation: Story = {
	args: { workType: mockConversationWorkType, selected: mockConversationBinding.signals },
	play: async ({ canvas }) => {
		await expect(canvas.queryByText("Ends")).toBeNull();
	},
};

const ALL_WORK_TYPES: Array<{
	workType: PracticeWorkTypeDefinitionOptions;
	selected: readonly string[];
}> = [
	{ workType: mockPullRequestWorkType, selected: mockPullRequestBinding.signals },
	{ workType: mockIssueWorkType, selected: mockIssueBinding.signals },
	{ workType: mockDocumentWorkType, selected: mockDocumentBinding.signals },
	{ workType: mockConversationWorkType, selected: mockConversationBinding.signals },
];

/** Side by side, which is the only way to judge whether one visual language holds across them. */
export const EveryWorkType: Story = {
	render: (args) => (
		<div className="space-y-8">
			{ALL_WORK_TYPES.map(({ workType, selected }, index) => (
				<section key={workType.artifactKind} className="space-y-2">
					<h3 className="text-sm font-semibold">{artifactKindLabel(workType.artifactKind)}</h3>
					<OccasionLifecycle
						{...args}
						workType={workType}
						selected={selected}
						occasion={{ index }}
					/>
				</section>
			))}
		</div>
	),
};

/** The server refuses a moment bound twice, and discovering that on save costs the whole form. */
export const MomentsHeldByAnotherOccasion: Story = {
	args: {
		selected: ["scm.pull_request.merged"],
		occasion: { index: 1 },
		heldElsewhere: new Map([
			["scm.pull_request.opened", 1],
			["scm.pull_request.ready", 1],
		]),
	},
	play: async ({ canvas }) => {
		const strip = within(canvas.getByRole("group", { name: "Reviews when, occasion 2" }));
		await expect(strip.getByRole("checkbox", { name: /^Opened/ })).toHaveAttribute(
			"aria-disabled",
			"true",
		);
		await expect(strip.getAllByText("in occasion 1")).toHaveLength(2);
	},
};

/** The fault is drawn on the strip, not only in the message. */
export const NoMomentChosen: Story = {
	args: { selected: [], occasion: { index: 0, errorId: "practice-bindings-error" } },
};

export const Disabled: Story = {
	args: { disabled: true },
};

/** Clicking anywhere on a node toggles it, because the node is the label of a real checkbox. */
export const TogglingMoments: Story = {
	play: async ({ canvas, userEvent }) => {
		const merged = canvas.getByRole("checkbox", { name: /^Merged/ });
		await expect(merged).not.toBeChecked();

		await userEvent.click(canvas.getByText("Merged"));
		await expect(canvas.getByRole("checkbox", { name: /^Merged/ })).toBeChecked();

		const drafts = canvas.getByRole("switch", { name: /^Include drafts/ });
		await userEvent.click(drafts);
		await expect(canvas.getByRole("switch", { name: /^Include drafts/ })).toBeChecked();
	},
};

/**
 * A description nested inside the label joins the switch's accessible name and puts a `<p>` inside a
 * `<label>`, which no content model allows.
 */
export const DraftsSwitchIsNamedByItsLabelAlone: Story = {
	play: async ({ canvas }) => {
		// Exact string: a prefix match would pass against a name the description had run on to.
		const drafts = canvas.getByRole("switch", { name: "Include drafts" });
		const hint = canvas.getByText(/Off by default/);
		await expect(drafts).toHaveAccessibleName("Include drafts");
		await expect(drafts).toHaveAccessibleDescription(
			"Off by default: read the work once it is offered as finished.",
		);
		// The description is a paragraph, and it is outside the label rather than inside it.
		await expect(hint.tagName).toBe("P");
		await expect(hint.closest("label")).toBeNull();
	},
};
