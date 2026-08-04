import type { Meta, StoryObj } from "@storybook/react-vite";
import { HttpResponse, http } from "msw";
import { expect, fn, screen, waitFor, within } from "storybook/test";
import { withStandardPage, withWidePage } from "@/stories/decorators";
import { expectNoPageOverflow } from "@/test/reflow";
import { FindingsListPage } from "./FindingsListPage";
import { reviewFindings } from "./story-mock-data";

const meta = {
	title: "Admin/Practice reviews/Findings",
	component: FindingsListPage,
	parameters: {
		layout: "fullscreen",
		chromatic: { viewports: [320, 768, 1440] },
		msw: {
			handlers: [
				http.get("*/workspaces/:workspaceSlug/practices/reviews/findings", () =>
					HttpResponse.json({
						content: reviewFindings,
						page: {
							number: 0,
							size: 25,
							totalElements: reviewFindings.length,
							totalPages: 1,
						},
					}),
				),
				http.get("*/workspaces/:workspaceSlug/practice-areas", () =>
					HttpResponse.json([
						{
							id: 1,
							slug: "code-quality",
							name: "Code quality",
							active: true,
							displayOrder: 0,
							createdAt: new Date("2026-01-01T00:00:00Z"),
						},
					]),
				),
				http.get("*/workspaces/:workspaceSlug/practices", () =>
					HttpResponse.json([
						{
							id: 1,
							slug: "thin-controllers",
							name: "Thin controllers",
							areaSlug: "code-quality",
							active: true,
							artifactType: "PULL_REQUEST",
							criteria: "Keep controllers focused on transport concerns.",
							displayOrder: 0,
							triggerEvents: [],
							createdAt: new Date("2026-01-01T00:00:00Z"),
						},
					]),
				),
			],
		},
	},
	decorators: [withWidePage, withStandardPage],
	tags: ["autodocs"],
	args: {
		workspaceSlug: "demo",
		search: { presence: undefined, assessment: undefined, severity: undefined },
		onSearchChange: fn(),
	},
} satisfies Meta<typeof FindingsListPage>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(await canvas.findByText(`${reviewFindings.length} findings.`)).toBeVisible();
		for (const name of ["Area", "Practice", "Result"]) {
			await expect(canvas.getByRole("combobox", { name })).toBeVisible();
		}
		await expect(canvas.getByRole("button", { name: "Date" })).toBeVisible();
	},
};

export const MoreFiltersOpen: Story = {
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvasElement, userEvent }) => {
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getByRole("button", { name: /More filters/ }));
		const dialog = await screen.findByRole("dialog");
		const practiceStatus = await within(dialog).findByRole("combobox", {
			name: "Practice status",
		});
		await waitFor(() => expect(practiceStatus).toBeVisible());
	},
};

export const Mobile: Story = {
	parameters: {
		chromatic: { disableSnapshot: true },
		viewport: { defaultViewport: "reflow" },
	},
	play: async ({ canvasElement }) => {
		await expect(
			await within(canvasElement).findByText(`${reviewFindings.length} findings.`),
		).toBeVisible();
		await expectNoPageOverflow();
	},
};
