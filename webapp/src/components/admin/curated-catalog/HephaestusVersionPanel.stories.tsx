import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, userEvent, within } from "storybook/test";
import type { CatalogEntryStatus } from "@/api/types.gen";
import {
	mockAuthorDeclaredEvidenceValidation,
	mockPullRequestEvidence,
} from "@/mocks/fixtures/practice";
import { HephaestusVersionPanel } from "./HephaestusVersionPanel";

const status = (overrides: Partial<CatalogEntryStatus> = {}): CatalogEntryStatus => ({
	etag: "tag",
	state: "FROM_HEPHAESTUS",
	changeKind: "NONE",
	offered: true,
	...overrides,
});

const shipped = {
	name: "Say what changed and why",
	artifactType: "PULL_REQUEST" as const,
	triggerEvents: ["PullRequestCreated"],
	criteria: "The updated default criteria.",
	whyItMatters: "So a reviewer can start from intent rather than diff archaeology.",
	evidence: mockPullRequestEvidence,
	evidenceValidation: mockAuthorDeclaredEvidenceValidation,
};

const meta = {
	title: "Instance admin/Practice catalog/Hephaestus default panel",
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
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText("Uses Hephaestus default")).toBeVisible();
		await expect(
			canvas.queryByRole("button", { name: "Apply Hephaestus update" }),
		).not.toBeInTheDocument();
	},
};

export const Customized: Story = {
	args: { status: status({ state: "EDITED_HERE" }) },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByRole("button", { name: "Restore Hephaestus default" })).toBeVisible();
		await expect(
			canvas.queryByRole("button", { name: "Keep saved version" }),
		).not.toBeInTheDocument();
	},
};

export const UpdateChangesReviewBehavior: Story = {
	args: { status: status({ state: "UPDATE_WAITING", changeKind: "DETECTION" }), shipped },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/would change review behavior/)).toBeVisible();
		await userEvent.click(canvas.getByRole("button", { name: "Review Hephaestus update" }));
		await expect(await canvas.findByText("The updated default criteria.")).toBeVisible();
		await expect(canvas.getByText("Starts a review when")).toBeVisible();
		await expect(canvas.getByText("Pull or merge request is opened")).toBeVisible();
		await expect(canvas.getByText("Hephaestus detectability")).toBeVisible();
		await expect(canvas.getByText("Declared semantic assessment")).toBeVisible();
		await expect(canvas.getByText("scm.pull-request.core")).toBeVisible();
		await expect(canvas.getByText("Not independently validated")).toBeVisible();
		await expect(
			canvas.getByText("Repository evidence does not establish behavior in a deployed runtime."),
		).toBeVisible();
		await expect(canvas.queryByText("PullRequestCreated")).not.toBeInTheDocument();
		await expect(canvas.getByRole("button", { name: "Keep saved version" })).toBeVisible();
	},
};

export const WordingOnlyUpdate: Story = {
	args: { status: status({ state: "UPDATE_WAITING", changeKind: "WORDING" }), shipped },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/review behavior would stay the same/i)).toBeVisible();
	},
};

export const AreaAppearanceUpdate: Story = {
	args: {
		kind: "area",
		status: status({ state: "UPDATE_WAITING", changeKind: "PRESENTATION" }),
		shipped: {
			name: "Review-ready work",
			description: "Make every change easy to review.",
			icon: "PackageCheck",
			color: "sky",
		},
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(
			canvas.getByText(/would change the area's name, description, icon, or color/),
		).toBeVisible();
		await expect(canvas.queryByText(/detect/)).not.toBeInTheDocument();
	},
};

export const NoHephaestusDefault: Story = {
	args: { status: status({ state: "YOURS" }), kind: "area" },
};

export const RemovedFromDefaults: Story = {
	args: { status: status({ state: "NO_LONGER_SHIPPED" }) },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
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
		status: status({ state: "UPDATE_WAITING", changeKind: "DETECTION" }),
		shipped,
		isResetPending: true,
	},
};

export const KeepingSavedVersion: Story = {
	args: {
		status: status({ state: "UPDATE_WAITING", changeKind: "DETECTION" }),
		shipped,
		isKeepPending: true,
	},
};
