import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import { PracticeDetectionPolicyCard } from "./PracticeDetectionPolicyCard";
import { mockAiSettings, mockConfigs } from "./storyMockData";

/**
 * Policy editor for practice-detection reviews: runtime binding, automatic/manual
 * triggers, and review policy (drafts, cooldown, coverage). Saves field-by-field.
 */
const meta = {
	component: PracticeDetectionPolicyCard,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
		settings: mockAiSettings,
		configs: mockConfigs,
		autoTriggerEnabled: true,
		manualTriggerEnabled: true,
		isLoading: false,
		isSaving: false,
		onBindConfig: fn(),
		onUpdateReviewSettings: fn(),
		onUpdateFeatures: fn(),
		onResetReviewField: fn(),
	},
	decorators: [
		(Story) => (
			<div className="max-w-2xl">
				<Story />
			</div>
		),
	],
} satisfies Meta<typeof PracticeDetectionPolicyCard>;

export default meta;
type Story = StoryObj<typeof meta>;

/** A runtime is bound; triggers and policy populated. */
export const RuntimeBound: Story = {};

/** No runtime bound — fan-out alert is shown. */
export const FanOut: Story = {
	args: {
		settings: { ...mockAiSettings, practiceConfigId: undefined },
	},
};

/** Coverage scoped to the opt-in role. */
export const RoleScopedCoverage: Story = {
	args: {
		settings: { ...mockAiSettings, runForAllUsers: false },
	},
};

/** All policy fields inherit the fleet default — every control shows "Inherited from default". */
export const AllInherited: Story = {
	args: {
		settings: {
			...mockAiSettings,
			skipDraftsOverride: undefined,
			deliverToMergedOverride: undefined,
			cooldownMinutesOverride: undefined,
			runForAllUsersOverride: undefined,
		},
	},
};

/** Both triggers disabled — reviews never start automatically or on demand. */
export const TriggersOff: Story = {
	args: { autoTriggerEnabled: false, manualTriggerEnabled: false },
};

/** A save is in flight — controls disabled. */
export const Saving: Story = {
	args: { isSaving: true },
};

export const Loading: Story = {
	args: { isLoading: true, settings: undefined },
};
