import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect } from "storybook/test";

import { reviewLevel } from "@/components/admin/practice-reviews/review-levels";
import { InlineLink } from "@/components/common/InlineLink";
import { DetailStackLink } from "@/components/layout/detail-drawer/DetailStackLink";
import { levelsOpenedBy } from "@/test/detail-stack";

import { practiceScriptRuns } from "./fixtures";
import { PracticeScriptTraceLine } from "./PracticeScriptTraceLine";

const REVIEW = "11111111-1111-1111-1111-111111111111";
const DETAILS = "Details of the practice script for Thin controllers";

/**
 * How a practice's script ran in one review, as an admin reads it under the practice. The line is
 * muted text and never a badge: it is about the script that reviewed the practice, not about the
 * developer's work, so it must not read as the practice's outcome.
 */
const meta = {
	component: PracticeScriptTraceLine,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
		workspaceSlug: "demo",
		practiceSlug: "thin-controllers",
		practiceName: "Thin controllers",
		reviewId: REVIEW,
		run: practiceScriptRuns.returned,
	},
	argTypes: { run: { control: false } },
} satisfies Meta<typeof PracticeScriptTraceLine>;

export default meta;
type Story = StoryObj<typeof meta>;

/** *Details* opens the review's *Practice scripts* section on Practice reviews. */
export const Default: Story = {
	play: async ({ canvas }) => {
		const details = canvas.getByRole("link", { name: DETAILS });
		await expect(details.getAttribute("href")).toMatch(/^\/w\/demo\/admin\/practices\/reviews\?/u);
		await expect(levelsOpenedBy(details)).toStrictEqual([`review:${REVIEW}`]);
	},
};

/**
 * Inside a drawer stack, the caller opens the review over the stack it already has, so *Details*
 * keeps the reader's place.
 */
export const DetailsInAStack: Story = {
	args: {
		reviewLink: (reviewId, label) => (
			<InlineLink
				className="inline-flex items-center gap-1"
				render={<DetailStackLink entry={reviewLevel(reviewId)} />}
			>
				{label}
			</InlineLink>
		),
	},
	play: async ({ canvas }) => {
		const details = canvas.getByRole("link", { name: DETAILS });
		await expect(details.getAttribute("href")).not.toMatch(/\/admin\/practices\/reviews/u);
		await expect(levelsOpenedBy(details)).toStrictEqual([`review:${REVIEW}`]);
	},
};

/** On the review's own sheet the line sits beside its *Practice scripts* section, so it has no *Details*. */
export const OnTheReviewSheet: Story = {
	args: { run: practiceScriptRuns.failed, details: false },
	play: async ({ canvas }) => {
		await expect(canvas.queryByRole("link", { name: DETAILS })).not.toBeInTheDocument();
	},
};
