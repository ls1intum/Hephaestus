import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, userEvent } from "storybook/test";
import type { CatalogPracticePreview } from "@/api/types.gen";
import {
	mockAuthorDeclaredEvidenceValidation,
	mockPracticeDefinitionOptions,
	mockPullRequestBinding,
	mockPullRequestPolicy,
} from "@/mocks/fixtures/practice";
import { expectGenuinelyDisabled } from "@/test/controls";
import { PracticeAdoptionReview } from "./PracticeAdoptionReview";

const preview: CatalogPracticePreview = {
	slug: "describe-what-and-why",
	availability: "AVAILABLE",
	etag: '"adoption-plan"',
	initialAutonomy: "HUMAN_APPROVAL",
	sourceReviewRuleFingerprint: mockAuthorDeclaredEvidenceValidation.reviewRuleFingerprint,
	area: {
		slug: "review-ready-work",
		disposition: "CREATE_CATALOG_AREA",
		definition: { name: "Review-ready work", description: "Work prepared for useful review." },
	},
	definition: {
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
	},
};

const meta = {
	title: "Workspace admin/Practice adoption/Review",
	component: PracticeAdoptionReview,
	parameters: { layout: "padded", chromatic: { viewports: [320, 1440] } },
	args: {
		workspaceSlug: "demo",
		preview,
		definitionOptions: mockPracticeDefinitionOptions,
		onAdopt: fn(),
		isPending: false,
	},
	tags: ["autodocs"],
} satisfies Meta<typeof PracticeAdoptionReview>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Available: Story = {
	play: async ({ canvas, args }) => {
		const adopt = canvas.getByRole("button", { name: "Adopt practice" });
		await expect(adopt).toBeEnabled();
		await expect(canvas.getByText("Starts with Review before sending")).toBeVisible();
		await userEvent.click(adopt);
		await expect(args.onAdopt).toHaveBeenCalledOnce();
	},
};

export const ReusesExistingArea: Story = {
	args: {
		preview: {
			...preview,
			area: { ...preview.area, disposition: "REUSE_EXISTING_AREA" },
		},
	},
};

export const AlreadyAdopted: Story = {
	args: { preview: { ...preview, availability: "ADOPTED" } },
	play: async ({ canvas }) => {
		await expectGenuinelyDisabled(canvas.getByRole("button", { name: "Adopt practice" }));
		await expect(canvas.getByText("Already adopted")).toBeVisible();
	},
};

export const SlugConflict: Story = {
	args: { preview: { ...preview, availability: "SLUG_CONFLICT" } },
	play: async ({ canvas }) => {
		await expectGenuinelyDisabled(canvas.getByRole("button", { name: "Adopt practice" }));
		await expect(canvas.getByText("Slug conflict")).toBeVisible();
	},
};

export const Adopting: Story = {
	args: { isPending: true },
	play: async ({ canvas }) => {
		await expectGenuinelyDisabled(canvas.getByRole("button", { name: "Adopting…" }));
	},
};

export const UnassignedAndOff: Story = {
	args: {
		preview: {
			...preview,
			initialAutonomy: "OFF",
			area: { disposition: "UNASSIGNED" },
		},
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Starts with Off")).toBeVisible();
		await expect(canvas.getByText("Leave unassigned")).toBeVisible();
	},
};

export const LongContentInDarkMode: Story = {
	args: {
		preview: {
			...preview,
			definition: {
				...preview.definition,
				criteria:
					"Confirm that the pull request explains the behavior change, the operational constraints that shaped it, the alternatives considered, and the evidence used to verify the result. Stay silent when the change is generated automatically and no meaningful author decision exists.",
			},
		},
	},
	globals: { theme: "dark" },
};
