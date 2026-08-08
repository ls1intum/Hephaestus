import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, within } from "storybook/test";
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

export const Default: Story = {};

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
	play: async ({ canvasElement }) => {
		// WCAG 2.2 SC 1.4.1: every outcome is told apart by an icon and words, never by colour alone.
		const badges = within(canvasElement).getAllByRole("listitem");
		await expect(badges).toHaveLength(OUTCOMES.length);
		for (const badge of badges) {
			await expect(badge.querySelector("svg")).not.toBeNull();
			await expect(badge.textContent?.trim()).toBeTruthy();
		}
	},
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
