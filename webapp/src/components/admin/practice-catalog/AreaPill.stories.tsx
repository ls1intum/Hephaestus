import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect } from "storybook/test";
import { AreaPill } from "./AreaPill";

/**
 * One mark for a practice area, so the same area looks the same in a workspace tree, an instance
 * tree, a review row and a detail header. The colour is what an administrator learns to scan by, so
 * it may not be re-decided per surface.
 */
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

export const Decorative: Story = {
	play: async ({ canvasElement }) => {
		// Everywhere else the name is beside it, so announcing it twice would be noise.
		// The pill itself is hidden; only the icon inside it would be anyway.
		await expect(canvasElement.querySelector("span[aria-hidden='true']")).not.toBeNull();
	},
};

export const DarkMode: Story = {
	globals: { theme: "dark" },
};
