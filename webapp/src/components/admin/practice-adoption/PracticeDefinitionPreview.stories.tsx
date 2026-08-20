import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, userEvent } from "storybook/test";
import type { CuratedPracticeDefinition } from "@/api/types.gen";
import {
	mockAuthorDeclaredEvidenceValidation,
	mockPracticeDefinitionOptions,
	mockPullRequestBinding,
	mockPullRequestPolicy,
} from "@/mocks/fixtures/practice";
import { PracticeDefinitionPreview } from "./PracticeDefinitionPreview";

const definition: CuratedPracticeDefinition = {
	name: "Describe what changed and why",
	artifactKind: "scm.pull_request",
	bindings: [mockPullRequestBinding],
	criteria: "Confirm the pull request explains both the change and its motivation.",
	automatedReviewPolicy: mockPullRequestPolicy,
	automatedReviewValidation: mockAuthorDeclaredEvidenceValidation,
	precomputeScript: "export default { hasDescription: pullRequest.body.length > 0 };",
	whyItMatters: "Reviewers need intent to assess whether the change solves the right problem.",
	whatGoodLooksLike: "A concise summary, motivation, and verification steps.",
	areaSlug: "review-ready-work",
};

const meta = {
	title: "Workspace admin/Practice adoption/Definition preview",
	component: PracticeDefinitionPreview,
	parameters: { layout: "padded", chromatic: { viewports: [320, 1440] } },
	args: { definition, options: mockPracticeDefinitionOptions },
	tags: ["autodocs"],
} satisfies Meta<typeof PracticeDefinitionPreview>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Complete: Story = {
	play: async ({ canvas }) => {
		await expect(canvas.queryByText("Pull request details")).not.toBeInTheDocument();
		await expect(canvas.queryByText(/hasDescription/)).not.toBeInTheDocument();
		await userEvent.click(canvas.getByRole("button", { name: "How reviews work" }));
		await expect(canvas.getByText("Pull request details")).toBeVisible();
		await userEvent.click(canvas.getByRole("button", { name: "Advanced: static analysis" }));
		await expect(canvas.getByText(/hasDescription/)).toBeVisible();
	},
};

export const WithoutOptionalGuidance: Story = {
	args: {
		definition: {
			...definition,
			whyItMatters: undefined,
			whatGoodLooksLike: undefined,
			precomputeScript: undefined,
		},
	},
};

export const LongContentInDarkMode: Story = {
	args: {
		definition: {
			...definition,
			criteria:
				"Confirm that the pull request explains the behavior change, the operational constraints that shaped it, the alternatives considered, and the evidence used to verify the result. Stay silent when the change is generated automatically and no meaningful author decision exists.",
			whatGoodLooksLike:
				"The description connects the implementation to the user-visible outcome, names the rejected alternatives without reproducing the entire design discussion, and gives reviewers concrete verification steps.",
		},
	},
	globals: { theme: "dark" },
};
