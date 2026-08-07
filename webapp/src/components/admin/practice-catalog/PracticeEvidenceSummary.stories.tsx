import type { Meta, StoryObj } from "@storybook/react-vite";
import {
	mockMergeBinding,
	mockPracticeDefinitionOptions,
	mockPullRequestBinding,
	mockPullRequestPolicy,
} from "@/mocks/fixtures/practice";
import { PracticeEvidenceSummary } from "./PracticeEvidenceSummary";

const meta = {
	title: "Workspace admin/Practices/Evidence summary",
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
} satisfies Meta<typeof PracticeEvidenceSummary>;

export default meta;
type Story = StoryObj<typeof meta>;

export const StaleIndependentValidation: Story = {};

/**
 * One occasion, read back as the sentence it is. Merging both occasions' evidence into a single list
 * would claim the practice always reads the review threads whole — which only the review at the merge
 * does.
 */
export const OneOccasion: Story = {
	args: { bindings: [mockPullRequestBinding] },
};

export const NoOccasion: Story = {
	args: { bindings: [] },
};
