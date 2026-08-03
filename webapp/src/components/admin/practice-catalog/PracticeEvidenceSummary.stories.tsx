import type { Meta, StoryObj } from "@storybook/react-vite";
import { mockPullRequestEvidence } from "@/mocks/fixtures/practice";
import { PracticeEvidenceSummary } from "./PracticeEvidenceSummary";

const meta = {
	title: "Workspace admin/Practices/Evidence summary",
	component: PracticeEvidenceSummary,
	args: {
		declaration: mockPullRequestEvidence,
		validation: {
			status: "STALE",
			sourceContractVersion: "1.0.0",
			declarationDigest: "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
			validatedAt: new Date("2026-01-15T10:00:00Z"),
			validator: "Independent observability review",
			validationReference: "review-1437",
		},
	},
} satisfies Meta<typeof PracticeEvidenceSummary>;

export default meta;
type Story = StoryObj<typeof meta>;

export const StaleIndependentValidation: Story = {};
