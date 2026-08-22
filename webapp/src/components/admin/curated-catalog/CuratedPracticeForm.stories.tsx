import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn } from "storybook/test";
import {
	mockAuthorDeclaredEvidenceValidation,
	mockPracticeDefinitionOptions,
	mockPullRequestBinding,
	mockPullRequestPolicy,
} from "@/mocks/fixtures/practice";
import { withStandardPage } from "@/stories/decorators";
import { expectNoPageOverflow } from "@/test/reflow";
import { CuratedPracticeForm, type CuratedPracticeFormInitialValue } from "./CuratedPracticeForm";

const areas = [
	{ slug: "communication", name: "Communication" },
	{ slug: "version-control", name: "Version control" },
];

const initialData: CuratedPracticeFormInitialValue = {
	slug: "clear-pr-description",
	name: "Write a clear pull request description",
	areaSlug: "communication",
	bindings: [mockPullRequestBinding],
	criteria: "Review whether the description explains the purpose, approach, and testing.",
	whyItMatters: "Reviewers should not need to reconstruct the author's intent.",
	whatGoodLooksLike: "The description states why, what changed, and how it was verified.",
	precomputeScript: "export default function precompute() { return {}; }",
	automatedReviewPolicy: mockPullRequestPolicy,
	automatedReviewValidation: mockAuthorDeclaredEvidenceValidation,
	status: {
		etag: "tag",
		state: "FROM_HEPHAESTUS" as const,
		changeKind: "NONE" as const,
		offered: true,
	},
};

const meta = {
	title: "Instance admin/Practice catalog/Practice editor",
	component: CuratedPracticeForm,
	parameters: {
		layout: "fullscreen",
		chromatic: { viewports: [1440] },
	},
	decorators: [withStandardPage],
	tags: ["autodocs"],
	args: { definitionOptions: mockPracticeDefinitionOptions },
} satisfies Meta<typeof CuratedPracticeForm>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Create: Story = {
	args: {
		mode: "create",
		areas,
		isPending: false,
		onSubmit: fn(),
	},
	parameters: {
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320, 1440] },
	},
	play: async () => {
		await expectNoPageOverflow();
	},
};

export const Edit: Story = {
	args: {
		mode: "edit",
		initialData,
		areas,
		isPending: false,
		onSubmit: fn(),
	},
};

export const StaleEdit: Story = {
	args: {
		mode: "edit",
		initialData,
		areas,
		isPending: false,
		conflict: true,
		onContinueWithDraft: fn(),
		onSubmit: fn(),
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByText("This practice changed while you were editing")).toBeVisible();
		await expect(canvas.getByRole("button", { name: "Save changes" })).toBeDisabled();
	},
};

export const HephaestusUpdateAvailable: Story = {
	args: {
		mode: "edit",
		initialData: {
			...initialData,
			status: {
				...initialData.status,
				state: "UPDATE_WAITING" as const,
				changeKind: "DETECTION" as const,
			},
			shipped: {
				name: "Say what changed and why",
				artifactKind: "scm.pull_request",
				bindings: [mockPullRequestBinding],
				criteria: "The updated default criteria",
				automatedReviewPolicy: mockPullRequestPolicy,
				automatedReviewValidation: mockAuthorDeclaredEvidenceValidation,
				whyItMatters: "So a reviewer can start from intent rather than diff archaeology.",
			},
		},
		areas,
		isPending: false,
		onUseHephaestusVersion: fn(),
		onKeepCurrentDefinition: fn(),
		onSubmit: fn(),
	},
	play: async ({ canvas, userEvent }) => {
		// The full label, since colour alone cannot carry which kind of update it is.
		await expect(canvas.getByText("Hephaestus update available: review rules")).toBeVisible();
		await expect(canvas.getByText(/would change review rules/)).toBeVisible();
		await expect(canvas.getByRole("button", { name: "Review Hephaestus update" })).toBeVisible();
		await expect(canvas.getByRole("button", { name: "Apply Hephaestus update" })).toBeVisible();
		await expect(canvas.getByRole("button", { name: "Keep saved version" })).toBeVisible();
		await userEvent.click(canvas.getByRole("button", { name: "Review Hephaestus update" }));
		await expect(canvas.getByText("Unassigned")).toBeVisible();
		await expect(canvas.getAllByText("Not set").length).toBeGreaterThan(0);
	},
};

export const ValidationErrors: Story = {
	args: {
		mode: "create",
		areas,
		isPending: false,
		onSubmit: fn(),
	},
	parameters: { chromatic: { disableSnapshot: true } },
	play: async ({ canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("button", { name: "Create practice" }));
		await expect(canvas.getByText("Name must be at least 3 characters")).toBeVisible();
		await expect(canvas.queryByText("Select at least one trigger event")).not.toBeInTheDocument();
		await expect(canvas.getByRole("textbox", { name: /Name/ })).toHaveAttribute(
			"aria-describedby",
			"practice-name-error",
		);
	},
};

export const Submitting: Story = {
	args: {
		mode: "edit",
		initialData,
		areas,
		isPending: true,
		onSubmit: fn(),
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("textbox", { name: /Name/ })).toBeDisabled();
	},
};
