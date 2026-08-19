import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect } from "storybook/test";
import type { CatalogPracticeSummary } from "@/api/types.gen";
import { mockAuthorDeclaredEvidenceValidation } from "@/mocks/fixtures/practice";
import { AvailablePracticeList } from "./AvailablePracticeList";

const practices: CatalogPracticeSummary[] = [
	{
		slug: "describe-what-and-why",
		name: "Describe what changed and why",
		artifactKind: "scm.pull_request",
		areaSlug: "review-ready-work",
		availability: "AVAILABLE" as const,
		automatedReviewValidation: mockAuthorDeclaredEvidenceValidation,
	},
	{
		slug: "review-scope",
		name: "Keep pull requests focused",
		artifactKind: "scm.pull_request",
		areaSlug: "review-ready-work",
		availability: "ADOPTED" as const,
		automatedReviewValidation: mockAuthorDeclaredEvidenceValidation,
	},
	{
		slug: "issue-context",
		name: "Include enough issue context",
		artifactKind: "scm.issue",
		availability: "SLUG_CONFLICT" as const,
		automatedReviewValidation: mockAuthorDeclaredEvidenceValidation,
	},
];

const meta = {
	title: "Workspace admin/Practice adoption/Available practices",
	component: AvailablePracticeList,
	parameters: { layout: "padded", chromatic: { viewports: [320, 1440] } },
	args: { workspaceSlug: "demo", practices },
	tags: ["autodocs"],
} satisfies Meta<typeof AvailablePracticeList>;

export default meta;
type Story = StoryObj<typeof meta>;

export const AllAvailabilityStates: Story = {
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Available")).toBeVisible();
		await expect(canvas.getByText("Adopted")).toBeVisible();
		await expect(canvas.getByText("Slug conflict")).toBeVisible();
	},
};

export const Empty: Story = {
	args: { practices: [] },
};
