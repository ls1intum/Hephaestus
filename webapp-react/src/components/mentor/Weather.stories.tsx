import type { Meta, StoryObj } from "@storybook/react";
import { Weather } from "./Weather";

/**
 * Weather component for displaying current conditions and hourly forecast.
 * Features adaptive day/night styling, responsive design, and temperature visualization.
 * Perfect for contextual information in mentorship interfaces.
 */
const meta = {
	component: Weather,
	tags: ["autodocs"],
	argTypes: {
		weatherAtLocation: {
			description:
				"Weather data object containing current conditions and forecast",
			control: "object",
		},
	},
	args: {
		// Default sample data from the component
		weatherAtLocation: undefined, // Uses component's built-in SAMPLE
	},
} satisfies Meta<typeof Weather>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default weather display with sample San Francisco data during daytime.
 */
export const Default: Story = {};

/**
 * Nighttime weather display with cooler temperatures and dark styling.
 */
export const NightTime: Story = {
	args: {
		weatherAtLocation: {
			latitude: 40.7128,
			longitude: -74.006,
			generationtime_ms: 0.032,
			utc_offset_seconds: -18000,
			timezone: "America/New_York",
			timezone_abbreviation: "EST",
			elevation: 10,
			current_units: {
				time: "iso8601",
				interval: "seconds",
				temperature_2m: "°C",
			},
			current: {
				time: "2024-10-07T22:30",
				interval: 900,
				temperature_2m: 12.1,
			},
			hourly_units: { time: "iso8601", temperature_2m: "°C" },
			hourly: {
				time: [
					"2024-10-07T22:00",
					"2024-10-07T23:00",
					"2024-10-08T00:00",
					"2024-10-08T01:00",
					"2024-10-08T02:00",
					"2024-10-08T03:00",
					"2024-10-08T04:00",
					"2024-10-08T05:00",
					"2024-10-08T06:00",
					"2024-10-08T07:00",
				],
				temperature_2m: [
					12.1, 11.8, 11.2, 10.9, 10.5, 10.1, 9.8, 9.5, 10.2, 11.8,
				],
			},
			daily_units: {
				time: "iso8601",
				sunrise: "iso8601",
				sunset: "iso8601",
			},
			daily: {
				time: ["2024-10-07", "2024-10-08"],
				sunrise: ["2024-10-07T07:15", "2024-10-08T07:16"],
				sunset: ["2024-10-07T19:00", "2024-10-08T18:58"],
			},
		},
	},
};

/**
 * Hot summer day with high temperatures to test visual scaling.
 */
export const HotDay: Story = {
	args: {
		weatherAtLocation: {
			latitude: 33.4484,
			longitude: -112.074,
			generationtime_ms: 0.028,
			utc_offset_seconds: -25200,
			timezone: "America/Phoenix",
			timezone_abbreviation: "MST",
			elevation: 331,
			current_units: {
				time: "iso8601",
				interval: "seconds",
				temperature_2m: "°C",
			},
			current: {
				time: "2024-07-15T14:30",
				interval: 900,
				temperature_2m: 43.2,
			},
			hourly_units: { time: "iso8601", temperature_2m: "°C" },
			hourly: {
				time: [
					"2024-07-15T14:00",
					"2024-07-15T15:00",
					"2024-07-15T16:00",
					"2024-07-15T17:00",
					"2024-07-15T18:00",
					"2024-07-15T19:00",
					"2024-07-15T20:00",
					"2024-07-15T21:00",
					"2024-07-15T22:00",
					"2024-07-15T23:00",
				],
				temperature_2m: [
					43.2, 44.1, 44.8, 43.9, 42.1, 39.8, 37.2, 35.1, 33.8, 32.4,
				],
			},
			daily_units: {
				time: "iso8601",
				sunrise: "iso8601",
				sunset: "iso8601",
			},
			daily: {
				time: ["2024-07-15", "2024-07-16"],
				sunrise: ["2024-07-15T05:25", "2024-07-16T05:26"],
				sunset: ["2024-07-15T19:45", "2024-07-16T19:44"],
			},
		},
	},
};

/**
 * Cold winter conditions with sub-zero temperatures.
 */
