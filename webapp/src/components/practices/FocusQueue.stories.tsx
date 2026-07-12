import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, userEvent, within } from "storybook/test";
import { FocusQueue, FocusQueueSkeleton } from "@/components/practices/FocusQueue";
import { MY_REPORT_CARDS, SPARSE_REPORT_CARDS } from "@/components/practices/story-data";

/**
 * The developer self view: at most three practices to focus on this cycle, each earned by
 * concrete evidence with a deep link and one next step. Strengths are affirmed compactly and
 * everything else stays one tap away, so the default view fits on one screen.
 */
const meta = {
	component: FocusQueue,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: { cards: MY_REPORT_CARDS },
} satisfies Meta<typeof FocusQueue>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * A filled report: three focus cards (worst status first, declining trends breaking ties),
 * strengths as chips, and the quieter practices behind a disclosure.
 */
export const Filled: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		// The focus rule caps at three and leads with the developing practices.
		await expect(await canvas.findByText("Include tests with the change")).toBeInTheDocument();
		await expect(canvas.getByText("Handle failure paths deliberately")).toBeInTheDocument();
		await expect(canvas.getByText("Describe what changed and why")).toBeInTheDocument();
		// The first focus card expands to its remaining evidence.
		await userEvent.click(canvas.getAllByRole("button", { name: /More evidence · 1/ })[0]);
		await expect(
			await canvas.findByText("Cart totals rounding change lands without covering the new branch"),
		).toBeInTheDocument();
		// Everything else stays behind a disclosure until asked for.
		await expect(
			canvas.queryByText("Leave specific, actionable review comments"),
		).not.toBeInTheDocument();
		await userEvent.click(canvas.getByRole("button", { name: /Everything else · 2/ }));
		await expect(
			await canvas.findByText("Leave specific, actionable review comments"),
		).toBeInTheDocument();
	},
};

/** A first-cycle report: one strength, nothing needing special focus. */
export const SparseNewWorkspace: Story = {
	args: { cards: SPARSE_REPORT_CARDS },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(
			await canvas.findByText(
				"Nothing needs special focus right now. Keep working the way you do.",
			),
		).toBeInTheDocument();
	},
};

/** No activity at all yet. */
export const Empty: Story = {
	args: { cards: [] },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(await canvas.findByText("Your first focus arrives soon")).toBeInTheDocument();
	},
};

/** Loading skeleton mirroring the focus-card layout. */
export const Loading: Story = {
	render: () => <FocusQueueSkeleton />,
};
