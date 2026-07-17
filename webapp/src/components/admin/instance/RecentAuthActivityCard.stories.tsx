import type { Meta, StoryObj } from "@storybook/react";
import { expect, within } from "storybook/test";
import type { AuthEventView } from "@/api/types.gen";
import { RecentAuthActivityCard } from "./RecentAuthActivityCard";

const events: AuthEventView[] = [
	{
		id: 1,
		eventType: "LOGIN",
		result: "SUCCESS",
		occurredAt: new Date(Date.now() - 5 * 60_000),
		account: { id: 7, displayName: "Ada Lovelace" },
		accountId: 7,
	},
	{
		id: 2,
		eventType: "LOGIN_FAILED",
		result: "FAILURE",
		failureReason: "invalid_grant",
		occurredAt: new Date(Date.now() - 20 * 60_000),
		accountId: 12,
	},
	{
		id: 3,
		eventType: "IMPERSONATION_BEGIN",
		result: "SUCCESS",
		occurredAt: new Date(Date.now() - 3 * 3_600_000),
		account: { id: 7, displayName: "Ada Lovelace" },
		accountId: 7,
		actor: { id: 1, displayName: "Instance Admin" },
		actingAccountId: 1,
	},
	{
		id: 4,
		eventType: "APP_ROLE_CHANGED",
		result: "SUCCESS",
		occurredAt: new Date(Date.now() - 26 * 3_600_000),
		account: { id: 12, displayName: "Grace Hopper" },
		accountId: 12,
	},
];

const meta = {
	component: RecentAuthActivityCard,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: { events },
} satisfies Meta<typeof RecentAuthActivityCard>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		// eventLabel humanizes the raw type — proves the row renders, not just the framework.
		await expect(canvas.getByText("Login failed")).toBeInTheDocument();
	},
};

export const Empty: Story = {
	args: { events: [] },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/no activity yet/i)).toBeInTheDocument();
	},
};

export const Loading: Story = {
	args: { events: [], isLoading: true },
};
