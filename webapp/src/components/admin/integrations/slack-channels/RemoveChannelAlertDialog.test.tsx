import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { SlackMonitoredChannel } from "@/api/types.gen";
import { RemoveChannelAlertDialog } from "./RemoveChannelAlertDialog";

const base = {
	slackTeamId: "T0000000000",
	createdAt: new Date("2026-01-01T00:00:00Z"),
} satisfies Pick<SlackMonitoredChannel, "slackTeamId" | "createdAt">;

const active: SlackMonitoredChannel = {
	...base,
	id: 1,
	slackChannelId: "C02ACTIVE002",
	channelName: "team-standup",
	consentState: "ACTIVE",
	optedOutMemberCount: 0,
};

describe("RemoveChannelAlertDialog — confirm mismatch is reported, not silently disabled", () => {
	it("keeps the destructive action enabled and marks the field invalid when the ID is wrong", async () => {
		const onConfirm = vi.fn();
		render(
			<RemoveChannelAlertDialog channel={active} onOpenChange={vi.fn()} onConfirm={onConfirm} />,
		);

		const confirm = screen.getByRole("button", { name: /remove & erase/i }) as HTMLButtonElement;
		expect(confirm.disabled).toBe(false);

		const input = screen.getByLabelText(/to confirm/i);
		fireEvent.change(input, { target: { value: "C0-WRONG-ID" } });
		fireEvent.click(confirm);

		expect(onConfirm).not.toHaveBeenCalled();
		expect(input.getAttribute("aria-invalid")).toBe("true");
		expect(screen.getByText(/that does not match/i)).toBeTruthy();

		// Correcting the value clears the error and lets the erase through.
		fireEvent.change(input, { target: { value: active.slackChannelId } });
		expect(input.getAttribute("aria-invalid")).toBe("false");
		fireEvent.click(confirm);
		await waitFor(() =>
			expect(onConfirm).toHaveBeenCalledWith({
				slackChannelId: active.slackChannelId,
				reason: undefined,
			}),
		);
	});
});

describe("RemoveChannelAlertDialog — resets on close", () => {
	it("clears a typed confirmation and reason after Cancel, ready for the next open", () => {
		const onOpenChange = vi.fn();
		render(
			<RemoveChannelAlertDialog channel={active} onOpenChange={onOpenChange} onConfirm={vi.fn()} />,
		);

		fireEvent.change(screen.getByLabelText(/to confirm/i), {
			target: { value: active.slackChannelId },
		});
		fireEvent.change(screen.getByLabelText(/reason/i), { target: { value: "testing" } });

		fireEvent.click(screen.getByRole("button", { name: /^cancel$/i }));
		expect(onOpenChange).toHaveBeenCalledWith(false);

		// Same instance persists (no `key`-driven remount) — the fields reset synchronously.
		expect((screen.getByLabelText(/to confirm/i) as HTMLInputElement).value).toBe("");
		expect((screen.getByLabelText(/reason/i) as HTMLTextAreaElement).value).toBe("");
	});
});
