import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect } from "storybook/test";
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

/**
 * The words on every badge, in the order the outcome union declares them.
 *
 * Written out rather than read back off `TRACE_OUTCOME_DEFS`: an outcome the server adds, or a label
 * changed to something a reader cannot act on, has to show up here as a diff somebody approved.
 */
const EVERY_LABEL = [
	"Reviewed",
	"Running",
	"Waiting",
	"Skipped",
	"Couldn't assess",
	"Turned off",
	"Not triggered",
	"Waiting on a connection",
	"Expired",
	"Failed",
];

const everyOutcome = () => (
	<ul className="flex flex-col items-start gap-2">
		{OUTCOMES.map((outcome) => (
			<li key={outcome}>
				<TraceOutcomeBadge outcome={outcome} />
			</li>
		))}
	</ul>
);

export const EveryOutcome: Story = {
	parameters: { layout: "padded" },
	render: everyOutcome,
	play: async ({ canvas }) => {
		const badges = canvas.getAllByRole("listitem");
		await expect(badges.map((badge) => badge.textContent.trim())).toEqual(EVERY_LABEL);
		// WCAG 2.2 SC 1.4.1: every outcome is told apart by an icon as well as words, never by colour.
		for (const badge of badges) {
			await expect(badge.querySelector("svg")).not.toBeNull();
		}
	},
};

export const Mobile: Story = {
	parameters: {
		layout: "padded",
		chromatic: { disableSnapshot: true },
		viewport: { defaultViewport: "reflow" },
	},
	render: everyOutcome,
	play: async () => {
		await expectNoPageOverflow();
	},
};
