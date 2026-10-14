import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, userEvent } from "storybook/test";

import { mockMergeReviewFields, mockPracticeDefinitionOptions } from "@/mocks/fixtures/practice";
import { bundledGuidance } from "@/stories/practice-guidance-story-mock-data";
import { expectNoOverflowingElement } from "@/stories/reflow";

import type { CatalogEntryStatus } from "./catalog-entry-state";
import { catalogPracticeDefinition } from "./fixtures";
import { groupVersionFields, practiceVersionFields } from "./hephaestus-version-fields";
import { HephaestusVersionPanel } from "./HephaestusVersionPanel";

function shippedPractice(...overrides: Parameters<typeof catalogPracticeDefinition>) {
	return practiceVersionFields(
		catalogPracticeDefinition(...overrides),
		"scm.pull_request",
		mockPracticeDefinitionOptions,
	);
}

const status = (overrides: Partial<CatalogEntryStatus> = {}): CatalogEntryStatus => ({
	state: "FROM_HEPHAESTUS",
	changeKind: "NONE",
	excluded: false,
	...overrides,
});

const shipped = shippedPractice({
	...mockMergeReviewFields,
	about: "REVIEWER",
	appliesWhen: {
		anyOf: [{ changedPath: ["**/*.swift"] }],
		otherwise: "The change has no Swift code.",
	},
	criteria: "The updated default criteria.",
	script: "export default function practiceScript() { return {}; }",
	lineNotes: false,
});

const meta = {
	component: HephaestusVersionPanel,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
		kind: "practice",
		status: status(),
		isResetPending: false,
		disabled: false,
		onUseHephaestusVersion: fn(),
		onKeepCurrentDefinition: fn(),
	},
} satisfies Meta<typeof HephaestusVersionPanel>;

export default meta;
type Story = StoryObj<typeof meta>;

export const UsesDefault: Story = {
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Uses Hephaestus default")).toBeVisible();
		await expect(
			canvas.queryByRole("button", { name: "Apply Hephaestus update" }),
		).not.toBeInTheDocument();
	},
};

export const Customized: Story = {
	args: { status: status({ state: "EDITED_HERE" }) },
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("button", { name: "Restore Hephaestus default" })).toBeVisible();
		await expect(
			canvas.queryByRole("button", { name: "Keep saved version" }),
		).not.toBeInTheDocument();
	},
};

export const UpdateChangesReviewRules: Story = {
	args: { status: status({ state: "UPDATE_WAITING", changeKind: "REVIEW" }), shipped },
	play: async ({ canvas }) => {
		await expect(canvas.getByText(/would change review rules/u)).toBeVisible();
		await userEvent.click(canvas.getByRole("button", { name: "Review Hephaestus update" }));
		await expect(await canvas.findByText("The updated default criteria.")).toBeVisible();
		await expect(canvas.getByText("Merged")).toBeVisible();
		await expect(canvas.queryByText(/scm\.pull_request\.merged/u)).not.toBeInTheDocument();
		await expect(
			canvas.getByText(/Review threads and decisions · required and captured whole/u),
		).toBeVisible();
		await expect(canvas.getByText("Reviewer")).toBeVisible();
		await expect(canvas.getByText(/Changed path matches \*\*\/\*\.swift/u)).toBeVisible();
		await expect(canvas.getByText(/Otherwise: The change has no Swift code\./u)).toBeVisible();
		await expect(
			canvas.getByText("Feedback stays in the summary, not on a changed line."),
		).toBeVisible();
		await expect(canvas.getByRole("button", { name: "Keep saved version" })).toBeVisible();
	},
};

export const BuiltInScript: Story = {
	args: {
		status: status({ state: "EDITED_HERE" }),
		shipped: shippedPractice(),
	},
	play: async ({ canvas }) => {
		await userEvent.click(canvas.getByRole("button", { name: "View Hephaestus default" }));
		await expect(await canvas.findByText("Built-in")).toBeVisible();
		await expect(canvas.getByText("Not included in scans")).toBeVisible();
	},
};

export const ReviewedByPeople: Story = {
	args: {
		status: status({ state: "EDITED_HERE" }),
		shipped: shippedPractice({
			reviewedBy: {
				by: "PEOPLE",
				humanReason: {
					code: "NEEDS_PRODUCT_CONTEXT",
					description: "Only the team knows the product context.",
				},
				limits: [],
			},
		}),
	},
	play: async ({ canvas }) => {
		await userEvent.click(canvas.getByRole("button", { name: "View Hephaestus default" }));
		await expect(
			await canvas.findByText("People: Only the team knows the product context."),
		).toBeVisible();
	},
};

export const WordingOnlyUpdate: Story = {
	args: { status: status({ state: "UPDATE_WAITING", changeKind: "WORDING" }), shipped },
	play: async ({ canvas }) => {
		await expect(canvas.getByText(/review rules would stay the same/iu)).toBeVisible();
	},
};

export const GroupAppearanceUpdate: Story = {
	args: {
		kind: "group",
		status: status({ state: "UPDATE_WAITING", changeKind: "PRESENTATION" }),
		shipped: groupVersionFields({
			name: "Review-ready work",
			description: "Make every change easy to review.",
			icon: "PackageCheck",
			color: "sky",
		}),
	},
	play: async ({ canvas }) => {
		await expect(
			canvas.getByText(/would change the group’s name, description, icon, or color/u),
		).toBeVisible();
	},
};

export const NoHephaestusDefault: Story = {
	args: { kind: "group", status: status({ state: "YOURS" }) },
};

export const RemovedFromDefaults: Story = {
	args: { status: status({ state: "NO_LONGER_SHIPPED" }) },
	play: async ({ canvas }) => {
		await expect(
			canvas.getByRole("button", { name: "Keep saved version as custom" }),
		).toBeVisible();
		await expect(
			canvas.queryByRole("button", { name: "Apply Hephaestus update" }),
		).not.toBeInTheDocument();
	},
};

export const TakingTheUpdate: Story = {
	args: {
		status: status({ state: "UPDATE_WAITING", changeKind: "REVIEW" }),
		shipped,
		isResetPending: true,
	},
};

export const KeepingSavedVersion: Story = {
	args: {
		status: status({ state: "UPDATE_WAITING", changeKind: "REVIEW" }),
		shipped,
		isKeepPending: true,
	},
};

export const NarrowViewport: Story = {
	args: { status: status({ state: "UPDATE_WAITING", changeKind: "REVIEW" }), shipped },
	parameters: { viewport: { defaultViewport: "reflow" }, chromatic: { viewports: [320] } },
	play: async ({ canvasElement }) => {
		await expectNoOverflowingElement(canvasElement);
	},
};

/** The default's visual shows as its picture, never as markup; its guide as the Markdown it ships. */
export const DefaultWithVisualAndGuide: Story = {
	args: {
		status: status({ state: "EDITED_HERE" }),
		shipped: shippedPractice({
			guidance: {
				...catalogPracticeDefinition().guidance,
				visual: bundledGuidance.visual,
				guide: bundledGuidance.guide,
			},
		}),
	},
	play: async ({ canvas }) => {
		await userEvent.click(canvas.getByRole("button", { name: "View Hephaestus default" }));
		await expect(canvas.getByRole("img", { name: bundledGuidance.visual.alt })).toBeVisible();
		await expect(canvas.getByText(/^## How to do it/u)).toBeVisible();
		await expect(canvas.queryByText(/<svg/u)).not.toBeInTheDocument();
	},
};
