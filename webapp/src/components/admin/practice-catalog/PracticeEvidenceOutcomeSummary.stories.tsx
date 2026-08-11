import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect } from "storybook/test";
import { mockPracticeDefinitionOptions } from "@/mocks/fixtures/practice";
import { PracticeEvidenceOutcomeSummary } from "./PracticeEvidenceOutcomeSummary";
import { outcome } from "./story-mock-data";

const meta = {
	title: "Workspace admin/Practices/Evidence outcomes",
	component: PracticeEvidenceOutcomeSummary,
	args: {
		sources: mockPracticeDefinitionOptions.workTypes[0].allowedSources,
		outcome: outcome({
			practiceSlug: "handles-errors-instead-of-swallowing-them",
			considered: 12,
			skipped: 8,
			blockers: [
				{ sourceKind: "scm.pull-request.diff", reasonCode: "SOURCE_EMPTY", reviewsAffected: 6 },
				{
					sourceKind: "scm.pull-request.diff",
					reasonCode: "SOURCE_INCOMPLETE",
					reviewsAffected: 2,
				},
			],
		}),
	},
	parameters: { layout: "padded" },
	tags: ["autodocs"],
} satisfies Meta<typeof PracticeEvidenceOutcomeSummary>;

export default meta;
type Story = StoryObj<typeof meta>;

export const RequirementsThatKeepSkipping: Story = {
	play: async ({ canvas }) => {
		await expect(
			canvas.getByText(/skipped this practice in 8 of the last 12 reviews/),
		).toBeVisible();
		await expect(canvas.getByText(/Code changes — was empty \(6 reviews\)/)).toBeVisible();
	},
};

/**
 * Reasons are counted per source, so a review blocked on several appears once for each. The rows can
 * therefore total more than the skipped count, and the copy above them never claims otherwise.
 */
export const ReasonsCanOutnumberTheSkips: Story = {
	args: {
		outcome: outcome({
			practiceSlug: "validates-inputs-and-edge-cases-at-the-boundary",
			considered: 5,
			skipped: 1,
			blockers: [
				{
					sourceKind: "scm.pull-request.diff",
					reasonCode: "SOURCE_INCOMPLETE",
					reviewsAffected: 1,
				},
				{
					sourceKind: "scm.pull-request.core",
					reasonCode: "SOURCE_NOT_AVAILABLE",
					reviewsAffected: 1,
				},
			],
		}),
	},
	play: async ({ canvas }) => {
		await expect(
			canvas.getByText(/skipped this practice in 1 of the last 5 reviews/),
		).toBeVisible();
		await expect(canvas.getAllByRole("listitem").map((row) => row.textContent)).toEqual([
			"Code changes — was not fully captured (1 review)",
			"Pull request details — was not available (1 review)",
		]);
	},
};

/** A practice the author turned off is skipped by its own setting, not by a failing source. */
export const SkippedByItsOwnSetting: Story = {
	args: {
		outcome: outcome({
			practiceSlug: "records-significant-decisions-with-rationale",
			considered: 9,
			skipped: 9,
			blockers: [{ reasonCode: "NO_AUTOMATED_REVIEW", reviewsAffected: 9 }],
		}),
	},
	play: async ({ canvas }) => {
		await expect(
			canvas.getByText(/this practice is not set up for automated review \(9 reviews\)/),
		).toBeVisible();
	},
};

export const RequirementsThatAlwaysHold: Story = {
	args: { outcome: outcome({ practiceSlug: "submit-reviewable-work", considered: 12 }) },
	play: async ({ canvas }) => {
		await expect(canvas.getByText(/met every time, across the last 12 reviews/)).toBeVisible();
	},
};

/** Nothing to say before the practice has been reviewed, so the panel is absent rather than empty. */
export const NeverReviewed: Story = {
	args: { outcome: outcome({ practiceSlug: "submit-reviewable-work", considered: 0 }) },
	play: async ({ canvasElement }) => {
		await expect(canvasElement.querySelector("p")).toBeNull();
	},
};
