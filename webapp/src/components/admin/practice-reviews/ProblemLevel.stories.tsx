import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, within } from "storybook/test";

import { withPageBehind } from "@/stories/decorators";
import { InLevelStack } from "@/stories/level-stack";
import { settledDrawerPanel } from "@/stories/overlay";
import { levelsOpenedBy } from "@/test/detail-stack";

import { evidenceHiddenProblemDetail, reviewProblemDetail } from "./fixtures";
import { ProblemLevel } from "./ProblemLevel";
import { problemLevel } from "./review-levels";

/**
 * One problem under a Not met result, opened from the Problems list or the trace: its severity, where
 * it is, the passages that show it and the next step. It links to its result, which carries the marks.
 */
const meta = {
	component: ProblemLevel,
	parameters: { layout: "fullscreen" },
	decorators: [withPageBehind],
	tags: ["autodocs"],
	args: {
		path: { behind: [{ label: "Practice reviews", depth: 0 }], onClose: fn() },
		problem: { status: "ready", problem: reviewProblemDetail },
	},
	argTypes: { path: { control: false } },
	render: (args) => (
		<InLevelStack entry={problemLevel(reviewProblemDetail.id)} path={args.path}>
			{(level) => <ProblemLevel {...args} {...level} />}
		</InLevelStack>
	),
} satisfies Meta<typeof ProblemLevel>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
	play: async () => {
		const panel = within(await settledDrawerPanel());
		panel.getByText(reviewProblemDetail.summary);
		panel.getByRole("heading", { name: "Evidence" });
		await expect(
			levelsOpenedBy(
				panel.getByRole("link", {
					name: reviewProblemDetail.result.summary ?? reviewProblemDetail.result.practiceName,
				}),
			),
		).toEqual([`result:${reviewProblemDetail.resultId}`]);
		await expect(panel.queryByRole("button", { name: /^Mark/u })).not.toBeInTheDocument();
	},
};

/** Hidden evidence says so, and never stands in an invented reason or passage. */
export const EvidenceHidden: Story = {
	args: { problem: { status: "ready", problem: evidenceHiddenProblemDetail } },
	play: async () => {
		const panel = within(await settledDrawerPanel());
		panel.getByText("The evidence of this problem is hidden.");
		await expect(panel.queryByRole("heading", { name: "Evidence" })).not.toBeInTheDocument();
	},
};

export const Loading: Story = {
	args: { problem: { status: "loading" } },
};

export const LoadFailed: Story = {
	args: { problem: { status: "error", error: { status: 500 }, onRetry: fn() } },
	play: async () => {
		const panel = within(await settledDrawerPanel());
		panel.getByText("We could not load this problem");
	},
};
