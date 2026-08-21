import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect } from "storybook/test";
import type { CatalogPracticeSummary } from "@/api/types.gen";
import { mockAuthorDeclaredEvidenceValidation } from "@/mocks/fixtures/practice";
import { AvailablePracticeList } from "./AvailablePracticeList";

/**
 * The `detail` param a row would push. Read back off the href rather than compared as a whole URL,
 * because a stack link preserves whatever search the surrounding route already had — in Storybook
 * that is the preview iframe's own `sessionId`.
 */
const detailParamOf = (link: HTMLElement) =>
	new URL(link.getAttribute("href") ?? "", "https://example.test").searchParams.get("detail");

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
		// An adoptable row stays on the current route and only pushes a `detail` level, which is what
		// makes it open a drawer instead of replacing the page.
		await expect(
			detailParamOf(
				canvas.getByRole("link", { name: "Describe what changed and why, review for adoption" }),
			),
		).toBe('["practice:describe-what-and-why"]');
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
		existingAreaSlugs: new Set(["review-ready-work"]),
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("heading", { name: "Review-ready work" })).toBeVisible();
		await expect(canvas.queryByText("Added")).not.toBeInTheDocument();
		await expect(detailParamOf(canvas.getByRole("link", { name: "Review 1 practice" }))).toBe(
			'["area:review-ready-work"]',
		);
		await expect(canvas.queryByRole("link", { name: /0 practices/ })).not.toBeInTheDocument();
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
	},
	play: async ({ canvas }) => {
		// A deleted area keeps its adopted practices visible so it can be offered back.
		await expect(canvas.getByRole("link", { name: "Restore area · 1 practice" })).toBeVisible();
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
