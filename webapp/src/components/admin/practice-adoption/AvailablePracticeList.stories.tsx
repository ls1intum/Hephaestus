import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn } from "storybook/test";
import type { CatalogPracticeSummary } from "@/api/types.gen";
import { mockAuthorDeclaredEvidenceValidation } from "@/mocks/fixtures/practice";
import { AvailablePracticeList } from "./AvailablePracticeList";

const practices: CatalogPracticeSummary[] = [
	{
		slug: "describe-what-and-why",
		name: "Describe what changed and why",
		artifactKind: "scm.pull_request",
		areaSlug: "review-ready-work",
		areaName: "Review-ready work",
		availability: "AVAILABLE" as const,
		automatedReviewValidation: mockAuthorDeclaredEvidenceValidation,
	},
	{
		slug: "review-scope",
		name: "Keep pull requests focused",
		artifactKind: "scm.pull_request",
		areaSlug: "review-ready-work",
		areaName: "Review-ready work",
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

export const CatalogStates: Story = {
	play: async ({ canvas }) => {
		await expect(canvas.queryByText("Available")).not.toBeInTheDocument();
		await expect(canvas.getByText("Added")).toBeVisible();
		await expect(canvas.getByText("Name unavailable")).toBeVisible();
		await expect(
			canvas.getByRole("link", {
				name: "Describe what changed and why, review for adoption",
			}),
		).toHaveAttribute("href", "/w/demo/admin/practices/available/describe-what-and-why");
		await expect(
			canvas.getByRole("link", {
				name: "Keep pull requests focused, open workspace practice, added",
			}),
		).toHaveAttribute("href", "/w/demo/admin/practices/review-scope");
	},
};

export const Empty: Story = {
	args: { practices: [] },
};

export const GroupedLibrary: Story = {
	args: {
		groupByArea: true,
		hideAdopted: true,
		onReviewArea: fn(),
		existingAreaSlugs: new Set(["review-ready-work"]),
	},
	play: async ({ args, canvas }) => {
		await expect(canvas.getByRole("heading", { name: "Review-ready work" })).toBeVisible();
		await expect(canvas.queryByText("Added")).not.toBeInTheDocument();
		await canvas.getByRole("button", { name: "Review 1 practice" }).click();
		await expect(args.onReviewArea).toHaveBeenCalledWith("review-ready-work");
		await expect(canvas.queryByRole("button", { name: /0 practices/ })).not.toBeInTheDocument();
	},
};

export const EverythingAdded: Story = {
	args: {
		practices: [practices[1]],
		groupByArea: true,
		hideAdopted: true,
		existingAreaSlugs: new Set(["review-ready-work"]),
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Everything is already added")).toBeVisible();
	},
};

export const RestoreDeletedArea: Story = {
	args: {
		practices: [practices[1]],
		groupByArea: true,
		hideAdopted: true,
		existingAreaSlugs: new Set(),
		onReviewArea: fn(),
	},
	play: async ({ args, canvas }) => {
		await canvas.getByRole("button", { name: "Restore area · 1 practice" }).click();
		await expect(args.onReviewArea).toHaveBeenCalledWith("review-ready-work");
	},
};

export const LongContentInDarkMode: Story = {
	args: {
		practices: [
			{
				...practices[0],
				name: "Explain architectural trade-offs, operational constraints, and the evidence behind the chosen implementation",
				areaName: "Decisions, documentation, and long-lived operational knowledge",
			},
		],
	},
	globals: { theme: "dark" },
};
