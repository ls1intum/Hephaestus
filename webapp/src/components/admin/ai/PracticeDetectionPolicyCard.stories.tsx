import type { Meta, StoryObj } from "@storybook/react";
import { fn, within } from "storybook/test";
import type { AgentBinding } from "@/api/types.gen";
import { expectPageReflows, expectTargetSpacing, expectWithinViewport } from "@/test/reflow";
import { PracticeDetectionPolicyCard } from "./PracticeDetectionPolicyCard";
import { mockAvailableModels, mockPracticeReviewSettings } from "./story-mock-data";

const readyBinding: AgentBinding = {
	purpose: "PRACTICE_DETECTION",
	enabled: true,
	ready: true,
	instanceModelId: 1,
};

/**
 * Policy editor for practice-detection reviews: the bound model (read-only — the AI models page
 * owns it), the triggers, and the review policy. Saves field by field.
 */
const meta = {
	component: PracticeDetectionPolicyCard,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
		settings: mockPracticeReviewSettings,
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

export const RuntimeBound: Story = {};

export const Unbound: Story = {
	args: { detectionBinding: undefined },
};

export const BoundModelUnavailable: Story = {
	args: { detectionBinding: { ...readyBinding, ready: false } },
};

export const RoleScopedCoverage: Story = {
	args: {
		settings: { ...mockPracticeReviewSettings, runForAllUsers: false },
	},
};

export const AllInherited: Story = {
	args: {
		settings: {
			...mockPracticeReviewSettings,
			skipDraftsOverride: undefined,
			deliverToMergedOverride: undefined,
			cooldownMinutesOverride: undefined,
			runForAllUsersOverride: undefined,
		},
	},
};

export const TriggersOff: Story = {
	args: { autoTriggerEnabled: false, manualTriggerEnabled: false },
};

export const Saving: Story = {
	args: { isSaving: true },
};

export const Loading: Story = {
	args: { isLoading: true, settings: undefined },
};

export const LoadForbidden: Story = {
	args: {
		isError: true,
		settings: undefined,
		error: { status: 403, detail: "You are not an admin of this workspace." },
		onRetry: fn(),
	},
};

/**
 * WCAG 2.2 SC 1.4.10 at 320 px: nothing here is tabular, so nothing may scroll sideways.
 *
 * The switches meet SC 2.5.8 through a pseudo-element `getBoundingClientRect` cannot see, so the
 * Spacing exception is what this can actually measure — and what a denser layout breaks first.
 */
export const MobileReflow: Story = {
	parameters: {
		layout: "fullscreen",
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320, 375, 768] },
	},
	play: async ({ canvasElement }) => {
		await expectPageReflows();
		const switches = within(canvasElement).getAllByRole("switch");
		for (const control of switches) {
			await expectWithinViewport(control);
		}
		await expectTargetSpacing(switches);
	},
};
