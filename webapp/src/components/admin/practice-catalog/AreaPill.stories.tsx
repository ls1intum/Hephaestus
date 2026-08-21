import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect } from "storybook/test";
import { AreaPill } from "./AreaPill";

const meta = {
	component: AreaPill,
	parameters: { layout: "centered" },
	args: { slug: "review-ready-work", name: "Review-ready work" },
	tags: ["autodocs"],
} satisfies Meta<typeof AreaPill>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};

export const Sizes: Story = {
	render: (args) => (
		<div className="flex items-center gap-3">
			<AreaPill {...args} size="sm" />
			<AreaPill {...args} size="md" />
			<AreaPill {...args} size="lg" />
		</div>
	),
};

export const Unassigned: Story = {
	args: { slug: undefined, name: undefined },
};

export const Announced: Story = {
	args: { srLabel: true },
	play: async ({ canvas, canvasElement }) => {
		// The one place the pill carries the name is where nothing visible repeats it, so it must
		// stay in the accessibility tree.
		await expect(canvas.getByText("Review-ready work:")).toHaveClass("sr-only");
		await expect(canvasElement.querySelector("span[aria-hidden='true']")).toBeNull();
	},
};

export const DarkMode: Story = {
	globals: { theme: "dark" },
};
