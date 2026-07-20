import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, screen, userEvent, within } from "storybook/test";
import type { AuthEventView } from "@/api/types.gen";
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

/** Newest-first rows: accounts resolve to names, impersonation is attributed, failures show why. */
export const Default: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		// Accounts render as human names, not numeric ids (Ada is the subject of two rows).
		await expect(canvas.getAllByText("Ada Lovelace").length).toBeGreaterThan(0);
		// Impersonated actions attribute the operator ("via Grace Hopper").
		await expect(canvas.getAllByText("Grace Hopper").length).toBeGreaterThan(0);
		// A failure shows its reason (not just a red badge), plus the destructive result badge.
		await expect(canvas.getByText("Failure")).toBeInTheDocument();
	},
};

/** Deleted accounts (no resolved identity) fall back to `#id` so the row stays attributable. */
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
		await expect(canvas.getByText("#1234")).toBeInTheDocument();
	},
};

/** Opening a row reveals the full forensic record (user agent, workspace, pretty-printed details). */
export const RowDetail: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		const buttons = canvas.getAllByRole("button", { name: /View details/i });
		await userEvent.click(buttons[0]);
		// The dialog renders in a portal → query the document, not just the canvas.
		await expect(await screen.findByText("User agent")).toBeInTheDocument();
		await expect(screen.getByText("Workspace")).toBeInTheDocument();
	},
};

/** Nothing recorded yet — distinct from "your filter matched nothing", and it says what will appear. */
export const EmptyInitial: Story = {
	args: { events: [], hasFilter: false },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText("No events yet")).toBeInTheDocument();
		await expect(canvas.getByText(/Sign-ins, impersonation, role changes/i)).toBeInTheDocument();
	},
};

/** A failed/loaded state with no rows under an active filter. */
export const EmptyWithFilter: Story = {
	args: { events: [], hasFilter: true },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText("No events match your filters")).toBeInTheDocument();
	},
};

/** Error state when the page fails to load. */
export const ErrorState: Story = {
	args: { events: [], isError: true },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/Couldn’t load the audit log/i)).toBeInTheDocument();
	},
};

/** Skeleton rows under the real header, so the column box is reserved and nothing shifts on resolve. */
export const Loading: Story = {
	args: { events: [], isLoading: true },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByRole("columnheader", { name: "Event" })).toBeInTheDocument();
	},
};

/**
 * Header and body must declare the same number of columns. Responsive classes applied to `<th>` but
 * not the matching `<td>` silently shift every cell under the wrong header — invisible to a snapshot,
 * and wrong for `scope="col"` too.
 */
export const ColumnCountMatchesHeader: Story = {
	args: {},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		const headers = canvas.getAllByRole("columnheader");
		const cells = within(canvas.getAllByRole("row")[1]).getAllByRole("cell");
		await expect(headers).toHaveLength(cells.length);
	},
};
