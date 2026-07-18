import type { Meta, StoryObj } from "@storybook/react";
import { delay, HttpResponse, http } from "msw";
import { expect, fn, screen, within } from "storybook/test";
import type { SlackChannelConsentEvent, SlackMonitoredChannel } from "@/api/types.gen";
import { ChannelHistorySheet } from "./ChannelHistorySheet";

const CONSENT_EVENTS_URL = "*/slack/channels/:slackChannelId/consent-events";

const iso = (days: number) => new Date(Date.now() - days * 24 * 60 * 60 * 1000);

const channel: SlackMonitoredChannel = {
	id: 1,
	slackTeamId: "T0000000000",
	slackChannelId: "C02ACTIVE002",
	channelName: "team-standup",
	consentState: "ACTIVE",
	optedOutMemberCount: 0,
	consentAnnouncedAt: iso(3),
	createdAt: iso(9),
};

const events: SlackChannelConsentEvent[] = [
	{
		id: 4,
		slackChannelId: channel.slackChannelId,
		toState: "ACTIVE",
		fromState: "PAUSED",
		createdAt: iso(1),
		reason: "Course resumed",
	},
	{
		id: 3,
		slackChannelId: channel.slackChannelId,
		toState: "PAUSED",
		fromState: "ACTIVE",
		createdAt: iso(4),
		reason: "Exam week",
	},
	{
		id: 2,
		slackChannelId: channel.slackChannelId,
		toState: "ACTIVE",
		fromState: "PENDING",
		createdAt: iso(7),
	},
	{ id: 1, slackChannelId: channel.slackChannelId, toState: "PENDING", createdAt: iso(9) },
];

/**
 * The per-channel consent audit trail. Transitions are rendered as badge → badge through the
 * shared consent vocabulary, never as the wire enum. The query is lazy: it only runs while the
 * sheet is open, so listing channels never fans out N history requests.
 *
 * The sheet renders in a portal, so the plays query the document rather than the story canvas.
 */
const meta = {
	component: ChannelHistorySheet,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	args: {
		workspaceSlug: "demo-workspace",
		channel,
		onOpenChange: fn(),
	},
} satisfies Meta<typeof ChannelHistorySheet>;

export default meta;
type Story = StoryObj<typeof meta>;

/** The full audit trail, newest first — each entry showing where the channel came from. */
export const Populated: Story = {
	parameters: {
		msw: { handlers: [http.get(CONSENT_EVENTS_URL, () => HttpResponse.json(events))] },
	},
	play: async () => {
		const sheet = within(await screen.findByRole("dialog"));
		// The wire enum must never leak; a state can appear twice (left, then entered).
		await expect((await sheet.findAllByText("Monitoring")).length).toBeGreaterThan(0);
		await expect(sheet.getAllByText("Not started").length).toBeGreaterThan(0);
		await expect(sheet.getByText("Exam week")).toBeInTheDocument();
		await expect(sheet.queryByText("ACTIVE")).not.toBeInTheDocument();
		await expect(sheet.queryByText("PENDING")).not.toBeInTheDocument();
	},
};

/** Nothing recorded yet — the Empty primitive, not a bare sentence. */
export const EmptyHistory: Story = {
	parameters: {
		msw: { handlers: [http.get(CONSENT_EVENTS_URL, () => HttpResponse.json([]))] },
	},
	play: async () => {
		const sheet = within(await screen.findByRole("dialog"));
		await expect(await sheet.findByText(/no consent changes recorded yet/i)).toBeInTheDocument();
	},
};

/** In flight — skeleton rows hold the shape of the trail. */
export const Loading: Story = {
	parameters: {
		msw: {
			handlers: [
				http.get(CONSENT_EVENTS_URL, async () => {
					await delay("infinite");
					return HttpResponse.json([]);
				}),
			],
		},
	},
};

/** The audit-trail request failed — the shared error alert, with a retry. */
export const LoadError: Story = {
	parameters: {
		msw: {
			handlers: [http.get(CONSENT_EVENTS_URL, () => new HttpResponse(null, { status: 500 }))],
		},
	},
	play: async () => {
		const sheet = within(await screen.findByRole("dialog"));
		await expect(await sheet.findByText(/could not load the consent history/i)).toBeInTheDocument();
		await expect(sheet.getByRole("button", { name: /^retry$/i })).toBeInTheDocument();
	},
};

/** Closed — the sheet renders nothing and the lazy query never fires. */
export const Closed: Story = {
	args: { channel: null },
	play: async () => {
		await expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
	},
};
