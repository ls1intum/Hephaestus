import type { Meta, StoryObj } from "@storybook/react-vite";
import { BadPracticeLegendCard } from "./BadPracticeLegendCard";

/**
 * A legend card component that explains the different types of issues and practices
 * displayed in the application. Helps users understand the meaning of different colored
 * indicators and states.
 */
const meta = {
	component: BadPracticeLegendCard,
	parameters: {
		layout: "centered",
		docs: {
			description: {
				component:
					"A legend card that explains different types of practices and issues to help users understand the status indicators.",
			},
		},
	},
	tags: ["autodocs"],
} satisfies Meta<typeof BadPracticeLegendCard>;

export default meta;

type Story = StoryObj<typeof BadPracticeLegendCard>;

/**
 * Default view of the legend card showing all practice and issue types.
 * This component doesn't take any props as it's purely informational.
 */
export const Default: Story = {
	args: {},
};
