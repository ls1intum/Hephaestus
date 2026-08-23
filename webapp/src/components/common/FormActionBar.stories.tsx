import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect } from "storybook/test";
import { Button } from "@/components/ui/button";
import { expectWithinViewport } from "@/test/reflow";
import { FormActionBar } from "./FormActionBar";

/**
 * The practice form is several viewport-heights tall, so an action button in normal
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
		// The whole point of the component, and nothing asserted it: a `sticky` that regressed to
		// `relative` looks identical until the reader scrolls.
		const save = canvas.getByRole("button", { name: "Save changes" });
		window.scrollTo(0, document.documentElement.scrollHeight);
		await expect(window.scrollY).toBeGreaterThan(0);
		await expectWithinViewport(save);
	},
};

export const PrimaryOnly: Story = {
	args: { secondary: undefined },
};

export const NarrowViewport: Story = {
	parameters: { viewport: { defaultViewport: "reflow" }, chromatic: { viewports: [320] } },
	play: async ({ canvas }) => {
		await expectWithinViewport(canvas.getByRole("button", { name: "Save changes" }));
	},
};
