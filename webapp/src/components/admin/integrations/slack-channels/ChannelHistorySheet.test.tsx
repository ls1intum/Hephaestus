import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import type { ReactNode } from "react";
import { describe, expect, it, vi } from "vitest";
import type { SlackMonitoredChannel } from "@/api/types.gen";
import { server } from "@/mocks/server";
import { respondInTurn } from "@/test/responses";
import { ChannelHistorySheet } from "./ChannelHistorySheet";

function renderWithClient(node: ReactNode) {
	const queryClient = new QueryClient({
		defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
	});
	return render(<QueryClientProvider client={queryClient}>{node}</QueryClientProvider>);
}

const channel: SlackMonitoredChannel = {
	id: 1,
	slackTeamId: "T0000000000",
	slackChannelId: "C02ACTIVE002",
	channelName: "team-standup",
	consentState: "ACTIVE",
	optedOutMemberCount: 0,
	createdAt: new Date("2026-01-01T00:00:00Z"),
};

describe("ChannelHistorySheet — failed load offers a retry", () => {
	it("shows a Retry button that re-issues the request after a failed load", async () => {
		let requestCount = 0;
		const respond = respondInTurn(
			() => new HttpResponse(null, { status: 500 }),
			() => HttpResponse.json([]),
		);
		server.use(
			http.get("*/slack/channels/:slackChannelId/consent-events", () => {
				requestCount += 1;
				return respond();
			}),
		);

		renderWithClient(
			<ChannelHistorySheet workspaceSlug="demo" channel={channel} onOpenChange={vi.fn()} />,
		);

		await screen.findByText(/could not load the consent history/i);
		const retry = screen.getByRole("button", { name: /^retry$/i });

		fireEvent.click(retry);

		await waitFor(() => screen.getByText(/no consent changes recorded yet/i));
		expect(requestCount).toBe(2);
	});
});
