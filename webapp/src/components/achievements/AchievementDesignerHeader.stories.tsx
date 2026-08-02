import type { Meta, StoryObj } from "@storybook/react-vite";
import { ReactFlowProvider } from "@xyflow/react";
import { expect, fn } from "storybook/test";
import { expectControlOnScreen, expectNoPageOverflow } from "@/test/reflow";
import { AchievementDesignerHeader } from "./AchievementDesignerHeader";

const meta = {
	title: "Admin/Achievements/Designer header",
	component: AchievementDesignerHeader,
	parameters: { layout: "fullscreen" },
	decorators: [
		(Story) => (
			<ReactFlowProvider>
				<Story />
			</ReactFlowProvider>
		),
	],
	args: {
		onReload: fn(),
		isReloading: false,
	},
} satisfies Meta<typeof AchievementDesignerHeader>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};

export const Loading: Story = {
	args: { isLoading: true },
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("status")).toHaveTextContent("Loading achievements...");
	},
};

export const LoadFailed: Story = {
	args: { isError: true },
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("alert")).toHaveTextContent("Failed to load achievement data");
	},
};

export const Reloading: Story = {
	args: { isReloading: true },
};

export const Mobile: Story = {
	parameters: {
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320] },
	},
	play: async ({ canvas }) => {
		await expectNoPageOverflow();
		for (const name of ["Zoom in", "Zoom out", "Fit view", "Reload achievement definitions"]) {
			await expectControlOnScreen(canvas.getByRole("button", { name }));
		}
	},
};
