import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, screen, userEvent, within } from "storybook/test";
import type { OutlineCollection } from "@/api/types.gen";
import { Table, TableBody } from "@/components/ui/table";
import { OutlineCollectionRow } from "./OutlineCollectionRow";

/**
 * One mirrored Outline collection. A `<tr>` is only valid inside a table, so every story wraps the
 * row in a minimal `Table`. Pure — pause / resume / remove are delegated upward.
 */
const meta = {
	component: OutlineCollectionRow,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	decorators: [
		(Story) => (
			<Table>
				<TableBody>
					<Story />
				</TableBody>
			</Table>
		),
	],
	args: {
		onPause: fn(),
		onResume: fn(),
		onRemove: fn(),
	},
} satisfies Meta<typeof OutlineCollectionRow>;

export default meta;
type Story = StoryObj<typeof meta>;

const ago = (minutes: number) => new Date(Date.now() - minutes * 60 * 1000);

const base: OutlineCollection = {
	id: 1,
	collectionId: "col-engineering",
	name: "Engineering",
	urlId: "engineering-4nZ3x",
	color: "#4E5C6E",
	state: "ENABLED",
	syncStatus: "COMPLETE",
	documentCount: 87,
	lastSyncedAt: ago(12),
	createdAt: ago(60 * 24 * 30),
};

/** Steady state — mirroring, up to date, Pause offered in the row menu. */
export const Mirroring: Story = {
	args: { collection: base },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText("Mirroring")).toBeInTheDocument();
		await expect(canvas.getByText(/up to date/i)).toBeInTheDocument();
		await expect(canvas.getByText("engineering-4nZ3x")).toBeInTheDocument();

		await userEvent.click(canvas.getByRole("button", { name: /actions for engineering/i }));
		await expect(await screen.findByRole("menuitem", { name: /^pause$/i })).toBeInTheDocument();
	},
};

/** Paused — syncing frozen, documents kept; the menu offers Resume instead of Pause. */
export const Paused: Story = {
	args: {
		collection: { ...base, id: 2, collectionId: "col-handbook", name: "Handbook", state: "PAUSED" },
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText("Paused")).toBeInTheDocument();

		await userEvent.click(canvas.getByRole("button", { name: /actions for handbook/i }));
		await expect(await screen.findByRole("menuitem", { name: /resume/i })).toBeInTheDocument();
		await expect(screen.queryByRole("menuitem", { name: /^pause$/i })).not.toBeInTheDocument();
	},
};

/** First pass still running — a PENDING collection with no clean sync yet. */
export const Syncing: Story = {
	args: {
		collection: {
			...base,
			id: 3,
			collectionId: "col-decisions",
			name: "Architecture Decisions",
			urlId: undefined,
			icon: "📐",
			syncStatus: "PENDING",
			documentCount: 0,
			lastSyncedAt: undefined,
		},
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/syncing…/i)).toBeInTheDocument();
		// No urlId ⇒ no subtitle at all; the raw UUID is never printed at the admin.
		await expect(canvas.queryByText(/col-decisions/)).not.toBeInTheDocument();
	},
};

/** The last sync failed — the detail opens on click (a tooltip never opens on touch) and is selectable. */
export const SyncError: Story = {
	args: {
		collection: {
			...base,
			id: 4,
			collectionId: "col-legacy",
			name: "Legacy Wiki",
			urlId: undefined,
			syncStatus: "PENDING",
			documentCount: 3,
			lastSyncError: "Outline API returned 403 for collections.info — the bot user lost access.",
		},
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getByRole("button", { name: /sync error for legacy wiki/i }));
		await expect(await screen.findByText(/the bot user lost access/i)).toBeInTheDocument();
	},
};

/** The last pass hit the shared export budget — coverage plus a warning (not an error) detail. */
export const BudgetSkipped: Story = {
	args: {
		collection: {
			...base,
			id: 5,
			collectionId: "col-research",
			name: "Research Notes",
			urlId: "research-8pL4m",
			documentCount: 480,
			documentsUpstream: 512,
			exportsSkippedForBudget: 32,
			lastSyncedAt: ago(30),
		},
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText("480")).toBeInTheDocument();
		await expect(canvas.getByText(/\/ 512/)).toBeInTheDocument();

		await userEvent.click(
			canvas.getByRole("button", { name: /32 exports skipped for budget for research notes/i }),
		);
		await expect(await screen.findByText(/catch up on the next reconcile/i)).toBeInTheDocument();
	},
};
