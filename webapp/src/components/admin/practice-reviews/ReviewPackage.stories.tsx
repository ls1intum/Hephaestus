import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect } from "storybook/test";
import { expectNoPageOverflow } from "@/stories/reflow";
import { PRIVATE_FEEDBACK_TEXT, reviewFeedbackDetail } from "./fixtures";
import { ReviewPackage } from "./ReviewPackage";

const longPath =
	"server/src/main/java/de/tum/cit/aet/hephaestus/integration/provider/gitlab/WorkspaceMembershipReconciliationService.java";
const feedback = {
	...reviewFeedbackDetail,
	proposedPlacements: [
		{
			type: "SUMMARY" as const,
			body: "### Keep the retry boundary explicit\n\nThe review found one error-handling change to make.",
		},
		{
			type: "INLINE" as const,
			body: "Catch only the provider failure that is safe to retry; programming errors must still surface.",
			path: longPath,
			startLine: 148,
			endLine: 152,
		},
	],
};

const meta = {
	component: ReviewPackage,
	parameters: {
		layout: "padded",
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320, 1440] },
	},
	tags: ["autodocs"],
	args: { feedback, defaultExpanded: true },
	decorators: [
		(Story) => (
			<div className="mx-auto w-full max-w-4xl">
				<Story />
			</div>
		),
	],
} satisfies Meta<typeof ReviewPackage>;

export default meta;
type Story = StoryObj<typeof meta>;

export const ExactSummaryAndInlinePackage: Story = {
	globals: { theme: "dark" },
	play: async ({ canvas }) => {
		await expect(
			canvas.getByRole("heading", { name: "Keep the retry boundary explicit" }),
		).toBeVisible();
		await expect(canvas.getByText(longPath)).toBeVisible();
		await expect(
			canvas.getByText(
				"Catch only the provider failure that is safe to retry; programming errors must still surface.",
			),
		).toBeVisible();
		await expectNoPageOverflow();
	},
};

/**
 * A card on the developer's own practice pages is theirs alone: handed placements anyway, the
 * package says the text is withheld and shows none of it, not even the file it is about.
 */
export const PrivatePlaceNeverShown: Story = {
	args: {
		feedback: {
			...feedback,
			channel: "IN_APP",
			location: "PRACTICE_PAGES",
			proposedPlacements: [
				{ type: "SUMMARY" as const, body: PRIVATE_FEEDBACK_TEXT },
				{
					type: "INLINE" as const,
					body: PRIVATE_FEEDBACK_TEXT,
					path: longPath,
					startLine: 148,
				},
			],
		},
	},
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas }) => {
		await expect(
			canvas.getByText(/written for the developer’s own practice pages and is withheld/u),
		).toBeVisible();
		await expect(canvas.queryByText(PRIVATE_FEEDBACK_TEXT)).toBeNull();
		await expect(canvas.queryByText(longPath)).toBeNull();
	},
};
