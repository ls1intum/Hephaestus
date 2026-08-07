import type { Meta, StoryObj } from "@storybook/react-vite";
import { expectNoPageOverflow } from "@/test/reflow";
import { TraceOutcomeBadge } from "./TraceOutcomeBadge";
import { OUTCOMES } from "./trace-format";

const meta = {
	title: "Practice trace/Outcome badge",
	component: TraceOutcomeBadge,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	args: { outcome: "REVIEWED" },
} satisfies Meta<typeof TraceOutcomeBadge>;

export default meta;
type Story = StoryObj<typeof meta>;

/** The happy case: the practice was measured. Says nothing about whether anyone heard about it. */
export const Default: Story = {};

/**
 * The full vocabulary. Each outcome carries its own icon and its own words, so none of them depends
 * on colour to be told apart.
 */
export const EveryOutcome: Story = {
	parameters: { layout: "padded" },
	render: () => (
		<ul className="flex flex-col items-start gap-2">
			{OUTCOMES.map((outcome) => (
				<li key={outcome}>
					<TraceOutcomeBadge outcome={outcome} />
				</li>
			))}
		</ul>
	),
};

export const Mobile: Story = {
	parameters: {
		layout: "padded",
		chromatic: { disableSnapshot: true },
		viewport: { defaultViewport: "reflow" },
	},
	render: () => (
		<ul className="flex flex-col items-start gap-2">
			{OUTCOMES.map((outcome) => (
				<li key={outcome}>
					<TraceOutcomeBadge outcome={outcome} />
				</li>
			))}
		</ul>
	),
	play: async () => {
		await expectNoPageOverflow();
	},
};
