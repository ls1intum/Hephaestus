import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, screen, userEvent, waitFor, within } from "storybook/test";

import { StandingBasisNote } from "./StandingBasisNote";

/**
 * Beside a standing on the Practice profile and on Practices across the workspace: whether it includes
 * earlier assessments. Nothing shows for a standing from current reviews.
 */
const meta = {
	component: StandingBasisNote,
	tags: ["autodocs"],
	parameters: { layout: "padded" },
	args: { basis: "EARLIER_REVIEWS" },
} satisfies Meta<typeof StandingBasisNote>;

export default meta;
type Story = StoryObj<typeof meta>;

export const EarlierReviews: Story = {
	play: async ({ canvas }) => {
		await userEvent.click(canvas.getByRole("button", { name: "Based on earlier reviews" }));
		const dialog = await screen.findByRole("dialog", { name: "Based on earlier reviews" });
		await waitFor(async () =>
			expect(
				within(dialog).getByText(
					/They do not show whether the current criteria are met\. No trend or feedback quotes are shown for those assessments\./u,
				),
			).toBeVisible(),
		);
	},
};

/** The reviews recorded no practice version, so the note says their criteria are unknown. */
export const CriteriaUnknown: Story = {
	args: { basis: "EARLIER_REVIEWS_CRITERIA_UNKNOWN" },
	play: async ({ canvas }) => {
		await expect(
			canvas.getByRole("button", { name: "Based on earlier reviews, criteria unknown" }),
		).toHaveAccessibleDescription(/The original criterion version is unavailable\./u);
	},
};

/** A standing from current reviews needs no words. */
export const CurrentReviews: Story = {
	args: { basis: "CURRENT_REVIEWS" },
	play: async ({ canvas }) => {
		await expect(canvas.queryByRole("button")).toBeNull();
	},
};

/** No verdict, no basis. */
export const NoVerdict: Story = {
	args: { basis: null },
	play: async ({ canvas }) => {
		await expect(canvas.queryByRole("button")).toBeNull();
	},
};

export const Dark: Story = {
	args: { basis: "EARLIER_REVIEWS_CRITERIA_UNKNOWN" },
	globals: { theme: "dark" },
};
