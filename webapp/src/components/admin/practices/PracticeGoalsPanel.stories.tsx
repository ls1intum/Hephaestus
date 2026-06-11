import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import { PracticeGoalsPanel } from "./PracticeGoalsPanel";
import { mockGoals, mockPractices } from "./storyMockData";

const meta = {
	component: PracticeGoalsPanel,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
		goals: mockGoals,
		practices: mockPractices,
		isMutating: false,
		onCreate: fn(),
		onRename: fn(),
		onToggleActive: fn(),
		onDelete: fn(),
		onReorder: fn(),
	},
	decorators: [
		(Story) => (
			<div className="max-w-2xl mx-auto">
				<Story />
			</div>
		),
	],
} satisfies Meta<typeof PracticeGoalsPanel>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Seeded goals with per-goal practice counts and reorder controls. */
export const Default: Story = {};

/** No goals yet — the empty-state row prompts the admin to add one. */
export const Empty: Story = {
	args: { goals: [] },
};

/** A mid-write state: every control is disabled while a mutation is in flight. */
export const Mutating: Story = {
	args: { isMutating: true },
};

/** An inactive goal renders its "Inactive" badge and an off switch. */
export const WithInactiveGoal: Story = {
	args: {
		goals: mockGoals.map((goal, i) => (i === 1 ? { ...goal, active: false } : goal)),
	},
};
