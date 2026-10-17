import type { Meta, StoryObj } from "@storybook/react-vite";

import { expectNoOverflowingElement } from "@/stories/reflow";

import type { CatalogEntryStatus } from "./catalog-entry-state";
import { CatalogEntryBadges } from "./CatalogEntryBadges";

const status = (overrides: Partial<CatalogEntryStatus> = {}): CatalogEntryStatus => ({
	state: "FROM_HEPHAESTUS",
	changeKind: "NONE",
	excluded: false,
	...overrides,
});

const meta = {
	component: CatalogEntryBadges,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	args: { kind: "practice", status: status() },
} satisfies Meta<typeof CatalogEntryBadges>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Customized: Story = { args: { status: status({ state: "EDITED_HERE" }) } };

export const UpdateChangesReviewRules: Story = {
	args: { status: status({ state: "UPDATE_WAITING", changeKind: "REVIEW" }) },
};

export const UpdateWordingOnly: Story = {
	args: { status: status({ state: "UPDATE_WAITING", changeKind: "WORDING" }) },
};

export const NoHephaestusDefault: Story = { args: { status: status({ state: "YOURS" }) } };

export const RemovedFromDefaults: Story = {
	args: { status: status({ state: "NO_LONGER_SHIPPED" }) },
};

export const Excluded: Story = {
	args: { status: status({ excluded: true }), kind: "group" },
};

export const NarrowViewport: Story = {
	parameters: { viewport: { defaultViewport: "reflow" }, chromatic: { viewports: [320] } },
	play: async ({ canvasElement }) => {
		await expectNoOverflowingElement(canvasElement);
	},
};
