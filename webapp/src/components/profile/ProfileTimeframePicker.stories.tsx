import type { Meta, StoryObj } from "@storybook/react-vite";
import { useState } from "react";
import { fn } from "storybook/test";
import { DEFAULT_SCHEDULE, formatDateRangeForApi, getDateRangeForPreset } from "@/lib/timeframe";
import { ProfileTimeframePicker } from "./ProfileTimeframePicker";

// Calculate default dates using the shared timeframe utilities
const defaultRange = getDateRangeForPreset("this-week", DEFAULT_SCHEDULE);
const { after: defaultAfter, before: defaultBefore } = formatDateRangeForApi(defaultRange);

/**
 * ProfileTimeframePicker is a compact, single-row timeframe selector designed for the profile page.
 * It provides preset options (this week, last week, this month, last month, all time)
 * and custom date range selection with a calendar popover.
 *
 * Key features:
 * - Icons for each preset type for quick visual recognition
 * - Detailed labels showing actual date ranges (e.g., "This week, since Tue Dec 3")
 * - Supports leaderboard schedule awareness for correct week boundaries
 * - Minimal footprint while providing comprehensive date selection
 */
const meta = {
	component: ProfileTimeframePicker,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	argTypes: {
		afterDate: {
			description: "Start date in ISO format",
			control: "text",
		},
		beforeDate: {
			description: "End date in ISO format (undefined for open-ended ranges)",
			control: "text",
		},
		onTimeframeChange: {
			description:
				"Callback fired when timeframe selection changes. Receives (afterDate, beforeDate?)",
			action: "timeframe changed",
		},
		enableAllActivity: {
			description: 'Show "All time" option in the dropdown',
			control: "boolean",
		},
		schedule: {
			description: "Leaderboard schedule configuration. Affects week start calculation.",
			control: "object",
		},
	},
	args: {
		afterDate: defaultAfter,
		beforeDate: defaultBefore,
		onTimeframeChange: fn(),
		enableAllActivity: true,
		schedule: DEFAULT_SCHEDULE,
	},
} satisfies Meta<typeof ProfileTimeframePicker>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default state with "This week" preselected.
 * Shows the detailed label with date range context.
 */
export const Default: Story = {};

/**
 * All time - showing the entire activity history.
 * Useful for viewing a user's complete contribution history.
 */
export const AllTime: Story = {
	args: {
		afterDate: "1970-01-01T00:00:00.000Z",
		beforeDate: undefined,
	},
};

/**
 * Last week selection - shows a bounded date range.
 * The label displays the exact date range of the previous week.
 */
export const LastWeek: Story = {
	args: (() => {
		const range = getDateRangeForPreset("last-week", DEFAULT_SCHEDULE);
		const { after, before } = formatDateRangeForApi(range);
		return { afterDate: after, beforeDate: before };
	})(),
};

/**
 * This month selection - open-ended range from month start.
 */
export const ThisMonth: Story = {
	args: (() => {
		const range = getDateRangeForPreset("this-month", DEFAULT_SCHEDULE);
		const { after, before } = formatDateRangeForApi(range);
		return { afterDate: after, beforeDate: before };
	})(),
};

/**
 * Last month selection - bounded range for the previous month.
 */
export const LastMonth: Story = {
	args: (() => {
		const range = getDateRangeForPreset("last-month", DEFAULT_SCHEDULE);
		const { after, before } = formatDateRangeForApi(range);
		return { afterDate: after, beforeDate: before };
	})(),
};

/**
 * Custom timeframe with date range picker visible.
 * Shows how the calendar popover appears for custom date selection.
 */
export const CustomRange: Story = {
	args: {
		afterDate: "2024-11-01T00:00:00.000Z",
		beforeDate: "2024-11-15T00:00:00.000Z",
	},
};

/**
 * Interactive stateful story to test label updates live.
 * Demonstrates how the component responds to user interactions.
 */
export const Interactive: Story = {
	render: (props) => {
		const [afterDate, setAfterDate] = useState<string | undefined>(props.afterDate);
		const [beforeDate, setBeforeDate] = useState<string | undefined>(props.beforeDate);

		return (
			<div className="flex flex-col gap-4 items-start">
				<ProfileTimeframePicker
					{...props}
					afterDate={afterDate}
					beforeDate={beforeDate}
					onTimeframeChange={(after, before) => {
						setAfterDate(after);
						setBeforeDate(before);
						props.onTimeframeChange?.(after, before);
					}}
				/>
				<div className="text-sm text-muted-foreground font-mono bg-muted p-3 rounded-md">
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
};

/**
 * With Tuesday schedule at 9 AM - shows how the week start adapts to different schedules.
 * Default schedule uses Monday as the week start.
 */
export const TuesdaySchedule: Story = {
	args: {
		schedule: { day: 2, hour: 9, minute: 0 },
	},
};

/**
 * With Wednesday schedule - demonstrates mid-week leaderboard reset.
 */
export const WednesdaySchedule: Story = {
	args: {
		schedule: { day: 3, hour: 10, minute: 0 },
	},
};

/**
 * Friday schedule at 4:30 PM - for teams with end-of-week reviews.
 */
export const FridaySchedule: Story = {
	args: {
		schedule: { day: 5, hour: 16, minute: 30 },
	},
};

/**
 * Without "All time" option - for contexts where full history isn't appropriate.
 * Useful when you want to limit users to recent timeframes only.
 */
export const WithoutAllTime: Story = {
	args: {
		enableAllActivity: false,
	},
};

/**
 * Comparison of all preset states in a grid layout.
 * Useful for reviewing how labels appear for each option.
 */
export const AllPresets: Story = {
	render: () => {
		const presets = [
			{
				label: "This Week",
				...formatDateRangeForApi(getDateRangeForPreset("this-week")),
			},
			{
				label: "Last Week",
				...formatDateRangeForApi(getDateRangeForPreset("last-week")),
			},
			{
				label: "This Month",
				...formatDateRangeForApi(getDateRangeForPreset("this-month")),
			},
			{
				label: "Last Month",
				...formatDateRangeForApi(getDateRangeForPreset("last-month")),
			},
			{
				label: "All Time",
				after: "1970-01-01T00:00:00.000Z",
				before: undefined,
			},
		];

		return (
			<div className="flex flex-col gap-4">
				{presets.map(({ label, after, before }) => (
					<div key={label} className="flex items-center gap-4">
						<span className="w-24 text-sm text-muted-foreground">{label}:</span>
						<ProfileTimeframePicker
							afterDate={after}
							beforeDate={before}
							enableAllActivity={true}
							schedule={DEFAULT_SCHEDULE}
						/>
					</div>
				))}
			</div>
		);
	},
};
