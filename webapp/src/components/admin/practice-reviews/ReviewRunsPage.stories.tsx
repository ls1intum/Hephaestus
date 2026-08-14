import type { Meta, StoryObj } from "@storybook/react-vite";
import { HttpResponse, http } from "msw";
import { expect, fn, within } from "storybook/test";
import type { ReviewRunSummary } from "@/api/types.gen";
import { withStandardPage, withWidePage } from "@/stories/decorators";
import { expectNoPageOverflow } from "@/test/reflow";
import { ReviewRunsPage } from "./ReviewRunsPage";
import { reviewArtifact } from "./story-mock-data";

const reviews: ReviewRunSummary[] = [
	{
		id: "11111111-1111-1111-1111-111111111111",
		status: "COMPLETED",
		target: reviewArtifact,
		createdAt: new Date("2026-07-28T13:42:00Z"),
		observations: { strengths: 2, problems: 1, notApplicable: 1, inconclusive: 1 },
		feedback: { delivered: 1, failed: 0, prepared: 0, superseded: 0, suppressed: 1 },
	},
	{
		id: "22222222-2222-2222-2222-222222222222",
		status: "RUNNING",
		target: {
			id: 43,
			type: "scm.pull_request",
			provider: "GITLAB",
			number: 17,
			repositoryName: "team/service",
			title: "Keep the controller thin",
		},
		createdAt: new Date("2026-07-28T12:10:00Z"),
		observations: { strengths: 0, problems: 0, notApplicable: 0, inconclusive: 0 },
		feedback: { delivered: 0, failed: 0, prepared: 0, superseded: 0, suppressed: 0 },
	},
];

const reviewsHandler = (content: ReviewRunSummary[] = reviews) =>
	http.get("*/workspaces/:workspaceSlug/practices/reviews", () =>
		HttpResponse.json({
			content,
			page: { number: 0, size: 20, totalElements: content.length, totalPages: 1 },
		}),
	);

const meta = {
	title: "Workspace admin/Practice reviews/Reviews",
	component: ReviewRunsPage,
	parameters: {
		layout: "fullscreen",
		msw: { handlers: [reviewsHandler()] },
		chromatic: { viewports: [320, 768, 1440] },
	},
	decorators: [withWidePage, withStandardPage],
	tags: ["autodocs"],
	args: {
		workspaceSlug: "demo",
		search: {},
		onSearchChange: fn(),
	},
} satisfies Meta<typeof ReviewRunsPage>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
	parameters: {
		chromatic: { viewports: [1440] },
		viewport: { defaultViewport: "desktop" },
	},
};

export const Mobile: Story = {
	parameters: {
		chromatic: { viewports: [320, 768] },
		viewport: { defaultViewport: "reflow" },
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		const list = await canvas.findByRole("list");
		const review = await within(list).findByRole("link", {
			name: /ls1intum\/Hephaestus.*PR #1423/i,
		});
		await expect(review).toBeVisible();
		await expectNoPageOverflow();
	},
};

export const Empty: Story = {
	parameters: {
		chromatic: { viewports: [1440] },
		msw: { handlers: [reviewsHandler([])] },
	},
	play: async ({ canvasElement }) => {
		await expect(await within(canvasElement).findByText("No reviews found")).toBeVisible();
	},
};

export const LoadFailed: Story = {
	parameters: {
		chromatic: { viewports: [1440] },
		msw: {
			handlers: [
				http.get(
					"*/workspaces/:workspaceSlug/practices/reviews",
					() => new HttpResponse(null, { status: 500 }),
				),
			],
		},
	},
	play: async ({ canvasElement }) => {
		await expect(await within(canvasElement).findByText("Couldn't load reviews")).toBeVisible();
	},
};
