import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, within } from "storybook/test";

import { RefusalFixLink } from "./RefusalFixLink";
import { TraceRefusalAlert } from "./TraceRefusalAlert";

/**
 * Why an ask started nothing. A refusal is a 200 carrying the workspace's own answer, not an error,
 * so it is shown on the page rather than as a toast — a toast is gone before the reader has
 * finished reading it.
 *
 * The sentence is the server's, verbatim. The fix is a node the route renders beside it, keyed on
 * the coded reason, so re-worded prose can never cost a reader the way out.
 */
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

/** An admin can go and undo this one, so the route passes the way out with the sentence. */
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

/**
 * The same refusal for a member. The route passes no link rather than one that bounces: `/admin`
 * turns every non-admin away, losing the page they were reading.
 */
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
