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
			status: "AUTHOR_DECLARED",
			sourceContractVersion: "1.0.0",
			policyDigest: "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
			reviewRuleFingerprint: `v2:${"0".repeat(64)}`,
		},
	},
	parameters: { layout: "padded" },
	tags: ["autodocs"],
} satisfies Meta<typeof PracticeEvidenceSummary>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Every practice that ships is its author's declaration, and the badge says so rather than leaving a
 * reader to assume somebody checked.
 *
 * The two digests stay beside it: they are what an author compares when a review claim is disputed,
 * and they name the exact policy and rules the declaration is about.
 */
export const AuthorDeclared: Story = {
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Not independently validated")).toBeVisible();
		await expect(canvas.getByText(/^Rules/)).toHaveTextContent(
			"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
		);
	},
};

export const OneOccasion: Story = {
	args: { bindings: [mockPullRequestBinding] },
};

export const NoOccasion: Story = {
	args: { bindings: [] },
};
