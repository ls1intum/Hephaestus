import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, waitFor, within } from "storybook/test";

import { tracedArtifact } from "@/components/practice-trace/fixtures";
import { TraceRefusalAlert } from "@/components/practice-trace/TraceRefusalAlert";
import { levelsOpenedBy } from "@/test/detail-stack";

import { REQUEST_BLOCKED } from "./RequestReviewAction";
import { ReviewRowList } from "./ReviewRow";
import { UnreviewedWorkRow } from "./UnreviewedWorkRow";

/**
 * One row of the Reviews tab's work without a review: a piece of work none of whose moments started
 * a review, with its latest moment and how we came to know of it. The title opens the work's sheet,
 * which says why no review started; Track, Trace and Request review end the row.
 */
const requestReview = fn();

const meta = {
	component: UnreviewedWorkRow,
	parameters: { layout: "padded", chromatic: { viewports: [320, 1440] } },
	tags: ["autodocs"],
	args: {
		work: tracedArtifact(1430),
		request: { work: "issue #1430", onRequest: requestReview },
	},
	decorators: [
		(Story) => (
			<ReviewRowList label="Work without a review, most recent first">
				<Story />
			</ReviewRowList>
		),
	],
} satisfies Meta<typeof UnreviewedWorkRow>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * The row to scan: the work, its latest moment, and the way into its sheet, where the reason and
 * the setting that would let it through are. Track, Trace and Request review end it; Request review
 * asks first, since it starts an agent job.
 */
export const RefusedBySettings: Story = {
	play: async ({ canvas, userEvent }) => {
		requestReview.mockClear();
		canvas.getByText("No review started");
		canvas.getByText("Latest: Opened");
		await expect(
			canvas.queryByText("This workspace’s review settings did not allow a review of this work."),
		).not.toBeInTheDocument();
		await expect(
			levelsOpenedBy(canvas.getByRole("link", { name: "Define the practice-binding contract" })),
		).toEqual(["work:issue:1430"]);
		await expect(canvas.getByRole("button", { name: /^Track /u })).toHaveAttribute(
			"aria-pressed",
			"false",
		);
		await expect(levelsOpenedBy(canvas.getByRole("link", { name: /^Trace /u }))).toEqual([
			"trace:work:issue:1430",
		]);
		canvas.getByText("2 moments recorded · 0 started a review");
		// The repository once: the link carries it beside the provider mark.
		await expect(canvas.getAllByText(/ls1intum\/Hephaestus/u)).toHaveLength(1);
		await userEvent.click(canvas.getByRole("button", { name: /^Request review of /u }));
		const dialog = await screen.findByRole("alertdialog", {
			name: "Request a review of issue #1430?",
		});
		await expect(requestReview).not.toHaveBeenCalled();
		await userEvent.click(within(dialog).getByRole("button", { name: "Request review" }));
		await expect(requestReview).toHaveBeenCalledOnce();
	},
};

/**
 * A kind nobody can request a review for: the button stays in its place, disabled, and its
 * tooltip says why, so a keyboard reaches the reason too.
 */
export const NotRequestable: Story = {
	args: {
		work: tracedArtifact(88),
		request: { work: "conversation thread", blocked: REQUEST_BLOCKED.kind },
	},
	play: async ({ canvas, userEvent }) => {
		canvas.getByText("Latest: Message posted");
		canvas.getByText("Noticed during a sync");
		const button = canvas.getByRole("button", { name: /^Request review of /u });
		await expect(button).toHaveAttribute("aria-disabled", "true");
		await userEvent.hover(button);
		await waitFor(async () => expect(screen.getByText(REQUEST_BLOCKED.kind)).toBeVisible());
		await userEvent.click(button);
		await expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
	},
};

/** The ask is on its way: the button takes no second press until it lands. */
export const Requesting: Story = {
	args: { request: { work: "issue #1430", onRequest: requestReview, requesting: true } },
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("button", { name: /^Request review of /u })).toHaveAttribute(
			"aria-disabled",
			"true",
		);
	},
};

/** The ask started nothing: the refusal is said on the row it was asked from. */
export const RequestRefused: Story = {
	args: {
		refusal: (
			<TraceRefusalAlert
				refusal={{
					status: "REFUSED",
					reason: "BUDGET_EXHAUSTED",
					reasonDescription: "This workspace has used its AI budget for this period.",
				}}
				workspaceSlug="demo"
				canAdminister
			/>
		),
	},
	play: async ({ canvas }) => {
		canvas.getByText("This workspace has used its AI budget for this period.");
	},
};
