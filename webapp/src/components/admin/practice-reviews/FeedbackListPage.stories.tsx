import type { Meta, StoryObj } from "@storybook/react-vite";
import { HttpResponse, http } from "msw";
import { expect, fn, within } from "storybook/test";
import { withStandardPage, withWidePage } from "@/stories/decorators";
import { expectNoPageOverflow } from "@/test/reflow";
import { FeedbackListPage } from "./FeedbackListPage";
import { reviewFeedback } from "./story-mock-data";

const meta = {
	title: "Admin/Practice reviews/Delivery",
	component: FeedbackListPage,
	parameters: {
		layout: "fullscreen",
		chromatic: { viewports: [320, 768, 1440] },
		msw: {
			handlers: [
				http.get("*/workspaces/:workspaceSlug/practices/reviews/feedback", () =>
					HttpResponse.json({
						content: reviewFeedback,
						page: {
							number: 0,
							size: 25,
							totalElements: reviewFeedback.length,
							totalPages: 1,
						},
					}),
				),
			],
		},
	},
	decorators: [withWidePage, withStandardPage],
	tags: ["autodocs"],
	args: {
		workspaceSlug: "demo",
		search: { deliveryState: undefined, suppressionReason: undefined, channel: undefined },
		onSearchChange: fn(),
	},
} satisfies Meta<typeof FeedbackListPage>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(await canvas.findByText(`${reviewFeedback.length} messages.`)).toBeVisible();
		await expect(canvas.getByRole("combobox", { name: "Outcome" })).toBeVisible();
		await expect(canvas.getByRole("button", { name: "Date" })).toBeVisible();
	},
};

export const Mobile: Story = {
	parameters: {
		chromatic: { disableSnapshot: true },
		viewport: { defaultViewport: "reflow" },
	},
	play: async ({ canvasElement }) => {
		await expect(
			await within(canvasElement).findByText(`${reviewFeedback.length} messages.`),
		).toBeVisible();
		await expectNoPageOverflow();
	},
};
