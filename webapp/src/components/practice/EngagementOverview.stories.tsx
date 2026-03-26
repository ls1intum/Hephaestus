import type { Meta, StoryObj } from "@storybook/react";
import { EngagementOverview } from "./EngagementOverview";

const meta = {
	component: EngagementOverview,
	parameters: {
		layout: "padded",
		docs: {
			description: {
				component:
					"Displays engagement statistics with a progress ring showing the percentage of findings reviewed, plus applied/disputed/N/A counts.",
			},
		},
	},
	tags: ["autodocs"],
	decorators: [
		(Story) => (
			<div className="max-w-lg">
				<Story />
			</div>
		),
	],
} satisfies Meta<typeof EngagementOverview>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * High engagement — most findings have been reviewed.
 */
export const HighEngagement: Story = {
	args: {
		engagement: { applied: 12, disputed: 3, notApplicable: 2 },
		totalFindings: 20,
	},
};

/**
 * Low engagement — few findings reviewed.
 */
export const LowEngagement: Story = {
	args: {
		engagement: { applied: 1, disputed: 0, notApplicable: 0 },
		totalFindings: 15,
	},
};

/**
 * Full engagement — all findings reviewed.
 */
export const FullEngagement: Story = {
	args: {
		engagement: { applied: 8, disputed: 2, notApplicable: 1 },
		totalFindings: 11,
	},
};

/**
 * Zero engagement — no findings reviewed yet.
 */
export const ZeroEngagement: Story = {
	args: {
		engagement: { applied: 0, disputed: 0, notApplicable: 0 },
		totalFindings: 10,
	},
};
