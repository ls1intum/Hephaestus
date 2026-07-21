import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, screen, userEvent, within } from "storybook/test";
import type { LlmConnection } from "@/api/types.gen";
import { AdminLlmConnectionsTable } from "./AdminLlmConnectionsTable";

const mockConnections: LlmConnection[] = [
	{
		id: 1,
		slug: "azure-eu",
		displayName: "Azure EU",
		apiProtocol: "azure-openai-responses",
		authHeaderName: "api-key",
		authValuePrefix: "",
		baseUrl: "https://azure-eu.example.com/openai",
		enabled: true,
		hasApiKey: true,
		apiKeyLast4: "ab12",
		createdAt: new Date("2026-05-01T10:00:00Z"),
	},
	{
		id: 2,
		slug: "on-prem-gpu",
		displayName: "On-prem GPU (vLLM)",
		apiProtocol: "openai-completions",
		authHeaderName: "Authorization",
		authValuePrefix: "Bearer ",
		baseUrl: "https://gpu.internal.example.com/v1",
		enabled: false,
		hasApiKey: false,
		createdAt: new Date("2026-05-10T10:00:00Z"),
	},
];

const meta = {
	component: AdminLlmConnectionsTable,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
		connections: mockConnections,
		modelCounts: { 1: 3, 2: 0 },
		isLoading: false,
		isError: false,
		mutatingId: null,
		selectedId: null,
		onSelect: fn(),
		onEdit: fn(),
		onToggleEnabled: fn(),
		onDelete: fn(),
		onAdd: fn(),
	},
} satisfies Meta<typeof AdminLlmConnectionsTable>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};

export const SelectedRow: Story = {
	args: { selectedId: 1 },
};

export const Loading: Story = {
	args: { isLoading: true },
};

export const ErrorState: Story = {
	args: { isError: true, error: new Error("Network error") },
};

export const Empty: Story = {
	args: { connections: [], modelCounts: {} },
};

export const DeleteConfirm: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getByRole("button", { name: /delete azure eu/i }));
		const dialog = await screen.findByRole("alertdialog");
		await expect(within(dialog).getByText(/still on it/i)).toBeInTheDocument();
	},
};
