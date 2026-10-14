import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, within } from "storybook/test";

import { RefusalFixLink } from "./RefusalFixLink";
import { TraceRefusalAlert } from "./TraceRefusalAlert";

// A refused domain request can be a successful HTTP read; retain its reason on the page.
const meta = {
	component: TraceRefusalAlert,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
		refusal: {
			status: "REFUSED",
			reviewId: null,
			reason: "REVIEW_MODEL_UNBOUND",
		},
		fix: <RefusalFixLink workspaceSlug="demo" reason="REVIEW_MODEL_UNBOUND" />,
	},
	argTypes: { fix: { control: false } },
} satisfies Meta<typeof TraceRefusalAlert>;

export default meta;
type Story = StoryObj<typeof meta>;

export const OffersTheFixToAnAdmin: Story = {
	play: async ({ canvas }) => {
		const alert = within(canvas.getByRole("alert"));
		await expect(alert.getByText("No AI model is assigned to practice review.")).toBeVisible();
		await expect(alert.getByRole("link", { name: "Set up a review model" })).toHaveAttribute(
			"href",
			"/w/demo/admin/models",
		);
	},
};

// Members cannot use admin fix links; an alert must not send them to an inaccessible page.
export const WithoutAFix: Story = {
	args: { fix: undefined },
	play: async ({ canvas }) => {
		const alert = within(canvas.getByRole("alert"));
		await expect(alert.getByText("No AI model is assigned to practice review.")).toBeVisible();
		await expect(alert.queryByRole("link")).not.toBeInTheDocument();
	},
};

export const WithoutAReason: Story = {
	args: {
		refusal: { status: "REFUSED", reviewId: null, reason: null },
		fix: undefined,
	},
	play: async ({ canvas }) => {
		const alert = within(canvas.getByRole("alert"));
		await expect(alert.getByText("No reason was given.")).toBeVisible();
		await expect(alert.queryByRole("link")).not.toBeInTheDocument();
	},
};
