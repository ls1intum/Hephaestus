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
 * Policy editor for practice-detection reviews: which model detection runs on (read-only —
 * the binding is owned by the AI models page), automatic/manual triggers, and review policy
 * (drafts, cooldown, coverage). Saves field-by-field.
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

/** Detection is bound to a runnable model; triggers and policy populated. */
export const RuntimeBound: Story = {};

/** Practice detection has no model — nothing runs until one is chosen on the AI models page. */
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
		settings: { ...mockPracticeReviewSettings, runForAllUsers: false },
	},
};

/** All policy fields inherit the fleet default — every control shows "Inherited from default". */
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

/**
 * The policy could not be loaded because the account lacks the permission — the alert repeats the
 * server's explanation and withholds a Retry that would be refused identically.
 */
export const LoadForbidden: Story = {
	args: {
		isError: true,
		settings: undefined,
		error: { status: 403, detail: "You are not an admin of this workspace." },
		onRetry: fn(),
	},
};

/**
 * The policy editor at the WCAG 2.2 SC 1.4.10 reflow width (320 CSS px).
 *
 * Nothing here is tabular, so it must reflow to one column with no horizontal scrolling at all —
 * each `Field` keeps its switch beside a description that wraps, and the numeric input stays inside
 * the card.
 *
 * The switches are drawn at 32 x 18 px, the size shadcn (and the platform convention behind it) uses.
 * The vendored `ui/switch.tsx` enlarges the hit area past the SC 2.5.8 floor on its own, with
 * `after:-inset-x-3 after:-inset-y-2`, so the criterion is met without leaning on the *Spacing*
 * exception. The spacing is asserted anyway, because it is the second line of defence and the thing
 * a denser layout would break first — and because `getBoundingClientRect` measures the drawn switch,
 * not the pseudo-element that extends it.
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
