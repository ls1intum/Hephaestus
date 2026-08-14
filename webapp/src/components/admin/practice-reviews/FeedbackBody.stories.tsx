import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect } from "storybook/test";
import { FeedbackBody } from "./FeedbackBody";

const body =
	"## What worked\n\nThe controller stays focused on HTTP concerns.\n\n[Read the guide](https://example.com/guide).";

/**
 * The composed feedback, as the developer would read it or as it was actually written.
 *
 * The card used to carry its own five-case copy table for the delivery states, which is how it came
 * to say "Ready for a future conversation with Heph. It has not been delivered." — a sentence about
 * a queue, printed on a card about words. The narrative now lives in `DeliveryTrace`; the badge here
 * comes from the same registry every other status on these screens reads.
 */
const meta = {
	title: "Workspace admin/Practice reviews/Building blocks/Feedback preview",
	component: FeedbackBody,
	parameters: { layout: "padded", chromatic: { viewports: [320, 768] } },
	tags: ["autodocs"],
	args: {
		feedback: { body, channel: "IN_CONTEXT", deliveryState: "DELIVERED" },
	},
} satisfies Meta<typeof FeedbackBody>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * The ordinary case wears no badge at all. Text that reached the developer needs no marking, and the
 * Delivery section of the page it sits on already says so — badging it here put "Delivered" on the
 * screen twice.
 */
export const Delivered: Story = {
	play: async ({ canvas }) => {
		canvas.getByRole("heading", { level: 4, name: "What worked" });
		await expect(canvas.queryByText("Delivered")).not.toBeInTheDocument();
	},
};

/**
 * The Markdown behind the rendering, on a switch in the card's own header.
 *
 * Tailwind Typography's default rhythm is sized for an article; in a card of a few hundred words its
 * `mt-8` above every heading left the gap the product owner measured as "almost doubling what you
 * would expect". The prose modifiers on this card tighten it to the length of the thing.
 */
export const SwitchToSource: Story = {
	play: async ({ canvas, canvasElement, userEvent }) => {
		await userEvent.click(
			canvas.getByRole("button", { name: /Show the Markdown that was composed/ }),
		);
		await expect(canvasElement.querySelector("pre")?.textContent).toContain("## What worked");
		await expect(canvas.queryByRole("heading", { level: 4 })).not.toBeInTheDocument();
	},
};

export const Withheld: Story = {
	args: {
		feedback: {
			body,
			channel: "IN_CONTEXT",
			deliveryState: "SUPPRESSED",
			suppressionReason: "ARTIFACT_MERGED",
		},
	},
	play: async ({ canvas }) => {
		canvas.getByText("Withheld");
	},
};

export const FailedToDeliver: Story = {
	args: { feedback: { body, channel: "IN_CONTEXT", deliveryState: "FAILED" } },
};

/**
 * The one state the word "prepared" used to name. `PREPARED` only ever exists on the conversation
 * lane, so the badge names the queue it is in and what empties it.
 */
export const QueuedForConversation: Story = {
	args: { feedback: { body, channel: "CONVERSATION", deliveryState: "PREPARED" } },
	play: async ({ canvas }) => {
		canvas.getByText("Queued for conversation");
	},
};

export const ReplacedByNewer: Story = {
	args: { feedback: { body, channel: "IN_CONTEXT", deliveryState: "SUPERSEDED" } },
};

export const NoComposedText: Story = {
	args: {
		feedback: {
			channel: "IN_CONTEXT",
			deliveryState: "SUPPRESSED",
			suppressionReason: "REACTED_DISPUTED",
		},
	},
	play: async ({ canvas }) => {
		canvas.getByText("No feedback text was composed for this record.");
	},
};

/**
 * A composed body is model output that quotes a developer's own text, so it is rendered as untrusted
 * Markdown: images are dropped entirely and only `http(s)` links stay links.
 */
export const UntrustedMarkdown: Story = {
	args: {
		feedback: {
			body: [
				"![tracking pixel](https://attacker.example/pixel.png)",
				"[unsafe link](javascript:alert(1))",
				"<img src=x onerror=alert(1)>",
				"[safe link](https://example.com/docs)",
			].join("\n\n"),
			channel: "IN_CONTEXT",
			deliveryState: "SUPPRESSED",
			suppressionReason: "VOLUME_CAPPED",
		},
	},
	play: async ({ canvas, canvasElement }) => {
		await expect(canvasElement.querySelector("img")).toBeNull();
		canvas.getByText("unsafe link");
		await expect(canvas.queryByRole("link", { name: "unsafe link" })).not.toBeInTheDocument();
		await expect(canvas.getByRole("link", { name: "safe link" })).toHaveAttribute(
			"href",
			"https://example.com/docs",
		);
	},
};
