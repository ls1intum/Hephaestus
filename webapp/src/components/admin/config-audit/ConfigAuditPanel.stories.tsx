import type { Meta, StoryObj } from "@storybook/react-vite";
import { HttpResponse, http } from "msw";
import { expect, fn, within } from "storybook/test";
import type { ConfigAuditEntryView } from "@/api/types.gen";
import { AdminConfigAuditPanel, WorkspaceConfigAuditPanel } from "./ConfigAuditPanel";

const entries: ConfigAuditEntryView[] = [
	{
		id: 3,
		occurredAt: "2026-07-24T09:14:32Z" as unknown as Date,
		action: "UPDATED",
		entityType: "AGENT_BINDING",
		entityId: "PRACTICE_DETECTION",
		actorKind: "USER",
		actorAccountId: 7,
		actor: { id: 7, displayName: "Ada Lovelace", email: "ada@example.com" },
		workspaceId: 3,
		changedKeys: ["timeoutSeconds"],
		oldValue: JSON.stringify({ timeoutSeconds: 600 }),
		newValue: JSON.stringify({ timeoutSeconds: 900 }),
	},
	{
		id: 2,
		occurredAt: "2026-07-23T14:02:00Z" as unknown as Date,
		action: "CREATED",
		entityType: "WORKSPACE_LLM_CONNECTION",
		entityId: "12",
		actorKind: "USER",
		actorAccountId: 7,
		actor: { id: 7, displayName: "Ada Lovelace", email: "ada@example.com" },
		workspaceId: 3,
		newValue: JSON.stringify({ displayName: "Acme OpenAI", enabled: false }),
	},
	{
		id: 1,
		occurredAt: "2026-07-22T06:00:00Z" as unknown as Date,
		action: "UPDATED",
		entityType: "WORKSPACE_STATUS",
		entityId: "3",
		actorKind: "SYSTEM",
		workspaceId: 3,
		changedKeys: ["status"],
		oldValue: JSON.stringify({ status: "ACTIVE" }),
		newValue: JSON.stringify({ status: "SUSPENDED" }),
	},
];

function page(content: ConfigAuditEntryView[]) {
	return {
		content,
		number: 0,
		size: 50,
		totalElements: content.length,
		totalPages: 1,
		last: true,
		first: true,
	};
}

const handlers = (content: ConfigAuditEntryView[] = entries) => [
	http.get("*/admin/config-audit", () => HttpResponse.json(page(content))),
	http.get("*/workspaces/*/config-audit", () => HttpResponse.json(page(content))),
];

const meta = {
	component: AdminConfigAuditPanel,
	parameters: { layout: "padded", msw: { handlers: handlers() } },
	tags: ["autodocs"],
	args: {
		search: {},
		onSearchChange: fn(),
		resolveWorkspaceName: (id: number) => (id === 3 ? "Acme" : undefined),
	},
} satisfies Meta<typeof AdminConfigAuditPanel>;

export default meta;
type Story = StoryObj<typeof meta>;

export const InstanceScope: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect((await canvas.findAllByText("Acme"))[0]).toBeVisible();
		await expect(await canvas.findByText("System")).toBeVisible();
	},
};

export const WorkspaceScope: StoryObj<typeof WorkspaceConfigAuditPanel> = {
	render: (args) => <WorkspaceConfigAuditPanel {...args} />,
	args: { search: {}, onSearchChange: fn(), workspaceSlug: "acme" },
};

export const Empty: Story = {
	parameters: { msw: { handlers: handlers([]) } },
};

export const FilteredToNothing: Story = {
	args: { search: { entityType: ["AGENT_BINDING"], from: "2026-07-01" } },
	parameters: { msw: { handlers: handlers([]) } },
};

export const FiltersFromUrl: Story = {
	args: { search: { action: ["CREATED"], entityType: ["WORKSPACE_LLM_CONNECTION"] } },
	parameters: { msw: { handlers: handlers([entries[1]]) } },
};

export const LoadFailed: Story = {
	parameters: {
		msw: {
			handlers: [
				http.get("*/admin/config-audit", () =>
					HttpResponse.json(
						{
							type: "about:blank",
							title: "Internal Server Error",
							status: 500,
							detail: "The audit store is unavailable.",
						},
						{ status: 500 },
					),
				),
			],
		},
	},
};
