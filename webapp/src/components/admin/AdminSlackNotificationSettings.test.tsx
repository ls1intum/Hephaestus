import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen } from "@testing-library/react";
import type { ReactNode } from "react";
import { describe, expect, it, vi } from "vitest";
import { AdminSlackNotificationSettings } from "./AdminSlackNotificationSettings";

function renderWithClient(node: ReactNode) {
	const queryClient = new QueryClient({
		defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
	});
	return render(<QueryClientProvider client={queryClient}>{node}</QueryClientProvider>);
}

describe("AdminSlackNotificationSettings — digest channel picker", () => {
	it("fills the digest channel from Slack-discovered channels", () => {
		renderWithClient(
			<AdminSlackNotificationSettings
				workspaceSlug="demo"
				hasSlackConnection
				slackConnectionId={1}
				enabled={false}
				channelCandidates={[
					{
						slackChannelId: "C01GENERAL01",
						channelName: "general",
						privateChannel: false,
						member: true,
						archived: false,
					},
				]}
				onSaved={vi.fn()}
			/>,
		);

		fireEvent.click(screen.getByRole("option", { name: /#general/i }));

		const input = screen.getByLabelText(/digest channel/i) as HTMLInputElement;
		expect(input.value).toBe("C01GENERAL01");
	});

	it("requires a channel before enabling the digest", () => {
		renderWithClient(
			<AdminSlackNotificationSettings
				workspaceSlug="demo"
				hasSlackConnection
				slackConnectionId={1}
				enabled
				onSaved={vi.fn()}
			/>,
		);

		expect(screen.getByText(/choose a channel before enabling/i)).toBeTruthy();
		expect((screen.getByRole("button", { name: /^save$/i }) as HTMLButtonElement).disabled).toBe(
			true,
		);
	});

	it("does not let admins pick a digest channel before the bot is a member", () => {
		renderWithClient(
			<AdminSlackNotificationSettings
				workspaceSlug="demo"
				hasSlackConnection
				slackConnectionId={1}
				enabled={false}
				channelCandidates={[
					{
						slackChannelId: "C02PRIVATE02",
						channelName: "private-team",
						privateChannel: true,
						member: false,
						archived: false,
					},
				]}
				onSaved={vi.fn()}
			/>,
		);

		const privateChannel = screen.getByRole("option", { name: /#private-team/i });
		expect(privateChannel.getAttribute("aria-disabled")).toBe("true");
		expect(screen.getByText(/needs invite/i)).toBeTruthy();
	});

	it("filters the channel picker via the search input", () => {
		renderWithClient(
			<AdminSlackNotificationSettings
				workspaceSlug="demo"
				hasSlackConnection
				slackConnectionId={1}
				enabled={false}
				channelCandidates={[
					{
						slackChannelId: "C01GENERAL01",
						channelName: "general",
						privateChannel: false,
						member: true,
						archived: false,
					},
					{
						slackChannelId: "C02STANDUP2",
						channelName: "team-standup",
						privateChannel: false,
						member: true,
						archived: false,
					},
				]}
				onSaved={vi.fn()}
			/>,
		);

		expect(screen.getByRole("option", { name: /#general/i })).toBeTruthy();
		expect(screen.getByRole("option", { name: /#team-standup/i })).toBeTruthy();

		fireEvent.change(screen.getByRole("combobox", { name: /search digest slack channels/i }), {
			target: { value: "standup" },
		});

		expect(screen.queryByRole("option", { name: /#general/i })).toBeNull();
		expect(screen.getByRole("option", { name: /#team-standup/i })).toBeTruthy();
	});
});
