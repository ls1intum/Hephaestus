import type { Meta, StoryObj } from "@storybook/react";
import type { TrendSupport } from "@/api/types.gen";
import { PracticeTrendChip } from "./PracticeTrendChip";

const wellSupported: TrendSupport = {
	level: "WELL_SUPPORTED",
	currentOpportunities: 4,
	previousOpportunities: 4,
	opportunitiesUntilComparable: 0,
	calendarSpanDays: 9,
	bundleSize: 4,
	ropeHalfWidth: 0.15,
	credibilityThreshold: 0.9,
};

const tentative: TrendSupport = { ...wellSupported, level: "TENTATIVE", previousOpportunities: 3 };
const none: TrendSupport = {
	...wellSupported,
	level: "NONE",
	currentOpportunities: 2,
	previousOpportunities: 0,
	opportunitiesUntilComparable: 3,
};

const meta = {
	component: PracticeTrendChip,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
} satisfies Meta<typeof PracticeTrendChip>;

export default meta;
type Story = StoryObj<typeof meta>;

export const ImprovingWellSupported: Story = {
	args: { direction: "IMPROVING", support: wellSupported },
};
export const ImprovingTentative: Story = { args: { direction: "IMPROVING", support: tentative } };
export const DecliningWellSupported: Story = {
	args: { direction: "DECLINING", support: wellSupported },
};
export const DecliningTentative: Story = { args: { direction: "DECLINING", support: tentative } };
export const StableWellSupported: Story = { args: { direction: "STABLE", support: wellSupported } };
export const StableTentative: Story = { args: { direction: "STABLE", support: tentative } };
export const UncertainWellSupported: Story = {
	args: { direction: "UNCERTAIN", support: wellSupported },
};
export const UncertainTentative: Story = { args: { direction: "UNCERTAIN", support: tentative } };
export const InsufficientEvidence: Story = {
	args: { direction: "INSUFFICIENT_EVIDENCE", support: none },
};
