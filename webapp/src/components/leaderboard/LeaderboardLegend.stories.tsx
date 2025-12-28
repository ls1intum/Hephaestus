import type { Meta, StoryObj } from "@storybook/react-vite";
import { LeaderboardLegend } from "./LeaderboardLegend";

/**
 * Legend component that explains the scoring system and activity icons used in the leaderboard.
 * Provides a quick reference for understanding pull request review activities and their impact.
 */
const meta = {
	component: LeaderboardLegend,
	tags: ["autodocs"],
	parameters: {
		layout: "centered",
		backgrounds: {
			default: "light",
		},
	},
} satisfies Meta<typeof LeaderboardLegend>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default view showing the legend card with activity icons and descriptions.
 * Includes access to detailed scoring explanation via modal.
 */
export const Default: Story = {};
