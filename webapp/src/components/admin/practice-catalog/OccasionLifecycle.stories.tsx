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

/**
 * Ticking a moment and flipping the drafts switch have to change what is on screen, so the harness
 * holds both. Only one story used to go through it; the rest paired a frozen `selected` with `fn()`,
 * so clicking a node did nothing at all — the single most important thing this strip does.
 */
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
		idPrefix: "practice-binding-0",
		groupId: "practice-binding-0-signals",
		occasionLabel: "occasion 1",
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

export const Issue: Story = {
	args: { workType: mockIssueWorkType, selected: mockIssueBinding.signals },
};

export const Document: Story = {
	args: { workType: mockDocumentWorkType, selected: mockDocumentBinding.signals },
};

/** One moment, so no bands to name and no rail to draw. The strip still reads as the same object. */
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

/**
 * The four lifecycles side by side, which is the only way to judge whether one visual language holds
 * across them. It has to: a pull request's six moments in three bands, an issue's three, a document's
 * three under entirely different words, and a conversation's single one — none of them special-cased.
 */
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
						idPrefix={`work-type-${index}`}
						groupId={`work-type-${index}-signals`}
					/>
				</section>
			))}
		</div>
	),
};

/**
 * A moment already held by another occasion cannot be taken: the server refuses a moment bound twice,
 * and discovering that on save would cost the author the whole form.
 */
export const MomentsHeldByAnotherOccasion: Story = {
	args: {
		selected: ["scm.pull_request.merged"],
		occasionLabel: "occasion 2",
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

/** The fault is on the strip, not only in the message: an occasion with no moment shows it. */
export const NoMomentChosen: Story = {
	args: { selected: [], invalid: true, errorId: "practice-bindings-error" },
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
