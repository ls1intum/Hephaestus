import type { Meta, StoryObj } from "@storybook/react-vite";
import { HttpResponse, http } from "msw";
import { expect, within } from "storybook/test";
import { expectNoPageOverflow } from "@/test/reflow";
import { FindingDetailPage } from "./FindingDetailPage";
import { reviewFindingDetail } from "./story-mock-data";

const meta = {
	title: "Workspace admin/Practice reviews/Finding details",
	component: FindingDetailPage,
	parameters: {
		layout: "padded",
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320, 768, 1440] },
		msw: {
			handlers: [
				http.get("*/workspaces/:workspaceSlug/practices/reviews/observations/:observationId", () =>
					HttpResponse.json(reviewFindingDetail),
				),
			],
		},
	},
	tags: ["autodocs"],
	args: {
		workspaceSlug: "demo",
		findingId: reviewFindingDetail.id,
		search: {
			agentJobId: reviewFindingDetail.agentJobId,
			presence: undefined,
			assessment: undefined,
			severity: undefined,
		},
	},
} satisfies Meta<typeof FindingDetailPage>;

export default meta;
type Story = StoryObj<typeof meta>;

export const EvidenceAndLinkedFeedback: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(
			await canvas.findByRole("heading", { name: reviewFindingDetail.title }),
		).toBeVisible();
		await expect(canvas.getByText(/\$artifactId\.tsx:1/)).toBeVisible();
		await expect(canvas.getByRole("link", { name: /On the work/ })).toBeVisible();
		await expectNoPageOverflow();
	},
};
