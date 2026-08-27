import type { Meta, StoryObj } from "@storybook/react";
import { expect } from "storybook/test";

import { AboutCallToActionSection } from "./AboutCallToActionSection";

const meta = {
	component: AboutCallToActionSection,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
} satisfies Meta<typeof AboutCallToActionSection>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("link", { name: /View on GitHub/ })).toHaveAttribute(
			"href",
			"https://github.com/ls1intum/Hephaestus",
		);
		await expect(canvas.getByRole("link", { name: /Read the contributor guide/ })).toHaveAttribute(
			"href",
			"https://ls1intum.github.io/Hephaestus/contributor/overview",
		);
	},
};
