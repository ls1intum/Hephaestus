import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, screen, userEvent, within } from "storybook/test";
import type { ConfigAuditEntryView } from "@/api/types.gen";
import { ConfigAuditTable } from "./ConfigAuditTable";

const entries: ConfigAuditEntryView[] = [
	{
		id: 4,
		occurredAt: new Date("2026-07-10T10:05:00Z"),
		actorKind: "USER",
		actorAccountId: 7,
		actor: { id: 7, displayName: "Grace Hopper", email: "grace@example.com" },
		entityType: "PRACTICE_REVIEW_SETTINGS",
		entityId: "12",
		action: "UPDATED",
		changedKeys: ["cooldownMinutes"],
		oldValue: '{"cooldownMinutes":30,"skipDrafts":true,"deliverToMerged":false}',
		newValue: '{"cooldownMinutes":10,"skipDrafts":true,"deliverToMerged":false}',
		workspaceId: 12,
	},
	{
		id: 3,
		occurredAt: new Date("2026-07-10T09:50:00Z"),
		actorKind: "IMPERSONATED",
		actorAccountId: 42,
		actor: { id: 42, displayName: "Ada Lovelace", email: "ada@example.com" },
		actingAccountId: 7,
		actingActor: { id: 7, displayName: "Grace Hopper", email: "grace@example.com" },
		entityType: "AGENT_CONFIG",
		entityId: "5",
		action: "UPDATED",
		changedKeys: ["modelName", "llmApiKeySet"],
		oldValue: '{"name":"GPT reviewer","modelName":"gpt-4o","llmApiKeySet":false}',
		newValue: '{"name":"GPT reviewer","modelName":"gpt-5","llmApiKeySet":true}',
		workspaceId: 12,
	},
	{
		id: 2,
		occurredAt: new Date("2026-07-10T09:30:00Z"),
		actorKind: "USER",
		actorAccountId: 7,
		actor: { id: 7, displayName: "Grace Hopper", email: "grace@example.com" },
		entityType: "AGENT_CONFIG",
		entityId: "5",
		action: "CREATED",
		changedKeys: ["name", "modelName", "enabled"],
		newValue: '{"name":"GPT reviewer","modelName":"gpt-4o","enabled":true,"llmApiKeySet":true}',
		workspaceId: 12,
	},
	{
		id: 1,
		occurredAt: new Date("2026-07-10T09:00:00Z"),
		actorKind: "SYSTEM",
		entityType: "PRACTICE_REVIEW_SETTINGS",
		entityId: "12",
		action: "CREATED",
		changedKeys: ["runForAllUsers"],
		newValue: '{"runForAllUsers":false}',
		workspaceId: 12,
	},
];

const meta = {
	component: ConfigAuditTable,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
		entries,
		isLoading: false,
		isError: false,
		hasFilter: false,
		hasNextPage: false,
		isFetchingNextPage: false,
		onLoadMore: fn(),
		onRetry: fn(),
		onFilterActor: fn(),
	},
} satisfies Meta<typeof ConfigAuditTable>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/cooldownMinutes: 30 → 10/)).toBeInTheDocument();
		await expect(canvas.getAllByText(/GPT reviewer/).length).toBeGreaterThan(0);
	},
};

export const Impersonation: Story = {
	args: { entries: [entries[1]] },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/acting as Ada Lovelace/)).toBeInTheDocument();
		await expect(canvas.getByText(/not set → ••••••/)).toBeInTheDocument();
	},
};

export const SystemActor: Story = {
	args: {
		entries: [entries[3]],
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText("System")).toBeInTheDocument();
	},
};

export const WithWorkspaceColumn: Story = {
	args: {
		showWorkspace: true,
		resolveWorkspaceName: (id) => (id === 12 ? "Acme Engineering" : undefined),
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText("Workspace")).toBeInTheDocument();
		await expect(canvas.getAllByText("Acme Engineering").length).toBeGreaterThan(0);
	},
};

export const RowDetail: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		const buttons = canvas.getAllByRole("button", { name: /View details/i });
		await userEvent.click(buttons[0]);
		const dialog = within(await screen.findByRole("dialog"));
		await expect(dialog.getByText("cooldownMinutes")).toBeInTheDocument();
		await expect(dialog.getByText("30")).toBeInTheDocument();
		await expect(dialog.getByText("10")).toBeInTheDocument();
	},
};

export const EmptyWithFilter: Story = {
	args: { entries: [], hasFilter: true },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText("No changes match your filters")).toBeInTheDocument();
	},
};

export const EmptyInitial: Story = {
	args: { entries: [] },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText("No settings changes yet")).toBeInTheDocument();
	},
};

export const ErrorState: Story = {
	args: { entries: [], isError: true },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/Couldn’t load the audit log/i)).toBeInTheDocument();
		await expect(canvas.getByRole("button", { name: /Try again/i })).toBeInTheDocument();
	},
};

export const LoadMore: Story = {
	args: { hasNextPage: true, isFetchingNextPage: true },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByRole("button", { name: /Load more/i })).toBeDisabled();
	},
};

export const ColumnCountMatchesHeader: Story = {
	args: { showWorkspace: true },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		const headers = canvas.getAllByRole("columnheader");
		const cells = within(canvas.getAllByRole("row")[1]).getAllByRole("cell");
		await expect(headers).toHaveLength(cells.length);
	},
};
