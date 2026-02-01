import type { Meta, StoryObj } from "@storybook/react";
import { useState } from "react";
import { fn } from "storybook/test";
import { DEFAULT_SCHEDULE, formatDateRangeForApi, getDateRangeForPreset } from "@/lib/timeframe";
import { TimeframeFilter } from "./TimeframeFilter";

// Calculate default dates using the shared timeframe utilities
const defaultRange = getDateRangeForPreset("this-week", DEFAULT_SCHEDULE);
const { after: defaultAfter, before: defaultBefore } = formatDateRangeForApi(defaultRange);

/**
 * TimeframeFilter is the timeframe selector for the leaderboard page.
 * It provides preset options (this week, last week, etc.) and custom date range selection.
 *
 * Key features:
 * - Icons for each preset type for quick visual recognition
 * - Detailed labels showing actual date ranges (e.g., "This week, since Tue Dec 3")
 * - Supports leaderboard schedule awareness for correct week boundaries
 * - Stacked layout for sidebar placement
 */
const meta = {
	component: TimeframeFilter,
	tags: ["autodocs"],
	parameters: {
		layout: "centered",
	},
	argTypes: {
		onTimeframeChange: {
			description:
				"Callback fired when timeframe selection changes. Receives (afterDate, beforeDate?, timeframe?)",
			action: "timeframe changed",
		},
		initialAfterDate: {
			description: "Initial start date in ISO format",
			control: "text",
		},
		initialBeforeDate: {
			description: "Initial end date in ISO format",
			control: "text",
		},
		leaderboardSchedule: {
			description: "Schedule information for leaderboard updates",
			control: "object",
		},
		openEndedPresets: {
			description: 'When true, presets like "this week" emit only afterDate (open-ended)',
			control: "boolean",
		},
		enableAllActivityOption: {
			description: 'Enable an "All activity" option that covers full history',
			control: "boolean",
		},
	},
	args: {
		onTimeframeChange: fn(),
	},
} satisfies Meta<typeof TimeframeFilter>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default view without any schedule or initial date range.
 * Shows the standard timeframe selection options.
 */
export const Default: Story = {
	args: {},
};

/**
 * Shows timeframe filter with leaderboard schedule information.
 * The schedule affects how "this week" and "last week" are calculated.
 */
export const WithSchedule: Story = {
	args: {
		leaderboardSchedule: {
			day: 1, // Monday
			hour: 9,
			minute: 0,
			formatted: "Mondays at 09:00",
		},
	},
};

/**
 * Custom schedule starting on Friday - for teams with end-of-week reviews.
 */
export const FridaySchedule: Story = {
	args: {
		leaderboardSchedule: {
			day: 5, // Friday
			hour: 16,
			minute: 30,
			formatted: "Fridays at 16:30",
		},
	},
};

/**
 * Tuesday schedule at 9 AM - common configuration.
 */
export const TuesdaySchedule: Story = {
	args: {
		leaderboardSchedule: {
			day: 2, // Tuesday
			hour: 9,
			minute: 0,
			formatted: "Tuesdays at 09:00",
		},
	},
};

/**
 * With initial dates pre-selected for "This week".
 */
export const ThisWeekSelected: Story = {
	args: {
		initialAfterDate: defaultAfter,
		initialBeforeDate: defaultBefore,
		leaderboardSchedule: {
			day: 1,
			hour: 9,
			minute: 0,
			formatted: "Mondays at 09:00",
		},
	},
};

/**
 * With "Last week" dates pre-selected.
 */
export const LastWeekSelected: Story = {
	args: (() => {
		const range = getDateRangeForPreset("last-week", DEFAULT_SCHEDULE);
		const { after, before } = formatDateRangeForApi(range);
		return {
			initialAfterDate: after,
			initialBeforeDate: before,
			leaderboardSchedule: {
				day: 1,
				hour: 9,
				minute: 0,
				formatted: "Mondays at 09:00",
			},
		};
	})(),
};

/**
 * With "All activity" option enabled.
 * Useful for viewing complete history.
 */
export const WithAllActivityOption: Story = {
	args: {
		enableAllActivityOption: true,
		leaderboardSchedule: {
			day: 1,
			hour: 9,
			minute: 0,
			formatted: "Mondays at 09:00",
		},
	},
};

/**
 * With open-ended presets mode.
 * "This week" and "this month" will only emit afterDate without beforeDate.
 */
export const OpenEndedMode: Story = {
	args: {
		openEndedPresets: true,
		leaderboardSchedule: {
			day: 1,
			hour: 9,
			minute: 0,
			formatted: "Mondays at 09:00",
		},
	},
};

/**
 * Interactive stateful story to test the component behavior.
 */
export const Interactive: Story = {
	render: (props) => {
		const [afterDate, setAfterDate] = useState<string | undefined>(props.initialAfterDate);
		const [beforeDate, setBeforeDate] = useState<string | undefined>(props.initialBeforeDate);
		const [timeframe, setTimeframe] = useState<string | undefined>();

		return (
			<div className="flex flex-col gap-4 w-64">
				<TimeframeFilter
					{...props}
					initialAfterDate={afterDate}
					initialBeforeDate={beforeDate}
					onTimeframeChange={(after, before, tf) => {
						setAfterDate(after);
						setBeforeDate(before);
						setTimeframe(tf);
						props.onTimeframeChange?.(after, before, tf);
					}}
				/>
				<div className="text-sm text-muted-foreground font-mono bg-muted p-3 rounded-md">
					<div>
						<strong>timeframe:</strong> {timeframe ?? "undefined"}
					</div>
					<div>
						<strong>after:</strong> {afterDate ?? "undefined"}
					</div>
					<div>
						<strong>before:</strong> {beforeDate ?? "undefined"}
					</div>
				</div>
			</div>
		);
	},
	args: {
		leaderboardSchedule: {
			day: 1,
			hour: 9,
			minute: 0,
			formatted: "Mondays at 09:00",
		},
	},
};
