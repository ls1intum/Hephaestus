import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, userEvent, within } from "storybook/test";

import { definitionOf } from "@/components/admin/practice-editor/fixtures";

import { ResetToCatalogDialog } from "./ResetToCatalogDialog";

const current = definitionOf({ name: "Explain the change", criteria: "Our criteria" });
const offered = { ...current, criteria: "New catalog criteria", lineNotes: false };

const meta = {
	component: ResetToCatalogDialog,
	tags: ["autodocs"],
	args: {
		open: true,
		onOpenChange: fn(),
		name: "Explain the change",
		state: { status: "ready", comparison: { kind: "practice", current, offered } },
		pending: false,
		stale: false,
		onConfirm: fn(),
	},
} satisfies Meta<typeof ResetToCatalogDialog>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Each field the reset replaces, this workspace's beside the catalog's, and nothing else. */
export const PracticeDifferences: Story = {
	play: async ({ args }) => {
		const dialog = await screen.findByRole("alertdialog", {
			name: "Reset “Explain the change” to the catalog?",
		});
		const table = within(dialog).getByRole("table", { name: "What the reset changes" });
		await expect(within(table).getByRole("rowheader", { name: "Review criteria" })).toBeVisible();
		await expect(within(table).getByText("Our criteria")).toBeVisible();
		await expect(within(table).getByText("New catalog criteria")).toBeVisible();
		await expect(within(table).getByRole("rowheader", { name: "Line notes" })).toBeVisible();
		await expect(within(table).queryByRole("rowheader", { name: "Name" })).toBeNull();
		await expect(within(dialog).getByText(/saved as a new version/u)).toBeVisible();
		await userEvent.click(within(dialog).getByRole("button", { name: "Reset to catalog" }));
		await expect(args.onConfirm).toHaveBeenCalledOnce();
	},
};

export const GroupDifferences: Story = {
	args: {
		name: "Review-ready work",
		state: {
			status: "ready",
			comparison: {
				kind: "group",
				current: { name: "Review-ready work", description: "Ours", icon: null, color: null },
				offered: {
					name: "Review-ready work",
					description: "Make each change easy to review.",
					icon: "Package",
					color: "sky",
				},
			},
		},
	},
	play: async () => {
		const dialog = await screen.findByRole("alertdialog");
		await expect(within(dialog).getByRole("rowheader", { name: "Description" })).toBeVisible();
		await expect(within(dialog).getByText("Make each change easy to review.")).toBeVisible();
		await expect(within(dialog).getByText(/practices stay where they are/u)).toBeVisible();
	},
};

export const NoDifferences: Story = {
	args: {
		state: { status: "ready", comparison: { kind: "practice", current, offered: current } },
	},
	play: async () => {
		const dialog = await screen.findByRole("alertdialog");
		await expect(within(dialog).getByText("Nothing differs from the catalog.")).toBeVisible();
		await expect(within(dialog).getByRole("button", { name: "Reset to catalog" })).toBeDisabled();
	},
};

export const Loading: Story = {
	args: { state: { status: "loading" } },
	play: async () => {
		const dialog = await screen.findByRole("alertdialog");
		await expect(within(dialog).queryByRole("table")).toBeNull();
		await expect(within(dialog).getByRole("button", { name: "Reset to catalog" })).toBeDisabled();
	},
};

export const LoadError: Story = {
	args: { state: { status: "error", error: new Error("down"), onRetry: fn() } },
	play: async () => {
		const dialog = await screen.findByRole("alertdialog");
		await expect(within(dialog).getByText("We could not load the catalog’s version")).toBeVisible();
		await expect(within(dialog).getByRole("button", { name: "Reset to catalog" })).toBeDisabled();
	},
};

export const Resetting: Story = {
	args: { pending: true },
	play: async () => {
		const dialog = await screen.findByRole("alertdialog");
		await expect(within(dialog).getByRole("button", { name: "Resetting…" })).toBeDisabled();
		await expect(within(dialog).getByRole("button", { name: "Keep yours" })).toBeDisabled();
	},
};

/** A refused reset shows what changed instead of trying again on its own. */
export const ChangedWhileOpen: Story = {
	args: { stale: true },
	play: async () => {
		const dialog = await screen.findByRole("alertdialog");
		await expect(within(dialog).getByRole("alert")).toHaveTextContent(/Nothing was reset/u);
	},
};
