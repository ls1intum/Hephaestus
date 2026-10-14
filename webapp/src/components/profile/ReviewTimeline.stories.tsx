import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect } from "storybook/test";

import type { ProfileReview } from "@/api/types.gen";
import {
	detailReview,
	listedResult,
	reviewRef,
	searchedAndFoundNothing,
} from "@/stories/practice-detail-story-mock-data";
import { conversation } from "@/stories/practice-profile-story-mock-data";
import { daysBefore } from "@/stories/story-clock";

import { ReviewTimeline } from "./ReviewTimeline";

const THREAD_REVIEW = "00000000-0000-0000-0000-000000000201";

/** Two reviews of different kinds of item, each with a not met result whose problems open. */
const reviews: ProfileReview[] = [
	detailReview,
	{
		review: reviewRef(THREAD_REVIEW, conversation("#releases"), daysBefore(5)),
		results: [
			{
				...listedResult(searchedAndFoundNothing),
				id: "00000000-0000-0000-0000-000000000202",
				reviewId: THREAD_REVIEW,
				practiceSlug: "asks-answerable-questions",
				practiceName: "Ask questions a teammate can answer",
				summary: "The question leaves out the attempted fix",
			},
		],
		feedback: [],
	},
];

const meta = {
	component: ReviewTimeline,
	tags: ["autodocs"],
	parameters: { layout: "padded" },
	args: { reviews },
} satisfies Meta<typeof ReviewTimeline>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * A group's feed: every result with problems open on arrival, each row naming its practice, and
 * each closing on its own without disturbing the others.
 */
export const Default: Story = {
	play: async ({ canvas, userEvent }) => {
		const newest = canvas.getByRole("button", { name: /The refactor and the fix/u });
		const earlier = canvas.getByRole("button", { name: /The question leaves out/u });
		await expect(newest).toHaveAttribute("aria-expanded", "true");
		await expect(earlier).toHaveAttribute("aria-expanded", "true");
		await expect(canvas.getAllByRole("list", { name: "Problems" })).toHaveLength(2);
		await expect(canvas.getByText("Ask questions a teammate can answer")).toBeVisible();
		await userEvent.click(earlier);
		await expect(earlier).toHaveAttribute("aria-expanded", "false");
		await expect(canvas.getAllByRole("list", { name: "Problems" })).toHaveLength(1);
	},
};

/**
 * On a practice's own level no row repeats the practice under its summary, and only the newest
 * review's row arrives open: the rest are the same practice's history, each a press away.
 */
export const OnThePracticeLevel: Story = {
	args: { showPracticeName: false, initiallyOpen: "newest" },
	play: async ({ canvas, userEvent }) => {
		await expect(canvas.queryByText("Ask questions a teammate can answer")).not.toBeInTheDocument();
		await expect(canvas.getByText("The question leaves out the attempted fix")).toBeVisible();

		const newest = canvas.getByRole("button", { name: /The refactor and the fix/u });
		const earlier = canvas.getByRole("button", { name: /The question leaves out/u });
		await expect(newest).toHaveAttribute("aria-expanded", "true");
		await expect(earlier).toHaveAttribute("aria-expanded", "false");

		await userEvent.click(earlier);
		await expect(earlier).toHaveAttribute("aria-expanded", "true");
		await expect(newest).toHaveAttribute("aria-expanded", "true");
	},
};

/** No reviews: an empty rail, which the feed around it replaces with its own empty state. */
export const Empty: Story = {
	args: { reviews: [] },
	play: async ({ canvas }) => {
		await expect(canvas.queryAllByRole("listitem")).toHaveLength(0);
	},
};
