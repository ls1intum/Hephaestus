import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect } from "storybook/test";
import { LoadingBlock } from "./LoadingBlock";

/**
 * Twelve hand-rolled spellings of "a centred spinner" collapsed to four sizes. The `label` is
 * required because the primitive's own fallback is the word "Loading", which does not say which of
 * several regions on a page is busy.
 */
const meta = {
	component: LoadingBlock,
	parameters: { layout: "padded" },
	args: { label: "Loading practices" },
	tags: ["autodocs"],
} satisfies Meta<typeof LoadingBlock>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
	play: async ({ canvas }) => {
		// One status region, named by what is actually loading.
		await expect(canvas.getAllByRole("status")).toHaveLength(1);
		await expect(canvas.getByRole("status", { name: "Loading practices" })).toBeVisible();
	},
};

export const Sizes: Story = {
	render: (args) => (
		<div className="divide-y">
			<LoadingBlock {...args} size="sm" label="Loading a panel" />
			<LoadingBlock {...args} size="lg" label="Loading a page" />
		</div>
	),
};

export const DarkMode: Story = {
	globals: { theme: "dark" },
};
