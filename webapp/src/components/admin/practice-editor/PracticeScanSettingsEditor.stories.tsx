import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn } from "storybook/test";

import { expectNoOverflowingElement } from "@/stories/reflow";
import { Stateful } from "@/stories/stateful";

import { mockFileItemType, mockIssueItemType, mockPullRequestItemType, mockScan } from "./fixtures";
import { PracticeScanSettingsEditor } from "./PracticeScanSettingsEditor";
import { emptyScanDraft, scanDraftOf } from "./scan-settings";

const meta = {
	component: PracticeScanSettingsEditor,
	args: {
		itemType: mockPullRequestItemType,
		value: { enabled: true, draft: scanDraftOf(mockScan) },
		readSources: mockFileItemType.readSources,
		onChange: fn(),
	},
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	render: (args) => (
		<Stateful initial={args.value}>
			{(value, setValue) => (
				<PracticeScanSettingsEditor
					{...args}
					value={value}
					onChange={(next) => {
						args.onChange(next);
						setValue(next);
					}}
				/>
			)}
		</Stateful>
	),
} satisfies Meta<typeof PracticeScanSettingsEditor>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Enabled: Story = {
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("switch", { name: "Include in scans" })).toBeChecked();
		await expect(canvas.getByRole("textbox", { name: /^Include/u })).toHaveValue(
			"**/*.ts\n**/*.tsx",
		);
		await expect(canvas.getByRole("group", { name: "Scan reads" })).toBeVisible();
		// A scan names no developer, and the switch says so where it is turned on.
		await expect(
			canvas.getByRole("switch", { name: "Include in scans" }),
		).toHaveAccessibleDescription(/not attributed to any developer/u);
	},
};

export const Disabled: Story = {
	args: { value: { enabled: false, draft: emptyScanDraft(mockPullRequestItemType) } },
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("switch", { name: "Include in scans" })).not.toBeChecked();
		await expect(canvas.queryByRole("textbox", { name: /^Include/u })).toBeNull();
	},
};

/** Turning scans off keeps what was written, so turning them on again restores it. */
export const TurningOffKeepsTheDraft: Story = {
	play: async ({ args, canvas, userEvent }) => {
		const toggle = canvas.getByRole("switch", { name: "Include in scans" });
		await userEvent.click(toggle);
		await expect(args.onChange).toHaveBeenLastCalledWith({
			enabled: false,
			draft: scanDraftOf(mockScan),
		});
		await expect(canvas.queryByRole("textbox", { name: /^Include/u })).toBeNull();

		await userEvent.click(toggle);
		await expect(canvas.getByRole("textbox", { name: /^Exclude/u })).toHaveValue("**/*.gen.ts");
	},
};

export const Invalid: Story = {
	args: {
		value: { enabled: true, draft: { ...scanDraftOf(mockScan), include: "../outside/**" } },
		problem: {
			field: "include",
			message: "A pattern cannot leave the repository: remove “..” from “../outside/**”.",
		},
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("textbox", { name: /^Include/u })).toHaveAccessibleDescription(
			/A pattern cannot leave the repository/u,
		);
		await expect(
			canvas.getByRole("textbox", { name: /^Exclude/u }),
		).not.toHaveAccessibleDescription(/A pattern cannot leave the repository/u);
	},
};

/** An item type that offers no scans shows nothing, unless a scan was saved before it stopped. */
export const UnsupportedKind: Story = {
	args: {
		itemType: mockIssueItemType,
		value: { enabled: false, draft: emptyScanDraft(mockIssueItemType) },
	},
	play: async ({ canvas }) => {
		await expect(canvas.queryByRole("switch", { name: "Include in scans" })).toBeNull();
	},
};

/** A saved scan the kind no longer offers stays until the author removes it, never silently. */
export const UnsupportedKindWithASavedScan: Story = {
	args: { itemType: mockIssueItemType },
	play: async ({ args, canvas, userEvent }) => {
		await expect(canvas.getByText(/This kind of item no longer offers scans/u)).toBeVisible();
		await userEvent.click(canvas.getByRole("button", { name: "Remove scan settings" }));
		await expect(args.onChange).toHaveBeenLastCalledWith(
			expect.objectContaining({ enabled: false }),
		);
	},
};

export const NarrowViewport: Story = {
	parameters: { viewport: { defaultViewport: "reflow" }, chromatic: { viewports: [320] } },
	play: async ({ canvasElement }) => {
		await expectNoOverflowingElement(canvasElement);
	},
};
