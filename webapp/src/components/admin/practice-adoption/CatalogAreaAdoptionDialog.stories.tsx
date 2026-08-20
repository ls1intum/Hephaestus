import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, waitFor, within } from "storybook/test";
import type { CatalogAreaAdoptionPreview } from "@/api/types.gen";
import {
	mockAuthorDeclaredEvidenceValidation,
	mockPracticeDefinitionOptions,
	mockPullRequestBinding,
	mockPullRequestPolicy,
} from "@/mocks/fixtures/practice";
import { CatalogAreaAdoptionDialog } from "./CatalogAreaAdoptionDialog";

const practice = {
	slug: "describe-what-and-why",
	availability: "AVAILABLE" as const,
	etag: '"practice-preview"',
	initialAutonomy: "HUMAN_APPROVAL" as const,
	sourceReviewRuleFingerprint: mockAuthorDeclaredEvidenceValidation.reviewRuleFingerprint,
	area: { slug: "review-ready-work", disposition: "CREATE_CATALOG_AREA" as const },
	definition: {
		name: "Describe what changed and why",
		artifactKind: "scm.pull_request" as const,
		bindings: [mockPullRequestBinding],
		criteria: "Confirm the pull request explains both the change and its motivation.",
		automatedReviewPolicy: mockPullRequestPolicy,
		automatedReviewValidation: mockAuthorDeclaredEvidenceValidation,
		areaSlug: "review-ready-work",
	},
};

const preview: CatalogAreaAdoptionPreview = {
	slug: "review-ready-work",
	definition: {
		name: "Review-ready work",
		description: "Practices that make proposed changes easier to understand and review.",
	},
	disposition: "CREATE_CATALOG_AREA",
	etag: '"area-preview"',
	actions: [
		{ slug: "describe-what-and-why", action: "ADD" },
		{ slug: "focused-changes", action: "KEEP" },
		{ slug: "clear-context", action: "BLOCKED" },
	],
	practices: [
		practice,
		{
			...practice,
			slug: "focused-changes",
			availability: "ADOPTED",
			definition: { ...practice.definition, name: "Keep changes focused" },
		},
		{
			...practice,
			slug: "clear-context",
			availability: "SLUG_CONFLICT",
			definition: { ...practice.definition, name: "Provide clear context" },
		},
	],
};

const meta = {
	title: "Workspace admin/Practice adoption/Area review",
	component: CatalogAreaAdoptionDialog,
	args: {
		open: true,
		preview,
		isLoading: false,
		isError: false,
		isPending: false,
		onOpenChange: fn(),
		onRetry: fn(),
		onConfirm: fn(),
		definitionOptions: mockPracticeDefinitionOptions,
	},
	parameters: { layout: "fullscreen", chromatic: { viewports: [320, 1440] } },
} satisfies Meta<typeof CatalogAreaAdoptionDialog>;

export default meta;
type Story = StoryObj<typeof meta>;

export const ReviewMixedArea: Story = {
	play: async ({ args, canvasElement }) => {
		const documentView = within(canvasElement.ownerDocument.body);
		const screen = within(await documentView.findByRole("dialog"));
		await waitFor(() =>
			expect(screen.getByRole("heading", { name: "Review-ready work" })).toBeVisible(),
		);
		await expect(screen.getByText("Keep changes focused — already in this area")).toBeVisible();
		await expect(screen.getByText("Provide clear context — name unavailable")).toBeVisible();
		await screen.getByRole("button", { name: "Describe what changed and why" }).click();
		await expect(screen.getByRole("heading", { name: "What this practice checks" })).toBeVisible();
		await screen.getByRole("button", { name: "Apply 1 change" }).click();
		await expect(args.onConfirm).toHaveBeenCalledOnce();
	},
};

export const RestoreDeletedArea: Story = {
	args: {
		preview: {
			...preview,
			actions: [{ slug: "focused-changes", action: "MOVE_TO_AREA" }],
			practices: [preview.practices[1]],
		},
	},
	play: async ({ args, canvasElement }) => {
		const screen = within(await within(canvasElement.ownerDocument.body).findByRole("dialog"));
		await waitFor(() =>
			expect(screen.getByRole("heading", { name: /Practices to move/ })).toBeVisible(),
		);
		await screen.getByRole("button", { name: "Restore area" }).click();
		await expect(args.onConfirm).toHaveBeenCalledOnce();
	},
};

export const Loading: Story = { args: { preview: undefined, isLoading: true } };
export const LoadFailure: Story = { args: { preview: undefined, isLoading: false, isError: true } };
