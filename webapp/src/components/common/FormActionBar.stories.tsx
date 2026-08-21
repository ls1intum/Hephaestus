import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect } from "storybook/test";
import { Button } from "@/components/ui/button";
import { FormActionBar } from "./FormActionBar";

/**
 * The practice form carries over 800px of fixed-minimum field height, so an action button in normal
 * flow is below the fold before the reader has typed anything. This keeps it on screen for the whole
 * scroll.
 */
const meta = {
	component: FormActionBar,
	parameters: { layout: "fullscreen" },
	args: {
		secondary: <Button variant="outline">Cancel</Button>,
		children: <Button type="submit">Save changes</Button>,
	},
	decorators: [
		(Story) => (
			<div className="mx-auto max-w-3xl px-4">
				<div className="h-[120vh] space-y-4 py-6">
					<p className="text-sm text-muted-foreground">
						A form taller than the viewport. The bar below stays put while this scrolls.
					</p>
				</div>
				<Story />
			</div>
		),
	],
	tags: ["autodocs"],
} satisfies Meta<typeof FormActionBar>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
	play: async ({ canvas }) => {
		const bar = canvas.getByRole("button", { name: "Save changes" }).parentElement;
		// Sticky rather than fixed, so it tracks the form's column instead of the whole page.
		await expect(getComputedStyle(bar as HTMLElement).position).toBe("sticky");
	},
};

export const PrimaryOnly: Story = {
	args: { secondary: undefined },
};

export const NarrowViewport: Story = {
	parameters: { viewport: { defaultViewport: "reflow" }, chromatic: { viewports: [320] } },
};

export const DarkMode: Story = {
	globals: { theme: "dark" },
};
