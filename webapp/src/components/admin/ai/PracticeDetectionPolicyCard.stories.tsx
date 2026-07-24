import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import type { AgentBinding } from "@/api/types.gen";
import { PracticeDetectionPolicyCard } from "./PracticeDetectionPolicyCard";
import { mockAiSettings, mockAvailableModels } from "./storyMockData";

const readyBinding: AgentBinding = {
	purpose: "PRACTICE_DETECTION",
	enabled: true,
	ready: true,
	instanceModelId: 1,
};

/**
 * Policy editor for practice-detection reviews: which model detection runs on (read-only —
 * the binding is owned by the AI setup page), automatic/manual triggers, and review policy
 * (drafts, cooldown, coverage). Saves field-by-field.
 */
const meta = {
	component: PracticeDetectionPolicyCard,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
		settings: mockAiSettings,
		detectionBinding: readyBinding,
		availableModels: mockAvailableModels,
		workspaceSlug: "acme",
		autoTriggerEnabled: true,
		manualTriggerEnabled: true,
		isLoading: false,
		isSaving: false,
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

/** Detection is bound to a runnable model; triggers and policy populated. */
export const RuntimeBound: Story = {};

/** No model bound to practice detection — nothing can run until one is set on the AI setup page. */
export const Unbound: Story = {
	args: { detectionBinding: undefined },
};

/** The bound model was disabled or revoked elsewhere — detection is paused (destructive warning). */
export const BoundModelUnavailable: Story = {
	args: { detectionBinding: { ...readyBinding, ready: false } },
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
