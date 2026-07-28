import type { Meta, StoryObj } from "@storybook/react-vite";
import { HttpResponse, http } from "msw";
import { expect, fn, within } from "storybook/test";
import type { ReviewRunSummary } from "@/api/types.gen";
import { withStandardPage, withWidePage } from "@/stories/decorators";
import { expectNoPageOverflow } from "@/test/reflow";
import { ReviewRunsPage } from "./ReviewRunsPage";

const reviews: ReviewRunSummary[] = [
	{
		id: "11111111-1111-1111-1111-111111111111",
		status: "COMPLETED",
		target: {
			id: 42,
			type: "PULL_REQUEST",
			provider: "GITHUB",
			number: 1420,
			repositoryName: "hephaestustest/obsphera/obsphera-replay",
			title:
				"Make practice review output visible even when the repository and reviewed work names are unusually long",
			url: "https://github.com/ls1intum/Hephaestus/pull/1423",
		},
		createdAt: new Date("2026-07-28T13:42:00Z"),
		findings: { strengths: 2, problems: 1, notApplicable: 1 },
		feedback: { delivered: 1, failed: 0, prepared: 0, superseded: 0, suppressed: 1 },
	},
	{
		id: "22222222-2222-2222-2222-222222222222",
		status: "RUNNING",
		target: {
			id: 43,
			type: "PULL_REQUEST",
			provider: "GITLAB",
			number: 17,
			repositoryName: "team/service",
			title: "Keep the controller thin",
		},
		createdAt: new Date("2026-07-28T12:10:00Z"),
		findings: { strengths: 0, problems: 0, notApplicable: 0 },
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
	title: "Admin/Practice reviews/Review activity",
	component: ReviewRunsPage,
	parameters: {
		a11y: { test: "error" },
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
	play: async ({ canvasElement }) => {
		const table = within(await within(canvasElement).findByRole("table"));
		await expect(
			table.getByText("2 strengths · 1 area to improve · 1 not applicable"),
		).toBeVisible();
		await expect(table.getByText("1 delivered · 1 not delivered")).toBeVisible();
		await expect(
			table.getByRole("row", {
				name: /Keep the controller thin.*Running.*Pending.*Pending/i,
			}),
		).toBeVisible();
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
			name: /hephaestustest\/obsphera\/obsphera-replay.*PR #1420/i,
		});
		await expect(review).toBeVisible();
		await expect(
			within(review).getByText("2 strengths · 1 area to improve · 1 not applicable"),
		).toBeVisible();
		await expect(within(review).getByText("1 delivered · 1 not delivered")).toBeVisible();
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
