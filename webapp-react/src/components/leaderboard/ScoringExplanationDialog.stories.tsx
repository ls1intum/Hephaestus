// filepath: /Users/felixdietrich/Documents/Hephaestus/webapp-react/src/components/leaderboard/ScoringExplanationDialog.stories.tsx
import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "@storybook/test";
import { ScoringExplanationDialog } from "./ScoringExplanationDialog";

/**
 * Dialog component that explains the leaderboard scoring system in detail.
 * Provides transparency about how points are calculated from different activities.
 */
const meta: Meta<typeof ScoringExplanationDialog> = {
	component: ScoringExplanationDialog,
	tags: ["autodocs"],
	parameters: {
		layout: "centered",
	},
	argTypes: {
		open: {
			control: "boolean",
			description: "Controls whether the dialog is visible",
		},
		onOpenChange: {
			description: "Callback when dialog open state changes",
			action: "open state changed",
		},
	},
	args: {
		onOpenChange: fn(),
	},
	decorators: [
		(Story) => (
			<div className="p-6 max-w-sm">
				<Story />
			</div>
		),
	],
};

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default view showing the open dialog with scoring explanation content.
 * Details how different contributions are weighted in the points system.
 */
export const Open: Story = {
	args: {
		open: true,
	},
};

/**
 * Dialog in closed state (not visible).
 */
export const Closed: Story = {
	args: {
		open: false,
	},
};
