import type { Meta, StoryObj } from "@storybook/react-vite";
import {
	ArtifactStateIcon,
	StatusChip,
	StatusDot,
	TrendGlyph,
	TrendNote,
} from "@/components/practices/status-language";

/**
 * The shared status and trend vocabulary of the practice surfaces. Status is criterion
 * referenced per practice and never a rank; trend copy is blame free by construction.
 * Every surface composes these primitives, so an area reads the same everywhere.
 */
const meta = {
	component: StatusChip,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	args: { status: "STRENGTH" },
} satisfies Meta<typeof StatusChip>;

export default meta;
type Story = StoryObj<typeof meta>;

/** All four status chips side by side. */
export const Chips: Story = {
	render: () => (
		<div className="flex items-center gap-2">
			<StatusChip status="STRENGTH" />
			<StatusChip status="MIXED" />
			<StatusChip status="DEVELOPING" />
			<StatusChip status="NO_ACTIVITY" />
		</div>
	),
};

/** The dot form for dense surfaces. NO_ACTIVITY renders hollow. */
export const Dots: Story = {
	render: () => (
		<div className="flex items-center gap-3">
			<StatusDot status="STRENGTH" />
			<StatusDot status="MIXED" />
			<StatusDot status="DEVELOPING" />
			<StatusDot status="NO_ACTIVITY" />
		</div>
	),
};

/** Trend glyphs and their plain sentences. STEADY deliberately renders nothing. */
export const Trends: Story = {
	render: () => (
		<div className="flex flex-col gap-2">
			<TrendNote trend="IMPROVING" />
			<TrendNote trend="WORSENING" />
			<TrendNote trend="NEW" />
			<span className="text-xs text-muted-foreground">
				STEADY renders nothing: <TrendGlyph trend="STEADY" />
			</span>
		</div>
	),
};

/** Provider-tinted artifact state icons for every artifact kind. */
export const ArtifactIcons: Story = {
	render: () => (
		<div className="flex items-center gap-3">
			<ArtifactStateIcon kind="PULL_REQUEST" state="OPEN" />
			<ArtifactStateIcon kind="PULL_REQUEST" state="MERGED" />
			<ArtifactStateIcon kind="PULL_REQUEST" state="CLOSED" />
			<ArtifactStateIcon kind="ISSUE" state="OPEN" />
			<ArtifactStateIcon kind="ISSUE" state="CLOSED" />
			<ArtifactStateIcon kind="CONVERSATION_THREAD" />
		</div>
	),
};
