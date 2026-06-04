import type { Meta, StoryObj } from "@storybook/react";
import { expect, within } from "storybook/test";
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
		details: '{"from":"USER","to":"APP_ADMIN"}',
		ipAddress: "203.0.113.7",
	},
	{
		id: 2,
		occurredAt: new Date("2026-06-02T10:00:00Z"),
		eventType: "IMPERSONATION_BEGIN",
		result: "SUCCESS",
		accountId: 42,
		actingAccountId: 7,
		ipAddress: "203.0.113.7",
	},
	{
		id: 1,
		occurredAt: new Date("2026-06-02T09:30:00Z"),
		eventType: "LOGIN_FAILED",
		result: "FAILURE",
		accountId: 99,
		ipAddress: "198.51.100.4",
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
	},
} satisfies Meta<typeof AdminAuditTable>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Newest-first rows; impersonated actions show the operator in the Actor column. */
export const Default: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText("APP_ROLE_CHANGED")).toBeInTheDocument();
		// The impersonation event attributes the operator (act claim).
		await expect(canvas.getAllByText(/via #7/).length).toBeGreaterThan(0);
		// A failure renders with the destructive result badge.
		await expect(canvas.getByText("FAILURE")).toBeInTheDocument();
	},
};

/** A failed/loaded state with no rows under an active filter. */
export const EmptyWithFilter: Story = {
	args: { events: [], hasFilter: true },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText("No matching audit events.")).toBeInTheDocument();
	},
};

/** Loading skeleton (spinner) before the first page resolves. */
export const Loading: Story = {
	args: { events: [], isLoading: true },
};
