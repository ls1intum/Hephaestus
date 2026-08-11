import type { Meta, StoryObj } from "@storybook/react-vite";
import { HttpResponse, http } from "msw";
import { expect, within } from "storybook/test";
import { expectNoPageOverflow } from "@/test/reflow";
import { ReviewTargetPage } from "./ReviewTargetPage";
import { reviewArtifact, reviewFeedback, reviewFindings } from "./story-mock-data";

const page = (content: unknown[]) => ({
	content,
	page: { number: 0, size: 5, totalElements: content.length, totalPages: 1 },
});

const handlers = (feedback: unknown[] = reviewFeedback, findings: unknown[] = reviewFindings) => [
	http.get("*/workspaces/:workspaceSlug/practices/reviews/feedback", () =>
		HttpResponse.json(page(feedback)),
	),
	http.get("*/workspaces/:workspaceSlug/practices/reviews/findings", () =>
		HttpResponse.json(page(findings)),
	),
];

const meta = {
	title: "Workspace admin/Practice reviews/Reviewed work",
	component: ReviewTargetPage,
	parameters: {
		layout: "padded",
		msw: { handlers: handlers() },
		chromatic: { viewports: [320, 768, 1440] },
	},
	tags: ["autodocs"],
	args: {
		workspaceSlug: "demo",
		artifactKind: "scm.pull_request",
		artifactId: 42,
	},
} satisfies Meta<typeof ReviewTargetPage>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
	parameters: { viewport: { defaultViewport: "reflow" } },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(await canvas.findByRole("heading", { name: reviewArtifact.title })).toBeVisible();
		await expect(await canvas.findByText(reviewFeedback[0].bodyPreview)).toBeVisible();
		await expect(await canvas.findByText(reviewFindings[0].title)).toBeVisible();
		await expectNoPageOverflow();
	},
};

export const NoOutput: Story = {
	parameters: {
		chromatic: { viewports: [1440] },
		msw: { handlers: handlers([], []) },
	},
	play: async ({ canvasElement }) => {
		await expect(await within(canvasElement).findByText("No review output found")).toBeVisible();
	},
};

export const FindingsFailed: Story = {
	parameters: {
		chromatic: { viewports: [1440] },
		msw: {
			handlers: [
				http.get("*/workspaces/:workspaceSlug/practices/reviews/feedback", () =>
					HttpResponse.json(page(reviewFeedback)),
				),
				http.get(
					"*/workspaces/:workspaceSlug/practices/reviews/findings",
					() => new HttpResponse(null, { status: 500 }),
				),
			],
		},
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(await canvas.findByRole("heading", { name: reviewArtifact.title })).toBeVisible();
		await expect(await canvas.findByText("Couldn't load findings")).toBeVisible();
		await expect(await canvas.findByText(reviewFeedback[0].bodyPreview)).toBeVisible();
	},
};
