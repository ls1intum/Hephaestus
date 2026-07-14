import { fireEvent, render, screen, within } from "@testing-library/react";
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

	it("confirms before turning message use OFF — the switch alone would silently delete collected data", () => {
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

		// The flip alone must NOT delete anything — an irreversible deletion is gated by a confirmation.
		expect(onToggleChannelMessages).not.toHaveBeenCalled();

		fireEvent.click(screen.getByRole("button", { name: /turn off & delete/i }));

		expect(onToggleChannelMessages).toHaveBeenCalledWith("hephaestustest", false);
	});

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

	it("turning message use ON is instant — it is not destructive, so it is not gated", () => {
		const onToggleChannelMessages = vi.fn();
		render(
			<SlackPreferencesSection
				workspaces={[{ ...workspace, channelMessagesAllowed: false }]}
				isSlackLinked
				canConnectSlack
				onConnectSlack={vi.fn()}
				onToggleChannelMessages={onToggleChannelMessages}
			/>,
		);

		fireEvent.click(screen.getByRole("switch", { name: /use my new channel messages/i }));

		expect(onToggleChannelMessages).toHaveBeenCalledWith("hephaestustest", true);
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
