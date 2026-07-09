import { fireEvent, render, screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { SlackWorkspacePreferences } from "@/api/types.gen";
import { SlackPreferencesSection } from "./SlackPreferencesSection";

const workspace: SlackWorkspacePreferences = {
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
	it("lets an unlinked user start Slack account linking", () => {
		const onConnectSlack = vi.fn();
		render(
			<SlackPreferencesSection
				workspaces={[]}
				isSlackLinked={false}
				canConnectSlack
				onConnectSlack={onConnectSlack}
				onToggleChannelMessages={vi.fn()}
			/>,
		);

		fireEvent.click(screen.getByRole("button", { name: "Connect Slack" }));

		expect(onConnectSlack).toHaveBeenCalledOnce();
	});

	it("shows workspace message-use controls for linked Slack workspaces", () => {
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

		const row = screen.getByRole("group", { name: "Hephaestus Test Slack preferences" });
		expect(within(row).getByText("2 active monitored channels")).toBeTruthy();

		fireEvent.click(within(row).getByRole("switch", { name: /use my new channel messages/i }));

		expect(onToggleChannelMessages).toHaveBeenCalledWith("hephaestustest", false);
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
