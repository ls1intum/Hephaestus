import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, userEvent } from "storybook/test";
import { DrillDownSheet } from "@/components/practices/DrillDownSheet";
import { MY_REPORT_CARDS, ROSTER } from "@/components/practices/story-data";

/**
 * The drill-down side panel behind every matrix row: practices grouped under their area
 * identity, one line each (name, sparkline, count, status) that expands to evidence with a
 * deep link. The mentor reads the same cards the developer sees.
 */
const meta = {
	component: DrillDownSheet,
	parameters: { layout: "fullscreen" },
	tags: ["autodocs"],
	args: {
		developer: ROSTER[0],
		cards: MY_REPORT_CARDS,
		open: true,
		onOpenChange: fn(),
	},
} satisfies Meta<typeof DrillDownSheet>;

export default meta;
type Story = StoryObj<typeof meta>;

/** A filled report with the attention reason in the header. Rows expand to evidence. */
export const Filled: Story = {
	play: async () => {
		// The sheet renders in a portal, so query the whole screen.
		await expect(await screen.findByText("Priya Raghavan")).toBeInTheDocument();
		await expect(
			screen.getByText("Testing your changes: gaps to work on this cycle"),
		).toBeInTheDocument();
		// A practice line expands to its evidence with the artifact deep link.
		await userEvent.click(screen.getByRole("button", { name: /Include tests with the change/ }));
		await expect(
			await screen.findByText("Retry handling for payment webhooks ships with no test"),
		).toBeInTheDocument();
		await expect(
			screen.getByRole("link", { name: "Add retry handling to payment webhooks" }),
		).toHaveAttribute("href", "https://github.com/nimbus/payments-api/pull/482");
	},
};

/** A developer without attention flags gets the neutral description. */
export const WithoutAttentionReasons: Story = {
	args: { developer: ROSTER[2] },
};

/** The report is still loading. */
export const Loading: Story = {
	args: { cards: undefined, isLoading: true },
};

/** The report failed to load, with a retry action. */
export const ErrorWithRetry: Story = {
	args: { cards: undefined, isError: true, onRetry: fn() },
	play: async ({ args }) => {
		await userEvent.click(await screen.findByRole("button", { name: "Try again" }));
		await expect(args.onRetry).toHaveBeenCalled();
	},
};

/** No signals yet for this developer. */
export const Empty: Story = {
	args: { developer: ROSTER[5], cards: [] },
};
