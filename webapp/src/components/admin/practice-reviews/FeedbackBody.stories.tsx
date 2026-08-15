import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect } from "storybook/test";
import { FeedbackBody } from "./FeedbackBody";

const body =
	"## What worked\n\nThe controller stays focused on HTTP concerns.\n\n[Read the guide](https://example.com/guide).";

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
 * The ordinary case wears no badge: text that reached the developer needs no marking, and the page
 * this card sits on says so once already.
 */
export const Delivered: Story = {
	play: async ({ canvas }) => {
		canvas.getByRole("heading", { level: 4, name: "What worked" });
		await expect(canvas.queryByText("Delivered")).not.toBeInTheDocument();
	},
};

export const SwitchToSource: Story = {
	play: async ({ canvas, canvasElement, userEvent }) => {
		// The group is `role="group"` with no `aria-orientation`: the vendored wrapper drops the
		// attribute the Base UI primitive would otherwise put on a role ARIA does not allow it on.
		const views = canvas.getByRole("group", { name: "How to show the feedback" });
		await expect(views).not.toHaveAttribute("aria-orientation");
		await expect(canvas.getByRole("button", { name: "Rendered" })).toHaveAttribute(
			"aria-pressed",
			"true",
		);

		await userEvent.click(canvas.getByRole("button", { name: "Source" }));
		await expect(canvasElement.querySelector("pre")?.textContent).toContain("## What worked");
		await expect(canvas.queryByRole("heading", { level: 4 })).not.toBeInTheDocument();
		await expect(canvas.getByRole("button", { name: "Source" })).toHaveAttribute(
			"aria-pressed",
			"true",
		);

		// Pressing the held view again is refused: there is no state in which the body shows nothing.
		await userEvent.click(canvas.getByRole("button", { name: "Source" }));
		await expect(canvasElement.querySelector("pre")?.textContent).toContain("## What worked");
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

/** `PREPARED` only ever exists on the conversation lane, so the badge can name the queue it is in. */
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
