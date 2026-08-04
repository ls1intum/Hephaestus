import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn } from "storybook/test";
import {
	mockAuthorDeclaredEvidenceValidation,
	mockPracticeEvidenceOptions,
	mockPullRequestEvidence,
} from "@/mocks/fixtures/practice";
import { withStandardPage } from "@/stories/decorators";
import { expectNoPageOverflow } from "@/test/reflow";
import { CuratedPracticeForm } from "./CuratedPracticeForm";

const areas = [
	{ slug: "communication", name: "Communication" },
	{ slug: "version-control", name: "Version control" },
];

const initialData = {
	slug: "clear-pr-description",
	name: "Write a clear pull request description",
	artifactType: "PULL_REQUEST" as const,
	areaSlug: "communication",
	triggerEvents: ["PullRequestCreated", "PullRequestReady"],
	criteria: "Assess whether the description explains the purpose, approach, and testing.",
	whyItMatters: "Reviewers should not need to reconstruct the author's intent.",
	whatGoodLooksLike: "The description states why, what changed, and how it was verified.",
	precomputeScript: "export default function precompute() { return {}; }",
	automatedAssessmentPolicy: mockPullRequestEvidence,
	automatedAssessmentValidation: mockAuthorDeclaredEvidenceValidation,
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
	args: { evidenceOptions: mockPracticeEvidenceOptions },
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
				artifactType: "PULL_REQUEST",
				triggerEvents: ["PullRequestCreated"],
				criteria: "The updated default criteria",
				automatedAssessmentPolicy: mockPullRequestEvidence,
				automatedAssessmentValidation: mockAuthorDeclaredEvidenceValidation,
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
		await expect(canvas.getByText("Hephaestus update available")).toBeVisible();
		await expect(canvas.getByText(/would change review behavior/)).toBeVisible();
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
		await expect(canvas.getByText("Select at least one trigger event")).toBeVisible();
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
