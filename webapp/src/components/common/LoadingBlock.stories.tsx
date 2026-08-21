import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect } from "storybook/test";
import { LoadingBlock } from "./LoadingBlock";

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
		// One live region, and what it announces is what is loading.
		await expect(canvas.getAllByRole("status")).toHaveLength(1);
		canvas.getByText("Loading practices");
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
