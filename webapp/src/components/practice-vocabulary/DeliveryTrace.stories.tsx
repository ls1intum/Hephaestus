import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect } from "storybook/test";
import { DeliveryTrace } from "./DeliveryTrace";

const composedAt = new Date("2026-07-28T13:42:00Z");
const deliveredAt = new Date("2026-07-28T13:43:00Z");

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

export const Delivered: Story = {
	play: async ({ canvas }) => {
		canvas.getByText("Composed");
		canvas.getByText("Delivered");
		canvas.getByText(/As a summary comment on the work/);
	},
};

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

/** `PREPARED` only ever occurs on the conversation lane, and carries no delivered timestamp. */
export const PreparedForConversation: Story = {
	args: {
		feedback: {
			channel: "CONVERSATION",
			deliveryState: "PREPARED",
			createdAt: composedAt,
		},
	},
	play: async ({ canvas }) => {
		canvas.getByText("Prepared for conversation");
		canvas.getByText("In conversation");
	},
};

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

/** A queue entry that timed out: the server stores it as an ordinary withholding. */
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
		// Counted over the whole trace rather than queried: a repeat would render inside the same
		// paragraph and still satisfy a `getByText`.
		const inlinePhrases = canvasElement.textContent?.match(/As an inline note on the work/g) ?? [];
		await expect(inlinePhrases).toHaveLength(1);
	},
};

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
