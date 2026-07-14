import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { SlackUserWorkspacePreferences } from "@/api/types.gen";
import { SlackPreferencesSection } from "./SlackPreferencesSection";

const workspace: SlackUserWorkspacePreferences = {
	workspaceSlug: "hephaestustest",
	workspaceName: "Hephaestus Test",
	slackTeamId: "T1",
	slackTeamName: "hephaestus-test",
	slackUserId: "U1",
	slackDisplayName: "Felix",
	channelMessagesAllowed: true,
	activeMonitoredChannelCount: 2,
};

describe("SlackPreferencesSection", () => {
	it("cancelling the confirmation leaves message use ON", () => {
		const onToggleChannelMessages = vi.fn();
		render(
			<SlackPreferencesSection
				workspaces={[workspace]}
				isSlackLinked
				canConnectSlack
				onConnectSlack={vi.fn()}
				onToggleChannelMessages={onToggleChannelMessages}
			/>,
		);

		fireEvent.click(screen.getByRole("switch", { name: /use my new channel messages/i }));
		fireEvent.click(screen.getByRole("button", { name: "Cancel" }));

		expect(onToggleChannelMessages).not.toHaveBeenCalled();
		expect(screen.getByRole("switch", { name: /use my new channel messages/i })).toBeTruthy();
	});

	it("does not fake controls when Slack is linked but no workspace is available", () => {
		render(
			<SlackPreferencesSection
				workspaces={[]}
				isSlackLinked
				canConnectSlack
				onConnectSlack={vi.fn()}
				onToggleChannelMessages={vi.fn()}
			/>,
		);

		expect(screen.getByText(/Slack is connected/i)).toBeTruthy();
		expect(screen.queryByRole("switch")).toBeNull();
	});
});
