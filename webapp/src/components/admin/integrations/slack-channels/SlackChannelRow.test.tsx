import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { SlackMonitoredChannel } from "@/api/types.gen";
import { Table, TableBody } from "@/components/ui/table";
import { SlackChannelRow } from "./SlackChannelRow";

const channel: SlackMonitoredChannel = {
	id: 1,
	slackTeamId: "T0000000000",
	slackChannelId: "C01ACTIVE01",
	channelName: "team-standup",
	consentState: "ACTIVE",
	optedOutMemberCount: 3,
	consentAnnouncedAt: new Date("2026-02-01T00:00:00Z"),
	createdAt: new Date("2026-01-01T00:00:00Z"),
};

function renderRow(overrides: Partial<SlackMonitoredChannel> = {}) {
	return render(
		<Table>
			<TableBody>
				<SlackChannelRow
					channel={{ ...channel, ...overrides }}
					onActivate={vi.fn()}
					onPause={vi.fn()}
					onResume={vi.fn()}
					onRemove={vi.fn()}
					onSetUpAgain={vi.fn()}
					onViewHistory={vi.fn()}
				/>
			</TableBody>
		</Table>,
	);
}

describe("SlackChannelRow — opted-out tooltip is keyboard-reachable", () => {
	it("renders the opted-out count trigger as a real, focusable button", () => {
		renderRow();

		// A bare non-interactive <span> trigger has no accessible role for keyboard/SR users;
		// the trigger must be a real button so Tab can reach it.
		const trigger = screen.getByRole("button", { name: "3" });
		expect(trigger.tagName).toBe("BUTTON");
		expect(trigger.hasAttribute("disabled")).toBe(false);
	});
});
