import type { Meta, StoryObj } from "@storybook/react";
import { expect, screen, userEvent, within } from "storybook/test";
import { RelativeTime } from "./RelativeTime";

/** Fixtures are relative to render time — a story about freshness cannot be pinned to a date in 2026. */
const minutesAgo = (minutes: number) => new Date(Date.now() - minutes * 60_000);

/**
 * A timestamp as "4 minutes ago", against a clock shared by every relative time on the page, with the
 * absolute instant one hover away. The relative phrase re-renders so a stale reading can't linger; the
 * absolute time stays available for correlating a failed run against a server log.
 */
const meta = {
	component: RelativeTime,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	args: { value: minutesAgo(4) },
} satisfies Meta<typeof RelativeTime>;

export default meta;
type Story = StoryObj<typeof meta>;

/** No cadence to judge against, so the time is printed and left uncoloured. */
export const Default: Story = {};

/**
 * The absolute timestamp is always one hover — or one Tab — away. The trigger is the tooltip
 * primitive's native button, so the instant stays keyboard-reachable and not mouse-only.
 */
export const HoverRevealsAbsoluteTime: Story = {
	args: { value: new Date("2026-07-14T09:30:12Z") },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		const trigger = canvas.getByText(/ago$/);
		await userEvent.hover(trigger);
		await expect(await screen.findByText(/14 Jul 2026, /)).toBeInTheDocument();
	},
};

/** Within cadence — a fresh reading earns no colour, because only a judgement is worth tinting. */
export const Fresh: Story = { args: { value: minutesAgo(4), tone: "fresh" } };

/** Past twice the cadence: a run was actually missed. Exactly the rows the server counts as stale. */
export const Stale: Story = {
	args: { value: minutesAgo(180), tone: "stale" },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/ago$/)).toHaveClass("text-warning");
	},
};

/** Six cadences overdue — the most severe freshness tone. */
export const VeryStale: Story = {
	args: { value: minutesAgo(60 * 24 * 9), tone: "veryStale" },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/ago$/)).toHaveClass("text-destructive");
	},
};

/** There is a timestamp but no known cadence, so no judgement is possible — and none is implied. */
export const UnknownCadence: Story = { args: { value: minutesAgo(600), tone: "unknown" } };

/** A missing timestamp renders the fallback dash — never "now", which would read as fresh. */
export const Never: Story = {
	args: { value: undefined },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText("–")).toBeInTheDocument();
	},
};

/** The fallback is copy, so a caller with room for a sentence can say what the absence means. */
export const CustomFallback: Story = {
	args: { value: null, fallback: "not tracked" },
};

/**
 * The wire types say `Date`, but `lastSyncedAt` and friends arrive as ISO **strings** at runtime — the
 * generated client does not revive them. Both are accepted and both format identically; a component
 * that trusted the type here would render "Invalid Date" in production and pass in Storybook.
 */
export const WireString: Story = {
	args: { value: "2026-07-14T09:30:12Z" },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.queryByText(/invalid date/i)).not.toBeInTheDocument();
	},
};

/** An unparseable value is an absent one, not a fresh one. */
export const InvalidValue: Story = { args: { value: "not-a-date" } };

/** Inside a hover surface that already states the absolute time, the tooltip would be a second popup. */
export const WithoutTooltip: Story = {
	args: { value: minutesAgo(45), tooltip: false, tone: "stale" },
};

/** Every tone at once, so a palette regression is visible at a glance. */
export const AllTones: Story = {
	render: () => (
		<dl className="grid grid-cols-[8rem_1fr] gap-x-6 gap-y-2 text-sm">
			<dt className="text-muted-foreground">fresh</dt>
			<dd>
				<RelativeTime value={minutesAgo(3)} tone="fresh" />
			</dd>
			<dt className="text-muted-foreground">stale</dt>
			<dd>
				<RelativeTime value={minutesAgo(200)} tone="stale" />
			</dd>
			<dt className="text-muted-foreground">veryStale</dt>
			<dd>
				<RelativeTime value={minutesAgo(60 * 24 * 12)} tone="veryStale" />
			</dd>
			<dt className="text-muted-foreground">unknown</dt>
			<dd>
				<RelativeTime value={minutesAgo(90)} tone="unknown" />
			</dd>
			<dt className="text-muted-foreground">never</dt>
			<dd>
				<RelativeTime value={undefined} tone="never" fallback="Never synced" />
			</dd>
		</dl>
	),
};
