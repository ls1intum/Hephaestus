import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import { PracticeAreasPanel } from "./PracticeAreasPanel";
import { mockAreas, mockPractices } from "./storyMockData";

const meta = {
	component: PracticeAreasPanel,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
		areas: mockAreas,
		practices: mockPractices,
		isMutating: false,
		onCreate: fn(),
		onRename: fn(),
		onToggleActive: fn(),
		onDelete: fn(),
		onReorder: fn(),
		onSetVisual: fn(),
	},
	decorators: [
		(Story) => (
			<div className="max-w-2xl mx-auto">
				<Story />
			</div>
		),
	],
} satisfies Meta<typeof PracticeAreasPanel>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Seeded areas with per-area practice counts and reorder controls. */
export const Default: Story = {};

/** No areas yet — the empty-state row prompts the admin to add one. */
export const Empty: Story = {
	args: { areas: [] },
};

/** A mid-write state: every control is disabled while a mutation is in flight. */
export const Mutating: Story = {
	args: { isMutating: true },
};

/** An inactive area renders its "Inactive" badge and an off switch. */
export const WithInactiveArea: Story = {
	args: {
		areas: mockAreas.map((area, i) => (i === 1 ? { ...area, active: false } : area)),
	},
};
