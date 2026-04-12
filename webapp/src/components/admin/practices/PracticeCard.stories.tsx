import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import { PracticeCard } from "./PracticeCard";
import {
	mockPracticeLongText,
	mockPracticeNoCategory,
	mockPractices,
	mockPracticeWithAllTriggers,
} from "./storyMockData";

const meta = {
	component: PracticeCard,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
		workspaceSlug: "demo",
		practice: mockPractices[0],
		isToggling: false,
		onDelete: fn(),
		onSetActive: fn(),
	},
	decorators: [
		(Story) => (
			<div className="max-w-3xl">
				<Story />
			</div>
		),
	],
} satisfies Meta<typeof PracticeCard>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Active practice with criteria preview. */
export const ActiveWithCriteria: Story = {};

/** Inactive practice (visually dimmed). */
export const Inactive: Story = {
	args: {
		practice: mockPractices[2],
	},
};

/** Practice without criteria configured. */
export const NoCriteria: Story = {
	args: {
		practice: mockPractices[1],
	},
};

/** Practice with all 4 trigger events. */
export const AllTriggers: Story = {
	args: {
		practice: mockPracticeWithAllTriggers,
	},
};

/** Card while the active toggle is mid-mutation. */
export const Toggling: Story = {
	args: {
		isToggling: true,
	},
};

/** Active practice without category badge. */
export const NoCategory: Story = {
	args: {
		practice: mockPracticeNoCategory,
	},
};

/** Practice with long name, description, and criteria — tests overflow and line-clamp. */
export const LongText: Story = {
	args: {
		practice: mockPracticeLongText,
	},
};
