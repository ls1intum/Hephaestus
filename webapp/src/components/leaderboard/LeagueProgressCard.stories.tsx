import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import { LeagueProgressCard } from "./LeagueProgressCard";

const meta = {
	component: LeagueProgressCard,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	decorators: [
		(Story) => (
			<div className="min-w-[340px]">
				<Story />
			</div>
		),
	],
	argTypes: {
		onInfoClick: { control: false },
		leaguePoints: {
			control: { type: "range", min: 0, max: 2500, step: 50 },
		},
	},
} satisfies Meta<typeof LeagueProgressCard>;

export default meta;

type Story = StoryObj<typeof meta>;

export const BronzeStart: Story = {
	args: {
		leaguePoints: 100,
		onInfoClick: fn(),
	},
};

export const BronzeMidway: Story = {
	args: {
		leaguePoints: 625,
		onInfoClick: fn(),
	},
};

export const BronzeNearPromotion: Story = {
	args: {
		leaguePoints: 1150,
		onInfoClick: fn(),
	},
};

export const SilverNew: Story = {
	args: {
		leaguePoints: 1260,
		onInfoClick: fn(),
	},
};

export const SilverMidway: Story = {
	args: {
		leaguePoints: 1375,
		onInfoClick: fn(),
	},
};

export const GoldMidway: Story = {
	args: {
		leaguePoints: 1625,
		onInfoClick: fn(),
	},
};

export const DiamondMidway: Story = {
	args: {
		leaguePoints: 1875,
		onInfoClick: fn(),
	},
};

export const MasterLeague: Story = {
	args: {
		leaguePoints: 2200,
		onInfoClick: fn(),
	},
};

export const WithoutInfoButton: Story = {
	args: { leaguePoints: 1625 },
};
