import type { Meta, StoryObj } from "@storybook/react";
import { expect, screen, userEvent, within } from "storybook/test";
import { RelativeTime } from "./RelativeTime";

const minutesAgo = (minutes: number) => new Date(Date.now() - minutes * 60_000);

/** A timestamp as "4 minutes ago", with the absolute instant one hover away. */
const meta = {
	component: RelativeTime,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	args: { value: minutesAgo(4) },
} satisfies Meta<typeof RelativeTime>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};

export const HoverRevealsAbsoluteTime: Story = {
	args: { value: new Date("2026-07-14T09:30:12Z") },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		const trigger = canvas.getByText(/ago$/);
		await userEvent.hover(trigger);
		await expect(await screen.findByText(/14 Jul 2026, /)).toBeInTheDocument();
	},
};

/** Uncoloured on purpose: only an adverse judgement is worth tinting. */
export const Fresh: Story = { args: { value: minutesAgo(4), tone: "fresh" } };

export const Stale: Story = {
	args: { value: minutesAgo(180), tone: "stale" },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/ago$/)).toHaveClass("text-warning");
	},
};

export const VeryStale: Story = {
	args: { value: minutesAgo(60 * 24 * 9), tone: "veryStale" },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/ago$/)).toHaveClass("text-destructive");
	},
};

export const UnknownCadence: Story = { args: { value: minutesAgo(600), tone: "unknown" } };

export const Never: Story = {
	args: { value: undefined },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText("–")).toBeInTheDocument();
	},
};

export const CustomFallback: Story = {
	args: { value: null, fallback: "not tracked" },
};

/**
 * The generated client does not revive dates: the wire type says `Date`, runtime hands you an ISO
 * string. Trusting the type renders "Invalid Date" in production and passes in Storybook.
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

export const WithoutTooltip: Story = {
	args: { value: minutesAgo(45), tooltip: false, tone: "stale" },
};

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
