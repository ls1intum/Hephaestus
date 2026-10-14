import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, within } from "storybook/test";

import { RefusalFixLink } from "./RefusalFixLink";
import { TraceRefusalAlert } from "./TraceRefusalAlert";

/** A refused request is a successful HTTP read of a domain decision; keep its reason on the page. */
const meta = {
	component: TraceRefusalAlert,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
		refusal: {
			status: "REFUSED",
			reviewId: null,
			reason: "REVIEW_MODEL_UNBOUND",
			reasonDescription: "No AI model is set up to run reviews in this workspace.",
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
		await expect(
			alert.getByText("No AI model is set up to run reviews in this workspace."),
		).toBeVisible();
		await expect(alert.getByRole("link", { name: "Set up a review model" })).toHaveAttribute(
			"href",
			"/w/demo/admin/models",
		);
	},
};

/** Members cannot follow admin fix links; keep their current page usable without a dead-end link. */
export const WithoutAFix: Story = {
	args: { fix: undefined },
	play: async ({ canvas }) => {
		const alert = within(canvas.getByRole("alert"));
		await expect(
			alert.getByText("No AI model is set up to run reviews in this workspace."),
		).toBeVisible();
		await expect(alert.queryByRole("link")).not.toBeInTheDocument();
	},
};

/** A server that sent no sentence still gets a heading and a body rather than an empty alert. */
export const WithoutASentence: Story = {
	args: {
		refusal: { status: "REFUSED", reviewId: null, reason: null, reasonDescription: null },
		fix: undefined,
	},
	play: async ({ canvas }) => {
		const alert = within(canvas.getByRole("alert"));
		await expect(alert.getByText("No reason was given.")).toBeVisible();
		await expect(alert.queryByRole("link")).not.toBeInTheDocument();
	},
};
