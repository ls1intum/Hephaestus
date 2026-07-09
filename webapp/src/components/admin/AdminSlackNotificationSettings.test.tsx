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

		fireEvent.click(screen.getByRole("button", { name: /#general/i }));

		const input = screen.getByLabelText(/digest channel/i) as HTMLInputElement;
		expect(input.value).toBe("C01GENERAL01");
		expect(screen.getByRole("button", { name: /#general/i }).getAttribute("aria-pressed")).toBe(
			"true",
		);
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

		const privateChannel = screen.getByRole("button", {
			name: /#private-team/i,
		}) as HTMLButtonElement;
		expect(privateChannel.disabled).toBe(true);
	});
});
