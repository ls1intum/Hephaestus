import type { Meta, StoryObj } from "@storybook/react-vite";
import { HttpResponse, http } from "msw";
import { expect, fn, within } from "storybook/test";
import type { AuthEventView } from "@/api/types.gen";
import { AuthAuditPanel } from "./AuthAuditPanel";

const events: AuthEventView[] = [
	{
		id: 3,
		eventType: "LOGIN",
		result: "SUCCESS",
		occurredAt: "2026-07-24T09:14:32Z" as unknown as Date,
		accountId: 7,
		account: { id: 7, displayName: "Ada Lovelace", email: "ada@example.com" },
		ipAddress: "203.0.113.7",
		userAgent: "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) Chrome/140.0 Safari/537.36",
	},
	{
		id: 2,
		eventType: "LOGIN_FAILED",
		result: "FAILURE",
		failureReason: "Bad credentials",
		occurredAt: "2026-07-24T08:02:11Z" as unknown as Date,
		accountId: 7,
		account: { id: 7, displayName: "Ada Lovelace", email: "ada@example.com" },
		ipAddress: "203.0.113.7",
	},
	{
		id: 1,
		eventType: "APP_ROLE_CHANGED",
		result: "SUCCESS",
		occurredAt: "2026-07-23T17:40:00Z" as unknown as Date,
		accountId: 7,
		account: { id: 7, displayName: "Ada Lovelace", email: "ada@example.com" },
		actingAccountId: 1,
		actor: { id: 1, displayName: "Grace Hopper", email: "grace@example.com" },
		details: JSON.stringify({ from: "USER", to: "APP_ADMIN" }),
		workspaceId: 3,
	},
];

function page(content: AuthEventView[]) {
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

const handlers = (content: AuthEventView[] = events) => [
	http.get("*/admin/audit", () => HttpResponse.json(page(content))),
];

/**
 * The sign-in and account trail: toolbar, CSV export, and table. Sibling of `ConfigAuditPanel` and
 * deliberately the same shape, so the two tabs of the audit log differ in content rather than in how
 * they are operated.
 *
 * Every filter lives in the URL — the panel takes the selection in and reports changes out — because
 * an audit view exists to be cited.
 */
const meta = {
	component: AuthAuditPanel,
	parameters: { layout: "padded", msw: { handlers: handlers() } },
	tags: ["autodocs"],
	args: {
		search: { tab: "signins" },
		onSearchChange: fn(),
		resolveWorkspaceName: (id: number) => (id === 3 ? "Acme" : undefined),
	},
} satisfies Meta<typeof AuthAuditPanel>;

export default meta;
type Story = StoryObj<typeof meta>;

/** A routine sign-in, a failure, and a privilege change — each at a different severity. */
export const Default: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(await canvas.findByText("Sign-in")).toBeVisible();
		await expect(await canvas.findByText("Failed sign-in")).toBeVisible();
		await expect(await canvas.findByText("Instance role changed")).toBeVisible();
	},
};

/** Nothing recorded yet. */
export const Empty: Story = {
	parameters: { msw: { handlers: handlers([]) } },
};

/** A filter is active, so the empty result explains itself as "narrowed", not "nothing happened". */
export const FilteredToNothing: Story = {
	args: { search: { tab: "signins", outcome: ["FAILURE"], from: "2026-07-01" } },
	parameters: { msw: { handlers: handlers([]) } },
};

/** Filters restored from the URL show as active facets — a shared link arrives already narrowed. */
export const FiltersFromUrl: Story = {
	args: { search: { tab: "signins", eventType: ["LOGIN_FAILED"], outcome: ["FAILURE"] } },
	parameters: { msw: { handlers: handlers([events[1]]) } },
};

/** The list failed to load. */
export const LoadFailed: Story = {
	parameters: {
		msw: {
			handlers: [
				http.get("*/admin/audit", () =>
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
