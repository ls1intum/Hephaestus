import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, within } from "storybook/test";
import { FeedbackMessage } from "./FeedbackMessage";

const body =
	"## What worked\n\nThe controller stays focused on HTTP concerns.\n\n[Read the guide](https://example.com/guide).";

const meta = {
	title: "Admin/Practice reviews/Building blocks/Message preview",
	component: FeedbackMessage,
	parameters: { layout: "padded", chromatic: { viewports: [320, 768] } },
	tags: ["autodocs"],
	args: { body, deliveryState: "DELIVERED" },
} satisfies Meta<typeof FeedbackMessage>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Delivered: Story = {
	play: async ({ canvasElement }) => {
		const preview = within(canvasElement);
		await expect(preview.getByRole("heading", { level: 4, name: "What worked" })).toBeVisible();
	},
};

export const NotDelivered: Story = {
	args: { deliveryState: "SUPPRESSED", suppressionReason: "ARTIFACT_MERGED" },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText("Not delivered")).toBeVisible();
		await expect(canvas.getByText(/reviewed work was already merged/i)).toBeVisible();
	},
};

export const Failed: Story = { args: { deliveryState: "FAILED" } };

export const WaitingForConversation: Story = { args: { deliveryState: "PREPARED" } };

export const Replaced: Story = { args: { deliveryState: "SUPERSEDED" } };

export const NoComposedMessage: Story = {
	args: { body: undefined, deliveryState: "SUPPRESSED", suppressionReason: "REACTED_DISPUTED" },
	play: async ({ canvasElement }) => {
		await expect(
			within(canvasElement).getByText("No composed message is available for this record."),
		).toBeVisible();
	},
};

export const UntrustedMarkdown: Story = {
	args: {
		body: [
			"![tracking pixel](https://attacker.example/pixel.png)",
			"[unsafe link](javascript:alert(1))",
			"<img src=x onerror=alert(1)>",
			"[safe link](https://example.com/docs)",
		].join("\n\n"),
		deliveryState: "SUPPRESSED",
		suppressionReason: "VOLUME_CAPPED",
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvasElement.querySelector("img")).toBeNull();
		await expect(canvas.getByText("unsafe link")).toBeVisible();
		await expect(canvas.queryByRole("link", { name: "unsafe link" })).not.toBeInTheDocument();
		await expect(canvas.getByRole("link", { name: "safe link" })).toHaveAttribute(
			"href",
			"https://example.com/docs",
		);
	},
};
