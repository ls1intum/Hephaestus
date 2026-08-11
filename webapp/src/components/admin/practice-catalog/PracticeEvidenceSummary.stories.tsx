import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect } from "storybook/test";
import {
	mockMergeBinding,
	mockPracticeDefinitionOptions,
	mockPullRequestBinding,
	mockPullRequestPolicy,
} from "@/mocks/fixtures/practice";
import { PracticeEvidenceSummary } from "./PracticeEvidenceSummary";

const meta = {
	title: "Shared/Practice catalog/Evidence summary",
	component: PracticeEvidenceSummary,
	args: {
		policy: mockPullRequestPolicy,
		bindings: [mockPullRequestBinding, mockMergeBinding],
		sources: mockPracticeDefinitionOptions.workTypes[0].allowedSources,
		signals: mockPracticeDefinitionOptions.workTypes[0].signals,
		workTypeLabel: "Pull or merge request",
		validation: {
			status: "STALE",
			sourceContractVersion: "1.0.0",
			policyDigest: "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
			reviewRuleFingerprint: `v2:${"0".repeat(64)}`,
			evaluatorProcedureFingerprint: `v1:${"1".repeat(64)}`,
			validatedAt: new Date("2026-01-15T10:00:00Z"),
			validator: "Independent AI mentoring review",
			validationReference: "review-1437",
		},
	},
	parameters: { layout: "padded" },
	tags: ["autodocs"],
} satisfies Meta<typeof PracticeEvidenceSummary>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * A validation that was independent and no longer answers for what ships.
 *
 * Stale is the state an author is least likely to look for and most needs told: the badge said
 * somebody checked this, and the fingerprints it was checked against have since moved. It is warned
 * about rather than merely labelled, and the reference to the review that granted it stays, because
 * that is what an author has to go and repeat.
 */
export const StaleIndependentValidation: Story = {
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Validation is stale")).toBeVisible();
		await expect(canvas.getByText(/Independent AI mentoring review/)).toHaveTextContent(
			"review-1437",
		);
		await expect(canvas.getByText(/Validated for source contract 1\.0\.0/)).toBeVisible();
	},
};

export const OneOccasion: Story = {
	args: { bindings: [mockPullRequestBinding] },
};

export const NoOccasion: Story = {
	args: { bindings: [] },
};
