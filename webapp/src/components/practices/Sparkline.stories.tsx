import type { Meta, StoryObj } from "@storybook/react-vite";
import { Sparkline } from "@/components/practices/Sparkline";

/**
 * A tiny inline activity sparkline: observation volume over recent weeks, deliberately
 * unlabeled and unscaled. It answers "is there recent signal here" without inviting numeric
 * comparison between people.
 */
const meta = {
	component: Sparkline,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	args: {
		values: [0, 1, 3, 2, 4, 2],
		label: "Observation activity over the last six weeks",
	},
} satisfies Meta<typeof Sparkline>;

export default meta;
type Story = StoryObj<typeof meta>;

/** A typical six-week activity line. */
export const Default: Story = {};

/** A recent spike after quiet weeks. */
export const RecentSpike: Story = {
	args: { values: [0, 0, 0, 0, 1, 5] },
};

/** All-zero signal renders nothing at all (no misleading flat line). */
export const NoSignal: Story = {
	render: (args) => (
		<div className="flex items-center gap-2 text-xs text-muted-foreground">
			<Sparkline {...args} />
			(deliberately empty)
		</div>
	),
	args: { values: [0, 0, 0, 0, 0, 0] },
};
