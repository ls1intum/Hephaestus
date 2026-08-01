import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, within } from "storybook/test";
import type { CatalogEntryStatus } from "@/api/types.gen";
import { CuratedEntryBadges } from "./CuratedEntryBadges";

const status = (overrides: Partial<CatalogEntryStatus> = {}): CatalogEntryStatus => ({
	etag: "tag",
	state: "FROM_HEPHAESTUS",
	changeKind: "NONE",
	offered: true,
	retired: false,
	updatedAt: new Date("2026-07-30T12:00:00Z"),
	...overrides,
});

const meta = {
	title: "Instance admin/Practice catalog/Entry badges",
	component: CuratedEntryBadges,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	args: { kind: "practice", status: status() },
} satisfies Meta<typeof CuratedEntryBadges>;

export default meta;
type Story = StoryObj<typeof meta>;

/** The ordinary case renders nothing at all — a badge on every row would say nothing. */
export const Ordinary: Story = {
	play: async ({ canvasElement }) => {
		await expect(within(canvasElement).queryByText(/./)).toBeNull();
	},
};

export const EditedHere: Story = { args: { status: status({ state: "EDITED_HERE" }) } };

export const UpdateChangesDetection: Story = {
	args: { status: status({ state: "UPDATE_WAITING", changeKind: "DETECTION" }) },
};

export const UpdateWordingOnly: Story = {
	args: { status: status({ state: "UPDATE_WAITING", changeKind: "WORDING" }) },
};

export const AddedHere: Story = { args: { status: status({ state: "YOURS" }) } };

export const NoLongerShipped: Story = {
	args: { status: status({ state: "NO_LONGER_SHIPPED" }) },
};

/** Retired entries always badge, whatever else is true of them. */
export const NotOffered: Story = {
	args: { status: status({ offered: false, retired: true }), kind: "area" },
	play: async ({ canvasElement }) => {
		await expect(within(canvasElement).getByText("Not offered")).toBeVisible();
	},
};
