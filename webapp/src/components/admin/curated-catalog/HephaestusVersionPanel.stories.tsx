import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, userEvent, within } from "storybook/test";
import type { CatalogEntryStatus } from "@/api/types.gen";
import { HephaestusVersionPanel } from "./HephaestusVersionPanel";

const status = (overrides: Partial<CatalogEntryStatus> = {}): CatalogEntryStatus => ({
	etag: "tag",
	state: "FROM_HEPHAESTUS",
	changeKind: "NONE",
	offered: true,
	retired: false,
	updatedAt: new Date("2026-07-30T12:00:00Z"),
	...overrides,
});

const shipped = {
	name: "Say what changed and why",
	artifactType: "PULL_REQUEST" as const,
	triggerEvents: ["PullRequestCreated"],
	criteria: "The definition Hephaestus ships now.",
	whyItMatters: "So a reviewer can start from intent rather than diff archaeology.",
};

const meta = {
	title: "Instance admin/Practice catalog/Hephaestus version panel",
	component: HephaestusVersionPanel,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
		kind: "practice",
		status: status(),
		isResetPending: false,
		disabled: false,
		onUseHephaestusVersion: fn(),
		onKeepOurVersion: fn(),
	},
} satisfies Meta<typeof HephaestusVersionPanel>;

export default meta;
type Story = StoryObj<typeof meta>;

/** The ordinary case: nothing to decide, so nothing is offered. */
export const FromHephaestus: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText("From Hephaestus")).toBeVisible();
		await expect(
			canvas.queryByRole("button", { name: "Use the Hephaestus version" }),
		).not.toBeInTheDocument();
	},
};

export const EditedHere: Story = {
	args: { status: status({ state: "EDITED_HERE" }) },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		// Returning to Hephaestus is always available once ours differs; there is no update to decline.
		await expect(canvas.getByRole("button", { name: "Use the Hephaestus version" })).toBeVisible();
		await expect(
			canvas.queryByRole("button", { name: "Keep our version" }),
		).not.toBeInTheDocument();
	},
};

/** The only state that asks anything: both answers are offered, and the incoming text is readable. */
export const UpdateWaitingChangesDetection: Story = {
	args: { status: status({ state: "UPDATE_WAITING", changeKind: "DETECTION" }), shipped },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/would change what this practice detects/)).toBeVisible();
		await userEvent.click(canvas.getByRole("button", { name: "Show the Hephaestus version" }));
		await expect(await canvas.findByText("The definition Hephaestus ships now.")).toBeVisible();
		await expect(canvas.getByText("Starts a review when")).toBeVisible();
		await expect(canvas.getByRole("button", { name: "Keep our version" })).toBeVisible();
	},
};

export const UpdateWaitingWordingOnly: Story = {
	args: { status: status({ state: "UPDATE_WAITING", changeKind: "WORDING" }), shipped },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/cannot change what this practice detects/)).toBeVisible();
	},
};

export const UpdateWaitingChangesPresentation: Story = {
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
		await expect(canvas.getByText(/change how this area is presented/)).toBeVisible();
		await expect(canvas.queryByText(/detect/)).not.toBeInTheDocument();
	},
};

export const AddedHere: Story = { args: { status: status({ state: "YOURS" }), kind: "area" } };

export const NoLongerShipped: Story = {
	args: { status: status({ state: "NO_LONGER_SHIPPED" }) },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		// Nothing to return to, so neither decision is offered.
		await expect(
			canvas.queryByRole("button", { name: "Use the Hephaestus version" }),
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

export const KeepingOurVersion: Story = {
	args: {
		status: status({ state: "UPDATE_WAITING", changeKind: "DETECTION" }),
		shipped,
		isKeepPending: true,
	},
};
