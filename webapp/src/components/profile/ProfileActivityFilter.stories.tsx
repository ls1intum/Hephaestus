import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "@storybook/test";
import { endOfISOWeek, formatISO, startOfISOWeek } from "date-fns";
import { useState } from "react";
import { ProfileActivityFilter } from "./ProfileActivityFilter";

const now = new Date();
const defaultAfter = formatISO(startOfISOWeek(now));
const defaultBefore = formatISO(endOfISOWeek(now));

/**
 * Popover-based activity filter that reuses the timeframe selector.
 */
const meta = {
	component: ProfileActivityFilter,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	args: {
		initialAfterDate: defaultAfter,
		initialBeforeDate: defaultBefore,
		onTimeframeChange: fn(),
	},
} satisfies Meta<typeof ProfileActivityFilter>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default state with the current week preselected.
 */
export const Default: Story = {};

/**
 * Example showing a custom timeframe selection hook.
 */
export const WithHandler: Story = {
	args: {
		onTimeframeChange: fn(),
	},
};

/**
 * Interactive stateful story to validate label updates live.
 */
export const Stateful: Story = {
	render: (props) => {
		const [afterDate, setAfterDate] = useState<string | undefined>(
			props.initialAfterDate,
		);
		const [beforeDate, setBeforeDate] = useState<string | undefined>(
			props.initialBeforeDate,
		);

		return (
			<div className="flex flex-col gap-4 items-start">
				<ProfileActivityFilter
					{...props}
					initialAfterDate={afterDate}
					initialBeforeDate={beforeDate}
					onTimeframeChange={(after, before) => {
						setAfterDate(after);
						setBeforeDate(before);
						props.onTimeframeChange?.(after, before);
					}}
				/>
				<div className="text-sm text-muted-foreground">
					<strong>Debug:</strong> after={afterDate ?? "∅"}, before=
					{beforeDate ?? "∅"}
				</div>
			</div>
		);
	},
};
