import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn } from "storybook/test";
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
	revisionNumber: 3,
	status: "AVAILABLE" as const,
	sourceKind: "BUNDLED" as const,
	syncStatus: "SYNCED" as const,
	latestBundledCatalogRevision: 3,
};

const meta = {
	title: "Instance admin/Curated practice editor",
	component: CuratedPracticeForm,
	parameters: {
		layout: "fullscreen",
		chromatic: { viewports: [1440] },
	},
	decorators: [withStandardPage],
	tags: ["autodocs"],
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
		await expect(
			canvas.getByText("A newer version was saved while you were editing"),
		).toBeVisible();
		await expect(canvas.getByRole("button", { name: "Save changes" })).toBeDisabled();
	},
};

export const HephaestusUpdateAvailable: Story = {
	args: {
		mode: "edit",
		initialData: {
			...initialData,
			syncStatus: "UPDATE_AVAILABLE",
			latestBundledCatalogRevision: 4,
		},
		areas,
		isPending: false,
		onUseBundledVersion: fn(),
		onSubmit: fn(),
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Hephaestus update available")).toBeVisible();
		await expect(canvas.getByRole("button", { name: "Use Hephaestus version" })).toBeVisible();
	},
};

export const SourceRemoved: Story = {
	args: {
		mode: "edit",
		initialData: { ...initialData, syncStatus: "SOURCE_REMOVED" },
		areas,
		isPending: false,
		onUseBundledVersion: fn(),
		onSubmit: fn(),
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByText("No longer shipped by Hephaestus")).toBeVisible();
		await expect(
			canvas.queryByRole("button", { name: "Use Hephaestus version" }),
		).not.toBeInTheDocument();
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
};
