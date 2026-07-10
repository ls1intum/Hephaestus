import type { Meta, StoryObj } from "@storybook/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { HttpResponse, http } from "msw";
import { expect, fn, screen, userEvent, within } from "storybook/test";
import type { OutlineCollection } from "@/api/types.gen";
import { OutlineCollectionsSection } from "./OutlineCollectionsSection";

/**
 * Admin surface for the mirrored-collections plane of the Outline integration. Presentational:
 * the lifecycle mutations are delegated to the container, so these stories mock the callbacks
 * with `fn()`. The Add-collection dialog issues a lazy candidates query (only while open), so
 * the component is wrapped in a fresh QueryClient and candidate-driven stories bring their own
 * MSW handler.
 */
const meta = {
	component: OutlineCollectionsSection,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	decorators: [
		(Story) => (
			<QueryClientProvider
				client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
			>
				<Story />
			</QueryClientProvider>
		),
	],
	args: {
		workspaceSlug: "demo-workspace",
		collections: [],
		isLoading: false,
		onRegisterCollection: fn(),
		onUpdateCollectionState: fn(),
		onRemoveCollection: fn(),
	},
} satisfies Meta<typeof OutlineCollectionsSection>;

export default meta;
type Story = StoryObj<typeof meta>;

const ago = (minutes: number) => new Date(Date.now() - minutes * 60 * 1000);

const engineering: OutlineCollection = {
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

const decisions: OutlineCollection = {
	id: 2,
	collectionId: "col-decisions",
	name: "Architecture Decisions",
	urlId: "adr-9kQ2p",
	icon: "📐",
	state: "ENABLED",
	syncStatus: "PENDING",
	documentCount: 14,
	createdAt: ago(5),
};

const paused: OutlineCollection = {
	id: 3,
	collectionId: "col-handbook",
	name: "Handbook",
	urlId: "handbook-7xW1c",
	color: "#FF825C",
	state: "PAUSED",
	syncStatus: "COMPLETE",
	documentCount: 42,
	lastSyncedAt: ago(60 * 24 * 3),
	createdAt: ago(60 * 24 * 90),
};

const failing: OutlineCollection = {
	id: 4,
	collectionId: "col-legacy",
	name: "Legacy Wiki",
	state: "ENABLED",
	syncStatus: "PENDING",
	documentCount: 3,
	lastSyncedAt: ago(60 * 24 * 2),
	lastSyncError: "Outline API returned 403 for collections.info — the bot user lost access.",
	createdAt: ago(60 * 24 * 10),
};

const budgetThrottled: OutlineCollection = {
	id: 5,
	collectionId: "col-research",
	name: "Research Notes",
	urlId: "research-8pL4m",
	state: "ENABLED",
	syncStatus: "COMPLETE",
	documentCount: 480,
	documentsUpstream: 512,
	exportsSkippedForBudget: 32,
	lastSyncedAt: ago(30),
	createdAt: ago(60 * 24 * 60),
};

/** First run — no collections yet; the empty state offers an Add affordance. */
export const Empty: Story = {
	args: { collections: [] },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/no collections mirrored yet/i)).toBeInTheDocument();
		await expect(canvas.getAllByRole("button", { name: /add collection/i }).length).toBeGreaterThan(
			1,
		);
	},
};

/** Mixed list — an up-to-date collection and one still on its first sync pass. */
export const Populated: Story = {
	args: { collections: [engineering, decisions] },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText("Engineering")).toBeInTheDocument();
		await expect(canvas.getByText(/up to date/i)).toBeInTheDocument();
		await expect(canvas.getByText(/syncing…/i)).toBeInTheDocument();
		await expect(canvas.getByText("87")).toBeInTheDocument();
	},
};

/** A paused collection — sync frozen, documents kept, Resume in the row menu. */
export const PausedCollection: Story = {
	args: { collections: [paused] },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText("Paused")).toBeInTheDocument();
		await userEvent.click(canvas.getByRole("button", { name: /actions for handbook/i }));
		await expect(await screen.findByRole("menuitem", { name: /resume/i })).toBeInTheDocument();
		await expect(screen.queryByRole("menuitem", { name: /^pause$/i })).not.toBeInTheDocument();
	},
};

