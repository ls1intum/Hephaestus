// Behavioral tests for the Slack channel-monitoring admin surface. The component is
// presentational: mutations are mocked callbacks, so we assert on delegated intent and on
// the two safeguards — activation is guarded by a confirm dialog, and revoke is gated behind
// a type-to-confirm AlertDialog. jest-dom matchers and user-event are NOT set up in this
// repo's vitest, so assertions use plain DOM (`.disabled`, `queryByRole`) and `fireEvent`.

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import type { ReactNode } from "react";
import { describe, expect, it, vi } from "vitest";
import type { SlackMonitoredChannel } from "@/api/types.gen";
import { AdminSlackChannelsSettings } from "./AdminSlackChannelsSettings";

function renderWithClient(node: ReactNode) {
	const queryClient = new QueryClient({
		defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
	});
	return render(<QueryClientProvider client={queryClient}>{node}</QueryClientProvider>);
}

const base = {
	slackTeamId: "T0000000000",
	createdAt: new Date("2026-01-01T00:00:00Z"),
} satisfies Pick<SlackMonitoredChannel, "slackTeamId" | "createdAt">;

const pending: SlackMonitoredChannel = {
	...base,
	id: 1,
	slackChannelId: "C01PENDING01",
	channelName: "team-intro",
	consentState: "PENDING",
	optedOutMemberCount: 0,
};

const active: SlackMonitoredChannel = {
	...base,
	id: 2,
	slackChannelId: "C02ACTIVE002",
	channelName: "team-standup",
	consentState: "ACTIVE",
	optedOutMemberCount: 2,
	consentAnnouncedAt: new Date("2026-02-01T00:00:00Z"),
};

const revoked: SlackMonitoredChannel = {
	...base,
	id: 3,
	slackChannelId: "C03REVOKED03",
	channelName: "team-legacy",
	consentState: "REVOKED",
	optedOutMemberCount: 0,
};

function setup(overrides: Partial<Parameters<typeof AdminSlackChannelsSettings>[0]> = {}) {
	const props = {
		workspaceSlug: "demo",
		hasSlackConnection: true,
		isLoading: false,
		channels: [pending, active],
		onRegisterChannel: vi.fn(),
		onUpdateConsent: vi.fn(),
		onRemoveChannel: vi.fn(),
		...overrides,
	};
	renderWithClient(<AdminSlackChannelsSettings {...props} />);
	return { props };
}

function openRowMenu(label: string) {
	fireEvent.click(screen.getByRole("button", { name: new RegExp(`actions for ${label}`, "i") }));
}

describe("AdminSlackChannelsSettings — state-gated row actions", () => {
	it("exposes Activate (not Pause) on a PENDING channel", async () => {
		setup({ channels: [pending] });
		openRowMenu("team-intro");

		expect(await screen.findByRole("menuitem", { name: /activate monitoring/i })).toBeTruthy();
		expect(screen.queryByRole("menuitem", { name: /^pause$/i })).toBeNull();
	});

	it("exposes Pause (not Activate) on an ACTIVE channel", async () => {
		setup({ channels: [active] });
		openRowMenu("team-standup");

		expect(await screen.findByRole("menuitem", { name: /^pause$/i })).toBeTruthy();
		expect(screen.queryByRole("menuitem", { name: /activate monitoring/i })).toBeNull();
	});

	it("offers no lifecycle or erase actions on a terminal REVOKED channel", async () => {
		setup({ channels: [revoked] });
		openRowMenu("team-legacy");

		expect(await screen.findByRole("menuitem", { name: /view history/i })).toBeTruthy();
		expect(screen.queryByRole("menuitem", { name: /activate monitoring/i })).toBeNull();
		expect(screen.queryByRole("menuitem", { name: /^pause$/i })).toBeNull();
		expect(screen.queryByRole("menuitem", { name: /remove & erase/i })).toBeNull();
	});
});

describe("AdminSlackChannelsSettings — activation is guarded by a confirm dialog", () => {
	it("does not transition until the consequences dialog is confirmed", async () => {
		const { props } = setup({ channels: [pending] });
		openRowMenu("team-intro");
		fireEvent.click(await screen.findByRole("menuitem", { name: /activate monitoring/i }));

		// Dialog is open but nothing has been mutated yet.
		const dialog = await screen.findByRole("dialog");
		expect(props.onUpdateConsent).not.toHaveBeenCalled();

		fireEvent.click(within(dialog).getByRole("button", { name: /^activate monitoring$/i }));
		await waitFor(() => expect(props.onUpdateConsent).toHaveBeenCalledTimes(1));
		expect(props.onUpdateConsent).toHaveBeenCalledWith({
			slackChannelId: pending.slackChannelId,
			consentState: "ACTIVE",
		});
	});
});

describe("AdminSlackChannelsSettings — revoke type-to-confirm", () => {
	it("keeps the destructive action disabled until the channel name is typed, then reports the reason", async () => {
		const { props } = setup({ channels: [active] });
		openRowMenu("team-standup");
		fireEvent.click(await screen.findByRole("menuitem", { name: /remove & erase/i }));

		const dialog = await screen.findByRole("alertdialog");
		const confirm = within(dialog).getByRole("button", {
			name: /remove & erase/i,
		}) as HTMLButtonElement;
		expect(confirm.disabled).toBe(true);

		fireEvent.change(within(dialog).getByLabelText(/to confirm/i), {
			target: { value: "team-standup" },
		});
		expect(confirm.disabled).toBe(false);

		fireEvent.change(within(dialog).getByLabelText(/reason/i), {
			target: { value: "left the course" },
		});
		fireEvent.click(confirm);

		await waitFor(() => expect(props.onRemoveChannel).toHaveBeenCalledTimes(1));
		expect(props.onRemoveChannel).toHaveBeenCalledWith({
			slackChannelId: active.slackChannelId,
			reason: "left the course",
		});
	});
});

describe("AdminSlackChannelsSettings — add-channel gating & empty state", () => {
	it("disables Add channel when Slack is not connected", () => {
		setup({ hasSlackConnection: false });
		const addButton = screen.getByRole("button", { name: /add channel/i }) as HTMLButtonElement;
		expect(addButton.disabled).toBe(true);
	});

	it("renders an empty state with an Add affordance when there are no channels", () => {
		setup({ channels: [] });
		expect(screen.getByText(/no channels monitored yet/i)).toBeTruthy();
		// Both the header button and the empty-state CTA are labelled "Add channel".
		expect(screen.getAllByRole("button", { name: /add channel/i }).length).toBeGreaterThan(1);
	});
});

describe("AdminSlackChannelsSettings — opted-out signal", () => {
	it("shows the opt-out count for a channel with opt-outs and a muted 0 otherwise", () => {
		setup({ channels: [pending, active] });
		// active has 2 opt-outs; pending has 0 rendered as a visible trust signal.
		// Scope each assertion to its own row (via the deterministic action-button label) so a
		// stray "2"/"0" rendered elsewhere in the table can't satisfy a bare getByText.
		const activeRow = screen
			.getByRole("button", { name: "Actions for team-standup" })
			.closest("tr");
		const pendingRow = screen.getByRole("button", { name: "Actions for team-intro" }).closest("tr");
		expect(activeRow).not.toBeNull();
		expect(pendingRow).not.toBeNull();
		expect(within(activeRow as HTMLElement).getByText("2")).toBeTruthy();
		expect(within(pendingRow as HTMLElement).getByText("0")).toBeTruthy();
	});
});
