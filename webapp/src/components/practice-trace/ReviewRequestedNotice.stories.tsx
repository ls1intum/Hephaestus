import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, within } from "storybook/test";

import { reviewLevel } from "@/components/admin/practice-reviews/review-levels";
import { DetailStackLink } from "@/components/layout/detail-drawer/DetailStackLink";
import { levelsOpenedBy } from "@/test/detail-stack";

import { ReviewRequestedNotice } from "./ReviewRequestedNotice";

const REQUESTED_ID = "0f2b7c1e-9a3d-4c5b-8e1f-2d6a7b8c9d01";

// The way to an accepted request's review, which outlives the toast that announced it.
const meta = {
	component: ReviewRequestedNotice,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
		review: <DetailStackLink entry={reviewLevel(REQUESTED_ID)} single />,
	},
	argTypes: { review: { control: false } },
} satisfies Meta<typeof ReviewRequestedNotice>;

export default meta;
type Story = StoryObj<typeof meta>;

export const OpensTheRequestedReview: Story = {
	play: async ({ canvas }) => {
		const note = within(canvas.getByRole("note"));
		await expect(note.getByText("Review requested")).toBeVisible();
		await expect(
			levelsOpenedBy(note.getByRole("link", { name: "View requested review" })),
		).toStrictEqual([`review:${REQUESTED_ID}`]);
		// Accepted is not started: the new review's own level says whether it is queued or running.
		await expect(note.queryByText(/running|queued|started/iu)).toBeNull();
		// The toast announced it; a second live region would say it again.
		await expect(canvas.queryByRole("alert")).toBeNull();
	},
};
