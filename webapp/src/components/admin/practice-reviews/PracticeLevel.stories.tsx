import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, within } from "storybook/test";

import { mockPracticeDefinitionOptions } from "@/mocks/fixtures/practice";
import { withPageBehind } from "@/stories/decorators";
import { InLevelStack } from "@/stories/level-stack";
import { settledDrawerPanel } from "@/stories/overlay";
import { expectNoPanelOverflow } from "@/stories/reflow";

import { practiceRevisions, workspacePractices } from "./fixtures";
import { PracticeLevel } from "./PracticeLevel";
import { SHEET_SECTIONS } from "./RecordSheet";
import { practiceLevel } from "./review-levels";

const [practice] = workspacePractices;

/**
 * One practice's sheet, from the overview's Practices table: its full definition, read the same as
 * in Practice setup because it is the same component, with its autonomy in the status line once.
 * It counts nothing — the row it came from has the counts — and links only to Practice setup's
 * form, where it is changed.
 */
const meta = {
	component: PracticeLevel,
	parameters: { layout: "fullscreen" },
	decorators: [withPageBehind],
	tags: ["autodocs"],
	args: {
		workspaceSlug: "demo",
		path: { behind: [{ label: "Practice reviews", depth: 0 }], onClose: fn() },
		practice: {
			status: "ready",
			practice,
			definitionOptions: mockPracticeDefinitionOptions,
			groupName: "Review-ready work",
		},
		revisions: { status: "ready", revisions: practiceRevisions },
	},
	argTypes: { path: { control: false } },
	render: (args) => (
		<InLevelStack entry={practiceLevel(String(practice?.slug))} path={args.path}>
			{(level) => <PracticeLevel {...args} {...level} />}
		</InLevelStack>
	),
} satisfies Meta<typeof PracticeLevel>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
	play: async () => {
		const panelElement = await settledDrawerPanel();
		const panel = within(panelElement);
		await expect(
			panel.getAllByRole("heading", { level: 3 }).map((heading) => heading.textContent),
		).toEqual(
			expect.arrayContaining([SHEET_SECTIONS.what, SHEET_SECTIONS.state, SHEET_SECTIONS.technical]),
		);
		// No counts: the Practices table's row has them.
		await expect(panel.queryByText(/^Counts/u)).toBeNull();
		await expect(panel.queryByText("Feedback delivered")).toBeNull();
		// The autonomy once, in the status line.
		await expect(panelElement.querySelectorAll("[data-sheet-status]")).toHaveLength(1);
		await expect(panel.getByRole("link", { name: "Edit in Practice setup" })).toHaveAttribute(
			"href",
			expect.stringContaining("practice-edit"),
		);
	},
};

/** Every version of the definition in its history: what changed, and when what it checks changed. */
export const Versions: Story = {
	play: async () => {
		const panel = within(await settledDrawerPanel());
		const history = within(panel.getByRole("list", { name: "History" }));
		history.getByText("Version 1");
		history.getByText("The first version of the definition.");
		history.getByText("Changed: Why it matters.");
		history.getByText("Changed: Review criteria, Evidence to read.");
		// Only the version that changed what a review checks says so.
		await expect(history.getAllByText(/What a review checks changed/u)).toHaveLength(1);
		panel.getByText("Current version");
	},
};

/** The versions could not be read: the definition still stands. */
export const VersionsFailed: Story = {
	args: { revisions: { status: "error", error: new Error("Network down"), onRetry: fn() } },
	play: async () => {
		const panel = within(await settledDrawerPanel());
		panel.getByText(/the versions of this practice/u);
		panel.getByRole("heading", { name: SHEET_SECTIONS.what });
	},
};

/** A practice the workspace no longer has says so. */
export const NoLongerInTheWorkspace: Story = {
	args: {
		practice: {
			status: "ready",
			practice: undefined,
			definitionOptions: mockPracticeDefinitionOptions,
		},
	},
	play: async () => {
		const panel = within(await settledDrawerPanel());
		panel.getByText("This workspace no longer has this practice.");
	},
};

export const Reflow: Story = {
	parameters: { viewport: { defaultViewport: "reflow" }, chromatic: { viewports: [320] } },
	play: async () => {
		await expectNoPanelOverflow(await settledDrawerPanel());
	},
};
