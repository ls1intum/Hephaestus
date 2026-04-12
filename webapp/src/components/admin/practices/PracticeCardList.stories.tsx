import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import { PracticeCardList } from "./PracticeCardList";
import { mockPractices, mockPracticeWithAllTriggers } from "./storyMockData";

const meta = {
	component: PracticeCardList,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
		workspaceSlug: "demo",
		practices: mockPractices,
		isLoading: false,
		togglingPractices: new Set<string>(),
		onDelete: fn(),
		onSetActive: fn(),
	},
	decorators: [
		(Story) => (
			<div className="max-w-3xl mx-auto">
				<Story />
			</div>
		),
	],
} satisfies Meta<typeof PracticeCardList>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Default card list with mixed active/inactive practices. */
export const Default: Story = {};

/** Loading state with centered spinner. */
export const Loading: Story = {
	args: {
		practices: [],
		isLoading: true,
	},
};

/** Empty state with CTA to create first practice. */
export const Empty: Story = {
	args: {
		practices: [],
	},
};

/** Single practice card. */
export const SinglePractice: Story = {
	args: {
		practices: [mockPractices[0]],
	},
};

/** All practices including one with all triggers. */
export const WithAllTriggers: Story = {
	args: {
		practices: [mockPracticeWithAllTriggers, ...mockPractices],
	},
};

/** One practice actively toggling (disabled switch). */
export const WithTogglingPractice: Story = {
	args: {
		togglingPractices: new Set(["pr-description-quality"]),
	},
};
