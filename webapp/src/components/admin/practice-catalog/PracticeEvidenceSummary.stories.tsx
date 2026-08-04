import type { Meta, StoryObj } from "@storybook/react-vite";
import { mockPracticeEvidenceOptions, mockPullRequestEvidence } from "@/mocks/fixtures/practice";
import { PracticeEvidenceSummary } from "./PracticeEvidenceSummary";

const meta = {
	title: "Workspace admin/Practices/Evidence summary",
	component: PracticeEvidenceSummary,
	args: {
		policy: mockPullRequestEvidence,
		sources: mockPracticeEvidenceOptions.workTypes[0].allowedSources,
		workTypeLabel: "Pull or merge request",
		validation: {
			status: "STALE",
			sourceContractVersion: "1.0.0",
			policyDigest: "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
			reviewRuleFingerprint: `v2:${"0".repeat(64)}`,
			evaluatorProcedureFingerprint: `v1:${"1".repeat(64)}`,
			validatedAt: new Date("2026-01-15T10:00:00Z"),
			validator: "Independent automated assessment review",
			validationReference: "review-1437",
		},
	},
} satisfies Meta<typeof PracticeEvidenceSummary>;

export default meta;
type Story = StoryObj<typeof meta>;

export const StaleIndependentValidation: Story = {};