/** A collection whose last sync failed — the error is a focusable destructive indicator. */
export const SyncErrorRow: Story = {
	args: { collections: [failing] },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		const trigger = canvas.getByRole("button", { name: /sync error for legacy wiki/i });
		await expect(trigger).toBeInTheDocument();
		await userEvent.hover(trigger);
		await expect(await screen.findByText(/lost access/i)).toBeInTheDocument();
	},
};

/** A collection whose last pass hit the shared export budget — coverage and the skipped count show. */
export const BudgetThrottled: Story = {
	args: { collections: [budgetThrottled] },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText("480")).toBeInTheDocument();
		await expect(canvas.getByText(/\/ 512/)).toBeInTheDocument();
		const trigger = canvas.getByRole("button", {
			name: /32 exports skipped for budget for research notes/i,
		});
		await expect(trigger).toBeInTheDocument();
		await userEvent.hover(trigger);
		await expect(await screen.findByText(/skipped for the shared budget/i)).toBeInTheDocument();
	},
};

/** Loading — skeleton rows while the collection list resolves. */
export const Loading: Story = {
	args: { isLoading: true, collections: [] },
};

/** The collection-list query failed — a distinct error panel with Retry, not the empty state. */
export const LoadError: Story = {
	args: { collections: [], isError: true, onRetry: fn() },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.queryByText(/no collections mirrored yet/i)).not.toBeInTheDocument();
		await expect(canvas.getByText(/couldn't load the mirrored collections/i)).toBeInTheDocument();
		await expect(canvas.getByRole("button", { name: /^retry$/i })).toBeInTheDocument();
	},
};

/** The add dialog: candidates load lazily; already-mirrored entries are checked and disabled. */
export const AddCollectionPicker: Story = {
	args: { collections: [engineering] },
	parameters: {
		msw: {
			handlers: [
				http.get("*/workspaces/:workspaceSlug/outline/collections/candidates", () =>
					HttpResponse.json([
						{
							collectionId: "col-engineering",
							name: "Engineering",
							urlId: "engineering-4nZ3x",
							color: "#4E5C6E",
							alreadyMirrored: true,
						},
						{
							collectionId: "col-product",
							name: "Product",
							urlId: "product-2mR8v",
							icon: "🧭",
							alreadyMirrored: false,
						},
					]),
				),
			],
		},
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getByRole("button", { name: /add collection/i }));
		const dialog = await screen.findByRole("dialog");

		// The base-ui Checkbox renders a role="checkbox" span — disabled is ARIA, not a DOM prop.
		const mirrored = await within(dialog).findByRole("checkbox", { name: /engineering/i });
		await expect(mirrored).toHaveAttribute("aria-disabled", "true");
		await expect(mirrored).toBeChecked();
		await expect(within(dialog).getByText(/already mirrored/i)).toBeInTheDocument();

		await userEvent.click(within(dialog).getByRole("checkbox", { name: /product/i }));
		await expect(within(dialog).getByRole("button", { name: /add 1 collection/i })).toBeEnabled();
	},
};

/** The candidates probe failed (Outline unreachable) — the ProblemDetail lands in the dialog. */
export const AddCollectionProbeFails: Story = {
	args: { collections: [] },
	parameters: {
		msw: {
			handlers: [
				http.get("*/workspaces/:workspaceSlug/outline/collections/candidates", () =>
					HttpResponse.json(
						{
							type: "about:blank",
							title: "Bad Gateway",
							status: 502,
							detail: "Outline did not respond to collections.list.",
						},
						{ status: 502 },
					),
				),
			],
		},
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getAllByRole("button", { name: /add collection/i })[0]);
		const dialog = await screen.findByRole("dialog");
		await expect(await within(dialog).findByText(/outline did not respond/i)).toBeInTheDocument();
		await expect(within(dialog).getByRole("button", { name: /^retry$/i })).toBeInTheDocument();
	},
};

/** Removing a collection is guarded by a confirm that states the erase. */
export const RemoveConfirm: Story = {
	args: { collections: [engineering] },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getByRole("button", { name: /actions for engineering/i }));
		await userEvent.click(await screen.findByRole("menuitem", { name: /remove & erase/i }));
		const dialog = await screen.findByRole("alertdialog");
		await expect(
			within(dialog).getByText(/permanently erases all 87 mirrored documents/i),
		).toBeInTheDocument();
		await expect(
			within(dialog).getByRole("button", { name: /remove & erase/i }),
		).toBeInTheDocument();
	},
};