export const ColdWinter: Story = {
	args: {
		weatherAtLocation: {
			latitude: 64.1466,
			longitude: -21.9426,
			generationtime_ms: 0.035,
			utc_offset_seconds: 0,
			timezone: "GMT",
			timezone_abbreviation: "GMT",
			elevation: 61,
			current_units: {
				time: "iso8601",
				interval: "seconds",
				temperature_2m: "°C",
			},
			current: {
				time: "2024-01-15T11:30",
				interval: 900,
				temperature_2m: -8.3,
			},
			hourly_units: { time: "iso8601", temperature_2m: "°C" },
			hourly: {
				time: [
					"2024-01-15T11:00",
					"2024-01-15T12:00",
					"2024-01-15T13:00",
					"2024-01-15T14:00",
					"2024-01-15T15:00",
					"2024-01-15T16:00",
					"2024-01-15T17:00",
					"2024-01-15T18:00",
					"2024-01-15T19:00",
					"2024-01-15T20:00",
				],
				temperature_2m: [
					-8.3, -7.9, -7.2, -6.8, -7.1, -8.4, -9.2, -10.1, -11.3, -12.1,
				],
			},
			daily_units: {
				time: "iso8601",
				sunrise: "iso8601",
				sunset: "iso8601",
			},
			daily: {
				time: ["2024-01-15", "2024-01-16"],
				sunrise: ["2024-01-15T11:20", "2024-01-16T11:18"],
				sunset: ["2024-01-15T15:45", "2024-01-16T15:47"],
			},
		},
	},
};

/**
 * Early morning sunrise transition showing the boundary between night and day.
 */
export const SunriseTransition: Story = {
	args: {
		weatherAtLocation: {
			latitude: 35.6762,
			longitude: 139.6503,
			generationtime_ms: 0.024,
			utc_offset_seconds: 32400,
			timezone: "Asia/Tokyo",
			timezone_abbreviation: "JST",
			elevation: 40,
			current_units: {
				time: "iso8601",
				interval: "seconds",
				temperature_2m: "°C",
			},
			current: {
				time: "2024-10-07T07:15",
				interval: 900,
				temperature_2m: 18.5,
			},
			hourly_units: { time: "iso8601", temperature_2m: "°C" },
			hourly: {
				time: [
					"2024-10-07T07:00",
					"2024-10-07T08:00",
					"2024-10-07T09:00",
					"2024-10-07T10:00",
					"2024-10-07T11:00",
					"2024-10-07T12:00",
					"2024-10-07T13:00",
					"2024-10-07T14:00",
					"2024-10-07T15:00",
					"2024-10-07T16:00",
				],
				temperature_2m: [
					18.5, 20.1, 22.3, 24.8, 26.9, 28.4, 29.1, 28.8, 27.6, 26.2,
				],
			},
			daily_units: {
				time: "iso8601",
				sunrise: "iso8601",
				sunset: "iso8601",
			},
			daily: {
				time: ["2024-10-07", "2024-10-08"],
				sunrise: ["2024-10-07T07:15", "2024-10-08T07:16"],
				sunset: ["2024-10-07T19:00", "2024-10-08T18:58"],
			},
		},
	},
};

/**
 * Compact mobile view demonstration with reduced hourly forecast items.
 */
export const MobileView: Story = {
	parameters: {
		viewport: {
			defaultViewport: "mobile1",
		},
	},
};

/**
 * Extreme temperature scenario with Fahrenheit units for US users.
 */
export const FahrenheitUnits: Story = {
	args: {
		weatherAtLocation: {
			latitude: 25.7617,
			longitude: -80.1918,
			generationtime_ms: 0.031,
			utc_offset_seconds: -18000,
			timezone: "America/New_York",
			timezone_abbreviation: "EST",
			elevation: 2,
			current_units: {
				time: "iso8601",
				interval: "seconds",
				temperature_2m: "°F",
			},
			current: {
				time: "2024-08-15T15:30",
				interval: 900,
				temperature_2m: 95.2,
			},
			hourly_units: { time: "iso8601", temperature_2m: "°F" },
			hourly: {
				time: [
					"2024-08-15T15:00",
					"2024-08-15T16:00",
					"2024-08-15T17:00",
					"2024-08-15T18:00",
					"2024-08-15T19:00",
					"2024-08-15T20:00",
					"2024-08-15T21:00",
					"2024-08-15T22:00",
					"2024-08-15T23:00",
					"2024-08-16T00:00",
				],
				temperature_2m: [
					95.2, 96.1, 94.8, 92.3, 89.7, 87.2, 85.1, 83.4, 82.1, 80.8,
				],
			},
			daily_units: {
				time: "iso8601",
				sunrise: "iso8601",
				sunset: "iso8601",
			},
			daily: {
				time: ["2024-08-15", "2024-08-16"],
				sunrise: ["2024-08-15T06:35", "2024-08-16T06:36"],
				sunset: ["2024-08-15T20:05", "2024-08-16T20:04"],
			},
		},
	},
};
