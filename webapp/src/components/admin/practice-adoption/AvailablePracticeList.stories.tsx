import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect } from "storybook/test";
import type { CatalogPracticeSummary } from "@/api/types.gen";
import { mockAuthorDeclaredEvidenceValidation } from "@/mocks/fixtures/practice";
import { AvailablePracticeList } from "./AvailablePracticeList";

// The `detail` param a row would push. Read back off the href rather than compared as a whole URL,
// because a stack link preserves whatever search the surrounding route already had — in Storybook
// that is the preview iframe's own `sessionId`.
const detailParamOf = (link: HTMLElement) =>
	new URL(link.getAttribute("href") ?? "", "https://example.test").searchParams.get("detail");

const practices: CatalogPracticeSummary[] = [
	{
		slug: "describe-what-and-why",
		name: "Describe what changed and why",
		artifactKind: "scm.pull_request",
		areaSlug: "review-ready-work",
		areaName: "Review-ready work",
		availability: "AVAILABLE",
		automatedReviewValidation: mockAuthorDeclaredEvidenceValidation,
	},
	{
		slug: "review-scope",
		name: "Keep pull requests focused",
		artifactKind: "scm.pull_request",
		areaSlug: "review-ready-work",
		areaName: "Review-ready work",
		availability: "ADOPTED",
		automatedReviewValidation: mockAuthorDeclaredEvidenceValidation,
	},
	{
		slug: "issue-context",
		name: "Include enough issue context",
		artifactKind: "scm.issue",
		availability: "SLUG_CONFLICT",
		automatedReviewValidation: mockAuthorDeclaredEvidenceValidation,
	},
];

/**
 * Grouping is not optional and adopted entries are not shown twice, so the list has no display
 * flags: the one caller would have typed both as literals, which is the shape a flag argument takes
 * just before it stops being one.
 */
const meta = {
	title: "Workspace admin/Practice adoption/Available practices",
	component: AvailablePracticeList,
	parameters: { layout: "padded" },
	args: { practices, existingAreaSlugs: new Set(["review-ready-work"]) },
	argTypes: { existingAreaSlugs: { control: false } },
	tags: ["autodocs"],
} satisfies Meta<typeof AvailablePracticeList>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
	play: async ({ canvas }) => {
		// The ordinary case carries no chip, so the two exceptions are the only colour in the list.
		await expect(canvas.queryByText("Available")).not.toBeInTheDocument();
		await expect(canvas.getByText("Name unavailable")).toBeVisible();
		// An adopted practice inside an area the workspace still has is not offered again.
		await expect(canvas.queryByText("Keep pull requests focused")).not.toBeInTheDocument();
		// An adoptable row stays on the route and only pushes a `detail` level, which is what makes
		// it open a drawer instead of replacing the page.
		await expect(
			detailParamOf(canvas.getByRole("link", { name: /Describe what changed and why/ })),
		).toBe('["catalog-practice:describe-what-and-why"]');
		await expect(detailParamOf(canvas.getByRole("link", { name: /Review 1 practice/ }))).toBe(
			'["catalog-area:review-ready-work"]',
		);
	},
};

export const DeletedAreaStillHasSomethingToAdd: Story = {
	args: { existingAreaSlugs: new Set() },
	play: async ({ canvas }) => {
		// The area is gone, so its adopted practice is listed again — but one entry is still available,
		// so the area is offered as a review rather than a pure restore.
		await expect(canvas.getByRole("link", { name: /Review area · 1 practice/ })).toBeVisible();
		// An added practice opens the workspace copy as a drawer level too, so nothing in the library
		// navigates away from the library.
		await expect(
			detailParamOf(canvas.getByRole("link", { name: /Keep pull requests focused/ })),
		).toBe('["practice:review-scope"]');
	},
};

export const DeletedAreaCanOnlyBeRestored: Story = {
	args: { practices: [practices[1]], existingAreaSlugs: new Set() },
	play: async ({ canvas }) => {
		// Nothing left to add, so putting the area back is the only thing on offer.
		await expect(canvas.getByRole("link", { name: /Restore area · 1 practice/ })).toBeVisible();
	},
};

export const EverythingAdded: Story = {
	args: { practices: [practices[1]] },
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Everything is already added")).toBeVisible();
	},
};

export const NothingOffered: Story = {
	args: { practices: [] },
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Nothing to add")).toBeVisible();
	},
};

export const LongContent: Story = {
	args: {
		practices: [
			{
				...practices[0],
				name: "Explain architectural trade-offs, operational constraints, and the evidence behind the chosen implementation",
				areaName: "Decisions, documentation, and long-lived operational knowledge",
			},
		],
	},
};

export const NarrowViewport: Story = {
	parameters: { viewport: { defaultViewport: "reflow" }, chromatic: { viewports: [320] } },
};

export const DarkMode: Story = {
	globals: { theme: "dark" },
};
