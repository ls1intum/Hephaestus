import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect } from "storybook/test";
import { DeliveryTrace } from "./DeliveryTrace";

const composedAt = new Date("2026-07-28T13:42:00Z");
const deliveredAt = new Date("2026-07-28T13:43:00Z");

/**
 * What became of one piece of feedback, as the two or three things that actually happened to it.
 *
 * The delivery model is four orthogonal enums — place, outcome, placement and withholding reason —
 * and the screens used to show four scattered fragments of it, one cell printing the reason *or* the
 * place depending on which happened to be set. A reader could not tell whether they were being told
 * what happened or where it would have happened.
 *
 * The trace reads as a sentence instead: it was composed, something may have stopped it, it ended up
 * somewhere. The last step keeps the two axes on separate lines — the badge says what happened, the
 * line under it says where — and the middle step appears only when a gate had a say, because a step
 * reading "nothing stopped this" on every delivered row is a step nobody reads.
 */
const meta = {
	title: "Shared/Practice vocabulary/Delivery trace",
	component: DeliveryTrace,
	parameters: { layout: "padded", chromatic: { viewports: [320, 768] } },
	tags: ["autodocs"],
	args: {
		feedback: {
			channel: "IN_CONTEXT",
			deliveryState: "DELIVERED",
			createdAt: composedAt,
			deliveredAt,
			placements: [{ id: "p1", placementType: "SUMMARY" }],
		},
	},
} satisfies Meta<typeof DeliveryTrace>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Two steps: composed, then posted. Nothing stopped it, so nothing says so. */
export const Delivered: Story = {
	play: async ({ canvas }) => {
		canvas.getByText("Composed");
		canvas.getByText("Delivered");
		canvas.getByText(/As a summary comment on the work/);
	},
};

/**
 * The precise spot, not the lane. An operator chasing a delivery wants the shape of the thing that
 * was posted; a piece of feedback can be both a summary and a set of inline notes.
 */
export const DeliveredInlineAndSummary: Story = {
	args: {
		feedback: {
			channel: "IN_CONTEXT",
			deliveryState: "DELIVERED",
			createdAt: composedAt,
			deliveredAt,
			placements: [
				{ id: "p1", placementType: "SUMMARY" },
				{ id: "p2", placementType: "INLINE" },
			],
		},
	},
	play: async ({ canvas }) => {
		canvas.getByText(/As an inline note on the work/);
	},
};

/** Three steps. The family names who decided; the sentence says what they decided. */
export const Withheld: Story = {
	args: {
		feedback: {
			channel: "IN_CONTEXT",
			deliveryState: "SUPPRESSED",
			suppressionReason: "ARTIFACT_MERGED",
			createdAt: composedAt,
		},
	},
	play: async ({ canvas }) => {
		canvas.getByText("The work moved on");
		canvas.getByText("The work was already merged, so a note on it would arrive too late.");
		canvas.getByText("Withheld");
	},
};

/**
 * The state that used to read "Awaiting conversation". `PREPARED` exists on the conversation lane
 * and nowhere else, so the badge names the queue and the thing that empties it. There is no
 * delivered timestamp, because it has not happened.
 */
export const QueuedForConversation: Story = {
	args: {
		feedback: {
			channel: "CONVERSATION",
			deliveryState: "PREPARED",
			createdAt: composedAt,
		},
	},
	play: async ({ canvas }) => {
		canvas.getByText("Queued for conversation");
		canvas.getByText("In conversation");
	},
};

/** Delivered on the conversation lane: the developer turned up, and the mentor raised it. */
export const DeliveredInConversation: Story = {
	args: {
		feedback: {
			channel: "CONVERSATION",
			deliveryState: "DELIVERED",
			createdAt: composedAt,
			deliveredAt,
			placements: [{ id: "p1", placementType: "CONVERSATION_TURN" }],
		},
	},
	play: async ({ canvas }) => {
		canvas.getByText("Delivered in conversation");
		canvas.getByText(/As a turn in the conversation/);
	},
};

/**
 * A queue entry that timed out. Stored as an ordinary withholding, and the label keeps that stem so
 * the Outcome filter is findable, while the second clause carries the fact "withheld" alone loses:
 * nobody decided anything, the chat simply never came.
 */
export const WithheldNeverRaised: Story = {
	args: {
		feedback: {
			channel: "CONVERSATION",
			deliveryState: "SUPPRESSED",
			suppressionReason: "CONVERSATION_EXPIRED",
			createdAt: composedAt,
		},
	},
	play: async ({ canvas }) => {
		canvas.getByText("Withheld, never raised");
		canvas.getByText("Housekeeping");
	},
};

/** A gate never had a say, so there is no middle step — the attempt itself failed. */
export const FailedToDeliver: Story = {
	args: {
		feedback: {
			channel: "IN_CONTEXT",
			deliveryState: "FAILED",
			createdAt: composedAt,
		},
	},
	play: async ({ canvas }) => {
		canvas.getByText("Failed to deliver");
	},
};

/**
 * The server writes one placement per inline note, so a real review carries several of the same
 * shape. The step names the distinct shapes; how many there were, and where each one landed, is the
 * list of anchors the detail page renders under the trace.
 */
export const ManyInlineNotes: Story = {
	args: {
		feedback: {
			channel: "IN_CONTEXT",
			deliveryState: "DELIVERED",
			createdAt: composedAt,
			deliveredAt,
			placements: [
				{ id: "p1", placementType: "SUMMARY" },
				{ id: "p2", placementType: "INLINE" },
				{ id: "p3", placementType: "INLINE" },
				{ id: "p4", placementType: "INLINE" },
			],
		},
	},
	play: async ({ canvas, canvasElement }) => {
		canvas.getByText(/As a summary comment on the work/);
		// Three inline placements, one phrase. Counted over the whole trace rather than queried,
		// because a repeat would render inside the same paragraph and still satisfy a `getByText`.
		const inlinePhrases = canvasElement.textContent?.match(/As an inline note on the work/g) ?? [];
		await expect(inlinePhrases).toHaveLength(1);
	},
};

/**
 * `deliveredAt` survives being replaced, so it is when this was *posted*, not when it was replaced.
 * The word "delivered" in front of it is what stops the last step being read as the latter.
 */
export const ReplacedByNewer: Story = {
	args: {
		feedback: {
			channel: "IN_CONTEXT",
			deliveryState: "SUPERSEDED",
			createdAt: composedAt,
			deliveredAt,
			placements: [{ id: "p1", placementType: "SUMMARY" }],
		},
	},
	play: async ({ canvas }) => {
		canvas.getByText("Replaced by newer");
		canvas.getByText(/delivered/);
	},
};

/** The longest reason sentence in a narrow column, which is where a trace would break a layout. */
export const Narrow: Story = {
	args: {
		feedback: {
			channel: "IN_CONTEXT",
			deliveryState: "SUPPRESSED",
			suppressionReason: "VOLUME_CAPPED",
			createdAt: composedAt,
		},
	},
	parameters: { viewport: { defaultViewport: "reflow" } },
	render: (args) => (
		<div className="w-64 border p-3">
			<DeliveryTrace {...args} />
		</div>
	),
};
