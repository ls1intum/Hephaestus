import type { Meta, StoryObj } from "@storybook/react";
import { AboutMissionSection } from "./AboutMissionSection";

/**
 * AboutMissionSection component for displaying the platform's mission and core features.
 * Combines the mission statement with an open two-column product overview.
 */
const meta = {
	component: AboutMissionSection,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
} satisfies Meta<typeof AboutMissionSection>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default mission section with standard content and feature grid.
 */
export const Default: Story = {};
