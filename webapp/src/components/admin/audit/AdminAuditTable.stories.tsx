import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, screen, userEvent, within } from "storybook/test";
import type { AuthEventView } from "@/api/types.gen";
import { expectSettledVisible } from "@/test/overlay";
import { AdminAuditTable } from "./AdminAuditTable";

const events: AuthEventView[] = [
	{
		id: 3,
		occurredAt: new Date("2026-06-02T10:05:00Z"),
		eventType: "APP_ROLE_CHANGED",
		result: "SUCCESS",
		accountId: 42,
		actingAccountId: 7,
		account: { id: 42, displayName: "Ada Lovelace", email: "ada@example.com" },
		actor: { id: 7, displayName: "Grace Hopper", email: "grace@example.com" },
		details: '{"from":"USER","to":"APP_ADMIN"}',
		workspaceId: 12,
		ipAddress: "203.0.113.7",
		userAgent: "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) Chrome/124.0",
	},
	{
		id: 2,
		occurredAt: new Date("2026-06-02T10:00:00Z"),
		eventType: "IMPERSONATION_BEGIN",
		result: "SUCCESS",
		accountId: 42,
		actingAccountId: 7,
		account: { id: 42, displayName: "Ada Lovelace", email: "ada@example.com" },
		actor: { id: 7, displayName: "Grace Hopper", email: "grace@example.com" },
		details: '{"reason":"Investigating a failed sync reported by the student"}',
		ipAddress: "203.0.113.7",
	},
	{
		id: 1,
		occurredAt: new Date("2026-06-02T09:30:00Z"),
		eventType: "LOGIN_FAILED",
		result: "FAILURE",
		accountId: 99,
		failureReason: "Email not verified on the GitLab account",
		ipAddress: "198.51.100.4",
		userAgent: "curl/8.4.0",
	},
];

const meta = {
	component: AdminAuditTable,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
		events,
		isLoading: false,
		isError: false,
		hasFilter: false,
		hasNextPage: false,
		isFetchingNextPage: false,
		onLoadMore: () => {},
		onFilterAccount: fn(),
		onFilterActor: fn(),
	},
} satisfies Meta<typeof AdminAuditTable>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		// The table's own rendering of `result`, not `args.events` read back.
		canvas.getByText("Failure");
	},
};

export const DeletedAccountFallback: Story = {
	args: {
		events: [
			{
				id: 5,
				occurredAt: new Date("2026-06-02T11:00:00Z"),
				eventType: "ACCOUNT_DELETED",
				result: "SUCCESS",
				accountId: 1234,
				ipAddress: "203.0.113.9",
			},
		],
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		canvas.getByText("#1234");
	},
};

export const RowDetail: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		const buttons = canvas.getAllByRole("button", { name: /View details/i });
		await userEvent.click(buttons[0]);
		await expectSettledVisible(await screen.findByText("User agent"));
		screen.getByText("Workspace");
	},
};

export const EmptyInitial: Story = {
	args: { events: [], hasFilter: false },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		canvas.getByText("No events yet");
		canvas.getByText(/Sign-ins, impersonation, role changes/i);
	},
};

export const EmptyWithFilter: Story = {
	args: { events: [], hasFilter: true },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		canvas.getByText("No events match your filters");
	},
};

export const ErrorState: Story = {
	args: { events: [], isError: true },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		canvas.getByText(/Couldn’t load the audit log/i);
	},
};

export const Loading: Story = {
	args: { events: [], isLoading: true },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		canvas.getByRole("columnheader", { name: "Event" });
	},
};

export const ColumnCountMatchesHeader: Story = {
	args: {},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		const headers = canvas.getAllByRole("columnheader");
		const cells = within(canvas.getAllByRole("row")[1]).getAllByRole("cell");
		await expect(headers).toHaveLength(cells.length);
	},
};
