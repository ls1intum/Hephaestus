import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen } from "@testing-library/react";
import type { ReactNode } from "react";
import { describe, expect, it, vi } from "vitest";
import type { SlackChannelCandidate } from "@/api/types.gen";
import { AdminSlackNotificationSettings } from "./AdminSlackNotificationSettings";

function renderWithClient(node: ReactNode) {
	const queryClient = new QueryClient({
		defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
	});
	return render(<QueryClientProvider client={queryClient}>{node}</QueryClientProvider>);
}

const general: SlackChannelCandidate = {
	slackChannelId: "C01GENERAL01",
	channelName: "general",
	privateChannel: false,
	member: true,
	archived: false,
};

const privateTeam: SlackChannelCandidate = {
	slackChannelId: "C02PRIVATE02",
	channelName: "private-team",
	privateChannel: true,
	member: false,
	archived: false,
};

const standup: SlackChannelCandidate = {
	slackChannelId: "C02STANDUP2",
	channelName: "team-standup",
	privateChannel: false,
	member: true,
	archived: false,
};

function setup(candidates: SlackChannelCandidate[] = [], enabled = false) {
	renderWithClient(
		<AdminSlackNotificationSettings
			workspaceSlug="demo"
			hasSlackConnection
			slackConnectionId={1}
			enabled={enabled}
			channelCandidates={candidates}
			onSaved={vi.fn()}
		/>,
	);
}

/** The combobox keeps its options in a popover — open it before querying them. */
function openChannelCombobox() {
	fireEvent.click(screen.getByRole("combobox", { name: /digest channel/i }));
}

describe("AdminSlackNotificationSettings — digest channel combobox", () => {
	it("selects a Slack-discovered channel and never exposes the raw id as an editable value", () => {
		setup([general]);
		openChannelCombobox();

		fireEvent.click(screen.getByRole("option", { name: /#general/i }));

		// The trigger names the channel the way a human does…
		expect(screen.getByRole("combobox", { name: /digest channel/i }).textContent).toContain(
			"#general",
		);
		// …and the stable id stays in state: no text box carries it as its value.
		expect(screen.queryByDisplayValue("C01GENERAL01")).toBeNull();
	});

	it("requires a channel before enabling the digest", () => {
		setup([], true);

		expect(screen.getByText(/choose a channel before enabling/i)).toBeTruthy();
		expect((screen.getByRole("button", { name: /^save$/i }) as HTMLButtonElement).disabled).toBe(
			true,
		);
	});

	it("does not let admins pick a digest channel before the bot is a member", () => {
		setup([privateTeam]);
		openChannelCombobox();

		expect(
			screen.getByRole("option", { name: /#private-team/i }).getAttribute("aria-disabled"),
		).toBe("true");
		expect(screen.getByText(/needs invite/i)).toBeTruthy();
	});

	it("filters the channel list via the search input", () => {
		setup([general, standup]);
		openChannelCombobox();

		expect(screen.getByRole("option", { name: /#general/i })).toBeTruthy();
		expect(screen.getByRole("option", { name: /#team-standup/i })).toBeTruthy();

		fireEvent.change(screen.getByRole("combobox", { name: /search digest slack channels/i }), {
			target: { value: "standup" },
		});

		expect(screen.queryByRole("option", { name: /#general/i })).toBeNull();
		expect(screen.getByRole("option", { name: /#team-standup/i })).toBeTruthy();
	});

	it("resolves a pasted channel link through the escape hatch into the same single value", () => {
		setup([general]);

		// The paste path is secondary — it opens on demand and writes the value the combobox
		// writes, which is the one the Send-test button reads.
		fireEvent.click(screen.getByRole("button", { name: /paste a channel link or id instead/i }));
		fireEvent.change(screen.getByLabelText(/paste a channel link or id/i), {
			target: { value: "https://acme.slack.com/archives/C0974LJBPBK" },
		});

		expect(
			(screen.getByRole("button", { name: /send test message/i }) as HTMLButtonElement).disabled,
		).toBe(false);
	});
});
