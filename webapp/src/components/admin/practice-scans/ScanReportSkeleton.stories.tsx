import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect } from "storybook/test";

import { withStandardPage } from "@/stories/decorators";

import { ScanReportSkeleton } from "./ScanReportSkeleton";

const meta = {
	component: ScanReportSkeleton,
	parameters: { layout: "fullscreen" },
	decorators: [withStandardPage],
	tags: ["autodocs"],
} satisfies Meta<typeof ScanReportSkeleton>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Loading scan report")).toHaveClass("sr-only");
	},
};
